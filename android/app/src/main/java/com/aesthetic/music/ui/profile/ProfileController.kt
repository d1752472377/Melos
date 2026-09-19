package com.aesthetic.music.ui.profile

import android.util.Log
import com.aesthetic.music.MelosApplication
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.ApiClient
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.settings.UserStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 用户与个人中心控制器：登录态 + 登出 + 头像/背景/昵称等个性化。
 *
 * 从 `MainViewModel` 抽出：状态全部来自 [UserStore] 与 [MusicRepository]。登出会清空私有云
 * 同步配置，通过 [onLoggedOut] 回调通知 `LibraryController`，不反向依赖 ViewModel。
 */
class ProfileController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    /** 向用户展示一次性提示（对应 ViewModel 的全局 Snackbar 通道） */
    private val onToast: (String) -> Unit,
    /** 登出后清空云同步配置（由宿主转调 LibraryController.resetSyncState()） */
    private val onLoggedOut: () -> Unit,
) {

    private companion object {
        // 保持与抽取前一致的日志 TAG，便于现有日志过滤规则继续命中
        private const val TAG = "MainViewModel"
    }

    // 用户体系
    private val _user = MutableStateFlow(UserStore.user())
    val user: StateFlow<UserInfo?> = _user.asStateFlow()

    // 个人中心个性化状态
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

    /**
     * 资料页只读展示项（登录用户名 / 注册时间 / 已绑定邮箱）。
     *
     * 全部来自服务端返回的 [UserInfo]，本地不做二次加工：用户名与注册时间是服务端事实，
     * 邮箱的最终状态也以服务端为准——如果后端版本较旧、忽略了 email 字段，
     * 这里读到的仍然是"未绑定"，界面不会出现"本地显示已绑定、服务端其实没有"的假象。
     */
    private val _username = MutableStateFlow(UserStore.user()?.username.orEmpty())
    val username: StateFlow<String> = _username.asStateFlow()
    private val _email = MutableStateFlow(UserStore.user()?.email)
    val email: StateFlow<String?> = _email.asStateFlow()
    private val _createdAt = MutableStateFlow(UserStore.user()?.createdAt ?: 0L)
    val createdAt: StateFlow<Long> = _createdAt.asStateFlow()

    /**
     * 登录态失效（401）：清登录态并提示。此前 token 过期/被吊销后界面仍显示"已登录"，
     * 收藏/历史/歌单云同步全部静默失败，用户只看到"同步没生效"。
     */
    fun installAuthFailedHook() {
        ApiClient.setOnAuthFailed {
            if (UserStore.isLoggedIn()) {
                UserStore.logout()
                syncFromStore()
                onLoggedOut()
                onToast("登录已过期，请重新登录")
            }
        }
    }

    init {
        UserStore.user()?.let { restoreCloudImages(it) }
    }

    /**
     * 从本地登录态重新读取界面数据。
     *
     * 登录/退出/会话失效后由宿主调用（退出的完整流程在 `AuthController`：撤销服务端会话 +
     * 清本机 token + 按账号清同步状态）。这里只负责把个人中心**显示的账号信息**同步成真实状态——
     * 登出后还留着上一个账号的用户名/邮箱会直接泄露隐私，而登录后不刷新又会出现"登录了但页面没变"。
     */
    fun syncFromStore() {
        val u = UserStore.user()
        _user.value = u
        _username.value = u?.username.orEmpty()
        _email.value = u?.email
        _createdAt.value = u?.createdAt ?: 0L
        _profileNickname.value = UserStore.getNickname()
        _profileUserId.value = UserStore.getUserId()
        u?.let { restoreCloudImages(it) }
    }

    fun updateNickname(nickname: String) {
        if (nickname.isBlank()) return
        scope.launch {
            repository.updateProfile(nickname = nickname).onSuccess { updated ->
                applyUser(updated)
                onToast("昵称已更新")
            }.onFailure { e ->
                onToast("更新失败: ${e.message ?: "网络异常"}")
            }
        }
    }

    /**
     * 绑定 / 解绑邮箱。[email] 传空串表示解绑。
     *
     * 与昵称一样走服务端（`PUT /api/me`），并以**响应体**为准回写本地状态：
     * 后端若还没有 email 字段，接口会静默忽略，此时界面必须仍然显示"未绑定"，
     * 否则用户会以为绑定成功，直到某天需要邮箱找回密码时才发现是空的。
     */
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
                onToast("修改密码失败: ${e.message ?: "网络异常"}")
            }
        }
    }

    /**
     * 服务端返回的用户信息是唯一事实来源：本地展示项与 [UserStore] 缓存一起更新，
     * 避免出现"页面刷新后昵称/邮箱又变回去了"（UserStore 里还是旧的）。
     */
    private fun applyUser(updated: UserInfo) {
        _user.value = updated
        UserStore.updateUser(updated)
        _profileNickname.value = updated.nickname.ifBlank { updated.username }
        _username.value = updated.username
        _email.value = updated.email
        _createdAt.value = updated.createdAt
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

    // ===== 个人中心个性化管理 =====

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

    fun restoreProfileDefaults(context: android.content.Context) {
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
                    onToast("保存失败: ${e.message ?: "网络异常"}")
                }
            }
        } else {
            onToast("个人信息已保存")
        }
    }
}
