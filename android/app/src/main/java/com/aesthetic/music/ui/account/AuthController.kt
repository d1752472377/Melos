package com.aesthetic.music.ui.account

import android.content.Context
import android.net.Uri
import com.aesthetic.music.MelosApplication
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.ApiClient
import com.aesthetic.music.data.api.AuthData
import com.aesthetic.music.data.api.SessionDto
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.settings.UserStore
import com.aesthetic.music.data.sync.SyncStateStore
import com.aesthetic.music.service.LogReporter
import com.aesthetic.music.ui.profile.ProfileFormat
import com.aesthetic.music.ui.profile.ProfileUtils
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 账号与个人资料控制器：注册 / 登录 / 退出 / 登录设备管理，以及头像、背景、昵称、签名。
 *
 * 原先账号状态在 `AuthController`、个人资料状态在 `ProfileController`，两者都读写
 * [UserStore]，登录后还要互相刷新。合并后只有一个事实来源：登录态与资料展示状态都在这里，
 * `refresh()` 同时更新两者，宿主不再需要“登录后手动同步另一个控制器”。
 *
 * ## 后端只保存数据，鉴权交给账号
 * 旧的"私有云密钥"模式（一个手填的共享密钥）已经不存在了：
 * 服务器只做两件事——保存某个账号的数据、按账号把数据同步回来。
 * 因此这里登录成功后唯一必须做对的事是：**把 token 与用户信息写进 [UserStore]**，
 * 后续所有云接口（`ApiClient` 拦截器）都从那里取 Bearer Token。
 *
 * ## 登录态与数据的关系（重要）
 * - 登录**不会**导入或覆盖本机数据：登录后第一次同步会走"先拉后推"，
 *   云端已有的记录并进本地、本地自上次同步之后的改动推上去（见 `LibraryController.performCloudSync`）。
 * - 退出登录**不会**删除本地数据：只撤销服务端会话、清掉本机 token。
 *   本地收藏/歌单留着（用户可能只是换账号），云端数据当然也留着。
 * - 「退出并清空本机数据」是显式选项：连本地数据与同步基线一起清掉，用于"把这台设备交给别人"。
 */
