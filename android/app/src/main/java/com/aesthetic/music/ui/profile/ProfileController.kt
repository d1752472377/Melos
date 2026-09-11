package com.aesthetic.music.ui.profile

import android.util.Log
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.ApiClient
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.settings.UserStore
import kotlinx.coroutines.CoroutineScope
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
    private val _profileGender = MutableStateFlow(UserStore.getGender())
    val profileGender = _profileGender.asStateFlow()
    private val _profileBirthday = MutableStateFlow(UserStore.getBirthday())
    val profileBirthday = _profileBirthday.asStateFlow()
    private val _profileUserId = MutableStateFlow(UserStore.getUserId())
    val profileUserId = _profileUserId.asStateFlow()

    /**
     * 登录态失效（401）：清登录态并提示。此前 token 过期/被吊销后界面仍显示"已登录"，
     * 收藏/历史/歌单云同步全部静默失败，用户只看到"同步没生效"。
     */
    fun installAuthFailedHook() {
        ApiClient.setOnAuthFailed {
            if (UserStore.isLoggedIn()) {
                UserStore.logout()
                _user.value = null
                onToast("登录已过期，请重新登录")
            }
        }
    }

    fun logout() {
        scope.launch {
            try {
                repository.logout()
            } catch (e: Exception) {
                // 服务端登出失败：本地仍继续登出（用户体验优先），但必须留痕——
                // 否则服务端 token 可能仍然有效，属于需要被发现的鉴权残留。
                Log.w(TAG, "服务端登出失败，已继续本地登出", e)
            }
            UserStore.logout()
            onLoggedOut()
            _user.value = null
            _profileNickname.value = UserStore.getNickname()
            _profileUserId.value = UserStore.getUserId()
            onToast("已退出登录并清空私有服务器配置")
        }
    }

    fun updateNickname(nickname: String) {
        if (nickname.isBlank()) return
        scope.launch {
            repository.updateProfile(nickname = nickname).onSuccess { updated ->
                _user.value = updated
                UserStore.updateUser(updated)
                _profileNickname.value = updated.nickname.ifBlank { updated.username }
                onToast("昵称已更新")
            }.onFailure { e ->
                onToast("更新失败: ${e.message ?: "网络异常"}")
            }
        }
    }

    // ===== 个人中心个性化管理 =====

    fun setProfileAvatar(path: String?) {
        UserStore.setAvatarPath(path)
        _profileAvatar.value = path
    }

    fun setProfileBackground(path: String?) {
        UserStore.setBackgroundPath(path)
        _profileBackground.value = path
    }

    fun toggleProfileBgBlur() {
        val next = !isProfileBgBlur.value
        UserStore.setBackgroundBlur(next)
        _isProfileBgBlur.value = next
    }

    fun restoreProfileDefaults(context: android.content.Context) {
        UserStore.restoreProfileDefaults(context)
        _profileAvatar.value = null
        _profileBackground.value = null
        _isProfileBgBlur.value = false
        _profileNickname.value = UserStore.getNickname()
        _profileBio.value = ""
        _profileGender.value = "保密"
        _profileBirthday.value = ""
        _profileUserId.value = UserStore.getUserId()
        onToast("已恢复默认设置")
    }

    fun saveProfileInfo(
        nickname: String,
        bio: String,
        gender: String,
        birthday: String,
        avatarPath: String? = null,
    ) {
        if (nickname.isNotBlank()) {
            UserStore.setNickname(nickname)
            _profileNickname.value = nickname
        }
        UserStore.setBio(bio)
        _profileBio.value = bio
        UserStore.setGender(gender)
        _profileGender.value = gender
        UserStore.setBirthday(birthday)
        _profileBirthday.value = birthday
        if (avatarPath != null) {
            UserStore.setAvatarPath(avatarPath)
            _profileAvatar.value = avatarPath
        }
        if (UserStore.isLoggedIn() && nickname.isNotBlank()) {
            updateNickname(nickname)
        } else {
            onToast("个人信息已保存")
        }
    }
}
