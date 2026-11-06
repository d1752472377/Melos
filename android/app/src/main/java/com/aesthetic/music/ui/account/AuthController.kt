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

class AuthController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    private val onToast: (String) -> Unit,
    private val onAuthChanged: () -> Unit = {},
    private val onLoggedOut: () -> Unit = {},
) {

    private val _user = MutableStateFlow(UserStore.user())
    val user: StateFlow<UserInfo?> = _user.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(UserStore.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionDto>>(emptyList())
    val sessions: StateFlow<List<SessionDto>> = _sessions.asStateFlow()

    private val _isLoadingSessions = MutableStateFlow(false)
    val isLoadingSessions: StateFlow<Boolean> = _isLoadingSessions.asStateFlow()

    private val _codeCountdown = MutableStateFlow(0)
    val codeCountdown: StateFlow<Int> = _codeCountdown.asStateFlow()

    private val _isSendingCode = MutableStateFlow(false)
    val isSendingCode: StateFlow<Boolean> = _isSendingCode.asStateFlow()

    private var countdownJob: Job? = null

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

    fun refresh() {
        _user.value = UserStore.user()
        _isLoggedIn.value = UserStore.isLoggedIn()
        refreshProfileState()
    }

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
        UserStore.save(data.token, data.user)
        LogReporter.breadcrumb("Auth", "login success uid=${data.user.id}")
        refresh()
        onToast(if (isRegister) "注册成功，已自动登录" else "已登录：${data.user.username}")
        onAuthChanged()
    }

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

    fun saveAvatarFromUri(context: Context, uri: Uri): String? {
        val path = ProfileUtils.saveImageToInternal(context, uri, "avatar") ?: run {
            LogReporter.w("Profile", "头像保存失败")
            return null
        }
        setProfileAvatar(path)
        return path
    }

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
        val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_\\u4e00-\\u9fa5]{2,20}$")
        val EMAIL_PATTERN = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
    }
}