class AuthController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    private val onToast: (String) -> Unit,
    /** 登录态发生变化后的回调（刷新同步状态、触发一次同步、刷新设置页文案） */
    private val onAuthChanged: () -> Unit = {},
    /** 登录态失效时清空依赖登录的本地状态（由宿主转调 LibraryController） */
    private val onLoggedOut: () -> Unit = {},
) {

    private val _user = MutableStateFlow(UserStore.user())
    val user: StateFlow<UserInfo?> = _user.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(UserStore.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    /** 登录/注册请求进行中（按钮转圈与防重复点击） */
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    /** 表单级错误：直接显示在登录表单下方，比 Snackbar 更容易被看见 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionDto>>(emptyList())
    val sessions: StateFlow<List<SessionDto>> = _sessions.asStateFlow()

    private val _isLoadingSessions = MutableStateFlow(false)
    val isLoadingSessions: StateFlow<Boolean> = _isLoadingSessions.asStateFlow()

    /** 验证码重发倒计时（秒） */
    private val _codeCountdown = MutableStateFlow(0)
    val codeCountdown: StateFlow<Int> = _codeCountdown.asStateFlow()

    /** 正在发送验证码（用于按钮转圈） */
    private val _isSendingCode = MutableStateFlow(false)
    val isSendingCode: StateFlow<Boolean> = _isSendingCode.asStateFlow()

    private var countdownJob: Job? = null

    // ===== 个人资料与个性化状态 =====

    private val _profileAvatar = MutableStateFlow(UserStore.getAvatarPath())
    val profileAvatar = _profileAvatar.asStateFlow()
    private val _profileBackground = MutableStateFlow(UserStore.getBackgroundPath())
    val profileBackground = _profileBackground.asStateFlow()
    private val _isProfileBgBlur = MutableStateFlow(UserStore.isBackgroundBlur())
    val isProfileBgBlur = _isProfileBgBlur.asStateFlow()
    private val _profileNickname = MutableStateFlow(UserStore.getNickname())
    val profileNickname = _profileNickname.asStateFlow()
    private val _profileBio = MutableStateFlow(UserStore.getBio())
    val profileBio = _profileBio.asStateFlow()
    private val _profileUserId = MutableStateFlow(UserStore.getUserId())
    val profileUserId = _profileUserId.asStateFlow()

    init {
        UserStore.user()?.let { restoreCloudImages(it) }
    }

    /**
     * 登录态失效（401）：清登录态并提示。此前 token 过期/被吊销后界面仍显示"已登录"，
     * 收藏/历史/歌单云同步全部静默失败，用户只看到"同步没生效"。
     */
    fun installAuthFailedHook() {
        ApiClient.setOnAuthFailed {
            if (UserStore.isLoggedIn()) {
                UserStore.logout()
                refresh()
                onLoggedOut()
                onToast("登录已过期，请重新登录")
            }
        }
    }

    /** 从 [UserStore] 重新读取登录态（`isLoggedIn` 要求 token 与用户信息同时存在） */
    fun refresh() {
        _user.value = UserStore.user()
        _isLoggedIn.value = UserStore.isLoggedIn()
        refreshProfileState()
    }

    /**
     * 把个人中心展示的状态同步成 [UserStore] 的真实值。
     *
     * 登录、退出、会话失效后都要走这里：登出后还留着上一个账号的头像/昵称会泄露隐私，
     * 登录后不刷新又会出现"登录了但页面没变"。
     */
    private fun refreshProfileState() {
        _profileAvatar.value = UserStore.getAvatarPath()
        _profileBackground.value = UserStore.getBackgroundPath()
        _isProfileBgBlur.value = UserStore.isBackgroundBlur()
        _profileNickname.value = UserStore.getNickname()
        _profileBio.value = UserStore.getBio()
        _profileUserId.value = UserStore.getUserId()
        UserStore.user()?.let { restoreCloudImages(it) }
    }

    fun clearError() {
        _error.value = null
    }

    fun login(username: String, password: String) {
        val u = username.trim()
        if (u.isBlank() || password.isBlank()) {
            _error.value = "请填写用户名和密码"
            return
        }
        scope.launch {
            _isBusy.value = true
            _error.value = null
            repository.login(u, password, deviceName = SyncStateStore.deviceName())
                .onSuccess { onAuthSuccess(it, isRegister = false) }
                .onFailure {
                    LogReporter.w("Auth", "登录失败", it)
                    _error.value = friendly(it, "登录失败")
                }
            _isBusy.value = false
        }
    }

    fun register(
        username: String,
        password: String,
        email: String,
        code: String,
        nickname: String = ""
    ) {
        val u = username.trim()
        val e = email.trim().lowercase()
        val c = code.trim()
        if (e.isBlank()) {
            _error.value = "请填写注册邮箱"
            return
        }
        if (!EMAIL_PATTERN.matches(e)) {
            _error.value = "请输入有效的邮箱地址（如 name@domain.com）"
            return
        }
        if (c.length != 6 || !c.all { it.isDigit() }) {
            _error.value = "请输入 6 位数字验证码"
            return
        }
        if (u.isBlank() || password.isBlank()) {
            _error.value = "请填写用户名和密码"
            return
        }
        if (!USERNAME_PATTERN.matches(u)) {
            _error.value = "用户名需 2-20 位（字母 / 数字 / 下划线 / 中文）"
            return
        }
        if (password.length < 5 || password.length > 64) {
            _error.value = "密码需 5-64 位"
            return
        }
        if (nickname.trim().length > 24) {
            _error.value = "昵称最长 24 字"
            return
        }
        scope.launch {
            _isBusy.value = true
            _error.value = null
            repository.register(
                username = u,
                password = password,
                email = e,
                code = c,
                nickname = nickname.trim(),
                deviceName = SyncStateStore.deviceName(),
            ).onSuccess { onAuthSuccess(it, isRegister = true) }
                .onFailure {
                    LogReporter.w("Auth", "注册失败", it)
                    _error.value = friendly(it, "注册失败")
                }
            _isBusy.value = false
        }
    }

    fun sendCode(email: String, scene: String = "login") {
        val e = email.trim().lowercase()
        if (e.isBlank()) {
            _error.value = "请填写邮箱地址"
            return
        }
        if (!EMAIL_PATTERN.matches(e)) {
            _error.value = "请输入有效的邮箱地址（如 name@domain.com）"
            return
        }
        if (_codeCountdown.value > 0) {
            _error.value = "请在 ${_codeCountdown.value} 秒后再请求验证码"
            return
        }
        scope.launch {
            _isSendingCode.value = true
            _error.value = null
            repository.sendCode(e, scene = scene)
                .onSuccess {
                    onToast("验证码已发送至 $e，请查收")
                    startCountdown(60)
                }
                .onFailure {
                    LogReporter.w("Auth", "发送验证码失败 scene=$scene", it)
                    _error.value = friendly(it, "发送验证码失败")
                }
            _isSendingCode.value = false
        }
    }

    fun emailLogin(email: String, code: String) {
        val e = email.trim().lowercase()
        val c = code.trim()
        if (e.isBlank()) {
            _error.value = "请填写邮箱地址"
            return
        }
        if (!EMAIL_PATTERN.matches(e)) {
            _error.value = "请输入有效的邮箱地址（如 name@domain.com）"
            return
        }
        if (c.length != 6 || !c.all { it.isDigit() }) {
            _error.value = "请输入 6 位数字验证码"
            return
        }
        scope.launch {
            _isBusy.value = true
            _error.value = null
            repository.emailLogin(e, c, deviceName = SyncStateStore.deviceName())
                .onSuccess { onAuthSuccess(it, isRegister = false) }
                .onFailure {
                    LogReporter.w("Auth", "登录失败", it)
                    _error.value = friendly(it, "登录失败")
                }
            _isBusy.value = false
        }
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            _codeCountdown.value = seconds
            while (_codeCountdown.value > 0) {
                delay(1000L)
                _codeCountdown.value -= 1
            }
        }
    }

    private fun onAuthSuccess(data: AuthData, isRegister: Boolean) {
        if (data.token.isBlank()) {
            _error.value = "服务器没有返回登录凭证"
            return
        }
        // 唯一的登录态写入点：以前 MusicRepository.login 的返回值没人保存，
        // 结果"登录"这件事在应用里从未真正发生过（UserStore.save 没有调用方）。
        UserStore.save(data.token, data.user)
        LogReporter.breadcrumb("Auth", "login success uid=${data.user.id}")
        refresh()
        onToast(if (isRegister) "注册成功，已自动登录" else "已登录：${data.user.username}")
        onAuthChanged()
    }

    /**
     * 退出当前设备：撤销服务端会话 + 清掉本机登录态。
     *
     * 即使服务端撤销失败（离线/服务器挂了）也照样清本机 token：
     * 用户按了退出，界面上就必须退出，不能因为网络问题把用户留在登录态里。
     * 服务端那条会话有有效期，最终会自己过期。
     */
    fun logout() {
        val hadSession = UserStore.isLoggedIn()
        LogReporter.breadcrumb("Auth", "logout")
        scope.launch {
            if (hadSession) repository.logout()
            UserStore.logout()
            _sessions.value = emptyList()
            refresh()
            onToast("已退出登录，本地数据保留")
            onAuthChanged()
        }
    }

    /** 退出所有设备（服务端撤销该账号全部会话，本机同样清登录态） */
    fun logoutAll() {
        val userId = UserStore.user()?.id
        scope.launch {
            val result = repository.logoutAll()
            UserStore.logout()
            if (userId != null && userId > 0) SyncStateStore.clearUser(userId)
            _sessions.value = emptyList()
            refresh()
            onToast(
                if (result.isSuccess) "已退出全部设备"
                else "已在本地退出（服务器未响应，其它设备可能仍在线）"
            )
            onAuthChanged()
        }
    }

    /**
     * 「退出并清空本机数据」：把本机音乐库与同步状态一起清掉，用于设备转手/彻底重来。
     *
     * 只清本机、不动云端：这个选项的语义是"别在我这台设备上留痕"，
     * 而不是"把我的账号数据删掉"（那是服务端账号注销，不属于本次范围）。
     */
    fun logoutAndClearLocalData(clearLocalData: () -> Unit) {
        val userId = UserStore.user()?.id
        scope.launch {
            repository.logout()
            UserStore.logout()
            if (userId != null && userId > 0) SyncStateStore.clearUser(userId)
            clearLocalData()
            _sessions.value = emptyList()
            refresh()
            onToast("已退出并清空本机数据")
            onAuthChanged()
        }
    }

    fun loadSessions() {
        if (!UserStore.isLoggedIn()) {
            _sessions.value = emptyList()
            return
        }
        scope.launch {
            _isLoadingSessions.value = true
            repository.getSessions()
                .onSuccess { _sessions.value = it }
                .onFailure {
                    LogReporter.w("Auth", "设备列表获取失败", it)
                    onToast("设备列表获取失败：${it.message ?: "网络错误"}")
                }
            _isLoadingSessions.value = false
        }
    }

    fun revokeSession(sessionId: String) {
        if (sessionId.isBlank()) return
        scope.launch {
            repository.deleteSession(sessionId)
                .onSuccess {
                    _sessions.value = _sessions.value.filterNot { it.id == sessionId }
                    onToast("已退出该设备")
                }
                .onFailure {
                    LogReporter.w("Auth", "撤销设备会话失败 session=$sessionId", it)
                    onToast("操作失败：${it.message ?: "网络错误"}")
                }
        }
    }

    /**
     * 服务端返回的用户信息是唯一事实来源：本地展示项与 [UserStore] 缓存一起更新，
     * 避免出现"页面刷新后昵称/邮箱又变回去了"（UserStore 里还是旧的）。
     */
    private fun applyUser(updated: UserInfo) {
        UserStore.updateUser(updated)
        _user.value = updated
        _profileNickname.value = updated.nickname.ifBlank { updated.username }
        _profileBio.value = UserStore.getBio()
        _profileUserId.value = UserStore.getUserId()
        restoreCloudImages(updated)
    }

    private fun restoreCloudImages(user: UserInfo) {
        val context = MelosApplication.instance
        scope.launch(Dispatchers.IO) {
            // 头像：若本地无有效文件则从云端恢复
            if (user.avatar.isNotBlank()) {
                val curLocal = UserStore.getAvatarPath()
                if (curLocal.isNullOrBlank() || !File(curLocal).exists()) {
                    val saved = ProfileUtils.saveBase64ImageToInternal(context, user.avatar, "avatar")
                    if (saved != null) {
                        UserStore.setAvatarPath(saved)
                        _profileAvatar.value = saved
                    }
                } else {
                    _profileAvatar.value = curLocal
                }
            } else if (_profileAvatar.value == null) {
                _profileAvatar.value = UserStore.getAvatarPath()
            }

            // 背景：若本地无有效文件则从云端恢复
            if (user.background.isNotBlank()) {
                val curLocal = UserStore.getBackgroundPath()
                if (curLocal.isNullOrBlank() || !File(curLocal).exists()) {
                    val saved = ProfileUtils.saveBase64ImageToInternal(context, user.background, "background")
                    if (saved != null) {
                        UserStore.setBackgroundPath(saved)
                        _profileBackground.value = saved
                    }
                } else {
                    _profileBackground.value = curLocal
                }
            } else if (_profileBackground.value == null) {
                _profileBackground.value = UserStore.getBackgroundPath()
            }
        }
    }

    // ===== 个人资料与个性化管理 =====

    /** 绑定 / 解绑邮箱流程的验证码发送。[email] 传空串会被拒绝。 */
    fun sendEmailCode(email: String, scene: String, onSent: () -> Unit = {}) {
        val target = email.trim().lowercase()
        if (target.isBlank() || !ProfileFormat.isValidEmail(target)) {
            onToast("请输入有效的邮箱地址")
            return
        }
        scope.launch {
            repository.sendCode(target, scene = scene).onSuccess {
                onToast("验证码已发送至 $target，请查收")
                onSent()
            }.onFailure { e ->
                LogReporter.w("Auth", "邮箱验证码发送失败 scene=$scene", e)
                onToast("验证码发送失败: ${e.message ?: "网络异常"}")
            }
        }
    }

    /** 安全绑定/更换邮箱（核对新邮箱验证码） */
    fun bindEmail(email: String, code: String, onSuccess: () -> Unit = {}) {
        if (!UserStore.isLoggedIn()) {
            onToast("请先登录后再绑定邮箱")
            return
        }
        val target = email.trim().lowercase()
        val c = code.trim()
        if (target.isBlank() || !ProfileFormat.isValidEmail(target)) {
            onToast("邮箱格式不正确")
            return
        }
        if (c.length != 6 || !c.all { it.isDigit() }) {
            onToast("请输入 6 位数字验证码")
            return
        }
        scope.launch {
            repository.bindEmail(email = target, code = c).onSuccess { updated ->
                applyUser(updated)
                onToast("邮箱绑定成功")
                onSuccess()
            }.onFailure { e ->
                LogReporter.w("Auth", "绑定邮箱失败", e)
                onToast("绑定失败: ${e.message ?: "网络异常"}")
            }
        }
    }

    /** 安全解绑邮箱（核对原邮箱验证码） */
    fun unbindEmail(code: String, onSuccess: () -> Unit = {}) {
        if (!UserStore.isLoggedIn()) {
            onToast("请先登录")
            return
        }
        val c = code.trim()
        if (c.length != 6 || !c.all { it.isDigit() }) {
            onToast("请输入 6 位数字验证码")
            return
        }
        scope.launch {
            repository.unbindEmail(code = c).onSuccess { updated ->
                applyUser(updated)
                onToast("邮箱已成功解绑")
                onSuccess()
            }.onFailure { e ->
                LogReporter.w("Auth", "解绑邮箱失败", e)
                onToast("解绑失败: ${e.message ?: "网络异常"}")
            }
        }
    }

    /** 修改登录密码 */
    fun changePassword(oldPwd: String, newPwd: String, onSuccess: () -> Unit = {}) {
        if (!UserStore.isLoggedIn()) {
            onToast("请先登录")
            return
        }
        if (oldPwd.isBlank() || newPwd.isBlank()) {
            onToast("密码不能为空")
            return
        }
        if (newPwd.length < 5 || newPwd.length > 64) {
            onToast("新密码需 5-64 位")
            return
        }
        scope.launch {
            repository.updatePassword(oldPwd, newPwd).onSuccess {
                onToast("密码修改成功")
                onSuccess()
            }.onFailure { e ->
                LogReporter.w("Auth", "修改密码失败", e)
                onToast("修改密码失败: ${e.message ?: "网络异常"}")
            }
        }
    }

    /** 从系统选择器拿到的头像 URI：拷贝进私有目录并更新头像。返回落盘路径或 null */
    fun saveAvatarFromUri(context: Context, uri: Uri): String? {
        val path = ProfileUtils.saveImageToInternal(context, uri, "avatar") ?: run {
            LogReporter.w("Profile", "头像保存失败")
            return null
        }
        setProfileAvatar(path)
        return path
    }

    /** 从系统选择器拿到的背景 URI：拷贝进私有目录并更新背景。返回落盘路径或 null */
    fun saveBackgroundFromUri(context: Context, uri: Uri): String? {
        val path = ProfileUtils.saveImageToInternal(context, uri, "background") ?: run {
            LogReporter.w("Profile", "背景图保存失败")
            return null
        }
        setProfileBackground(path)
        return path
    }

    fun setProfileAvatar(path: String?) {
        UserStore.setAvatarPath(path)
        _profileAvatar.value = path
        if (UserStore.isLoggedIn()) {
            scope.launch(Dispatchers.IO) {
                if (path == null) {
                    repository.updateProfile(avatar = "").onSuccess { applyUser(it) }
                } else {
                    val base64 = ProfileUtils.compressImageToBase64(path, 256, 75)
                    if (!base64.isNullOrBlank()) {
                        repository.updateProfile(avatar = base64).onSuccess { applyUser(it) }
                    }
                }
            }
        }
    }

    fun setProfileBackground(path: String?) {
        UserStore.setBackgroundPath(path)
        _profileBackground.value = path
        if (UserStore.isLoggedIn()) {
            scope.launch(Dispatchers.IO) {
                if (path == null) {
                    repository.updateProfile(background = "").onSuccess { applyUser(it) }
                } else {
                    val base64 = ProfileUtils.compressImageToBase64(path, 720, 70)
                    if (!base64.isNullOrBlank()) {
                        repository.updateProfile(background = base64).onSuccess { applyUser(it) }
                    }
                }
            }
        }
    }

    fun toggleProfileBgBlur() {
        val next = !isProfileBgBlur.value
        UserStore.setBackgroundBlur(next)
        _isProfileBgBlur.value = next
    }

    fun restoreProfileDefaults(context: Context) {
        UserStore.restoreProfileDefaults(context)
        // 头像不在恢复范围内（UserStore 里也只删背景文件）：这里保持原值，不回写 null
        _profileAvatar.value = UserStore.getAvatarPath()
        _profileBackground.value = null
        _isProfileBgBlur.value = false
        _profileNickname.value = UserStore.getNickname()
        _profileBio.value = ""
        _profileUserId.value = UserStore.getUserId()
        if (UserStore.isLoggedIn()) {
            scope.launch(Dispatchers.IO) {
                repository.updateProfile(background = "").onSuccess { applyUser(it) }
            }
        }
        onToast("已恢复默认（头像已保留）")
    }

    /** 保存可编辑资料（昵称/签名/头像）。用户名、注册日期、邮箱是账号属性，不在这里改 */
    fun saveProfileInfo(
        nickname: String,
        bio: String,
        avatarPath: String? = null,
    ) {
        if (nickname.isNotBlank()) {
            UserStore.setNickname(nickname)
            _profileNickname.value = nickname
        }
        UserStore.setBio(bio)
        _profileBio.value = bio
        if (avatarPath != null) {
            UserStore.setAvatarPath(avatarPath)
            _profileAvatar.value = avatarPath
        }
        if (UserStore.isLoggedIn()) {
            scope.launch(Dispatchers.IO) {
                val avatarBase64 = if (avatarPath != null) {
                    ProfileUtils.compressImageToBase64(avatarPath, 256, 75)
                } else null
                repository.updateProfile(
                    nickname = nickname.ifBlank { null },
                    avatar = avatarBase64
                ).onSuccess { updated ->
                    applyUser(updated)
                    onToast("个人信息已保存")
                }.onFailure { e ->
                    LogReporter.w("Auth", "保存个人资料失败", e)
                    onToast("保存失败: ${e.message ?: "网络异常"}")
                }
            }
        } else {
            onToast("个人信息已保存")
        }
    }

    /**
     * 把异常翻译成用户能看懂的一句话。
     *
     * 仓库里 `MusicRepository.apiCatching` 已经把异常转成了**面向用户**的文案
     * （网络故障统一成"网络连接失败…"、4xx/5xx 取后端 `msg`，见 `ApiErrors.kt`），
     * 因此这里不再重复判断 HTTP 状态码，只做两件它做不到的事：
     *  1. 登录/注册场景下，"登录已过期"这类会话文案会误导用户（他本来就没登录）——
     *     在本页把它归因为账号密码问题；
     *  2. 网络故障文案对"登录不了"这个场景补一句该检查服务器地址。
     */
    private fun friendly(e: Throwable, fallback: String): String {
        val msg = e.message?.takeIf { it.isNotBlank() } ?: return fallback
        return when {
            msg.contains("登录已过期") || msg.contains("登录已失效") ->
                "用户名或密码错误，或账号已被禁用"
            msg.contains("网络连接失败") -> "$msg（如果用的是自建服务器，请确认地址正确且服务已启动）"
            else -> msg
        }
    }

    private companion object {
        /** 与后端 `USERNAME_RE = /^[a-zA-Z0-9_\u4e00-\u9fa5]{2,20}$/` 完全一致（含中文用户名） */
        val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_\\u4e00-\\u9fa5]{2,20}$")
        /** 与后端 `EMAIL_RE = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/` 完全一致 */
        val EMAIL_PATTERN = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
    }
}
