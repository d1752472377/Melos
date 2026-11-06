package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.ui.account.AuthController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.profile.CloudManageScreen
import com.aesthetic.music.ui.profile.ProfileEditScreen

/** 个人资料页（注册日期 / 登录用户名 / 邮箱绑定 / 昵称签名） */
@Composable
fun ProfileEditDestination(
    auth: AuthController,
    nav: ShellNav,
    actions: ShellActions,
) {
    val context = LocalContext.current
    val authUser by auth.user.collectAsState()
    val profileNickname by auth.profileNickname.collectAsState()
    val profileBio by auth.profileBio.collectAsState()
    val profileAvatar by auth.profileAvatar.collectAsState()
    val profileUserId by auth.profileUserId.collectAsState()
    ProfileEditScreen(
        initialNickname = profileNickname,
        initialBio = profileBio,
        initialAvatarPath = profileAvatar,
        username = authUser?.username.orEmpty(),
        userId = profileUserId,
        email = authUser?.email,
        createdAt = authUser?.createdAt ?: 0L,
        onBack = nav.popBack,
        onSave = { nick, bio, avatar -> auth.saveProfileInfo(nick, bio, avatar) },
        onPickAvatar = { uri -> auth.saveAvatarFromUri(context, uri) },
        onSendEmailCode = { email, scene, onSent ->
            auth.sendEmailCode(email, scene, onSent)
        },
        onBindEmail = { email, code, onSuccess ->
            auth.bindEmail(email, code, onSuccess)
        },
        onUnbindEmail = { code, onSuccess ->
            auth.unbindEmail(code, onSuccess)
        },
        onChangePassword = { oldPwd, newPwd, onSuccess ->
            auth.changePassword(oldPwd, newPwd, onSuccess)
        },
        onShowToast = actions.toast,
    )
}


/** 账号与云端同步页 */
@Composable
fun CloudManageDestination(
    auth: AuthController,
    library: LibraryController,
    nav: ShellNav,
) {
    val lastSyncedAt by library.lastSyncedAt.collectAsState()
    val lastSyncStatus by library.lastSyncStatus.collectAsState()
    val isSyncing by library.isSyncing.collectAsState()
    val user by auth.user.collectAsState()
    val sessions by auth.sessions.collectAsState()
    val isLoadingSessions by auth.isLoadingSessions.collectAsState()

    CloudManageScreen(
        user = user,
        sessions = sessions,
        isLoadingSessions = isLoadingSessions,
        lastSyncedAt = lastSyncedAt,
        lastSyncStatus = lastSyncStatus,
        isSyncing = isSyncing,
        onBack = nav.popBack,
        onLoadSessions = { auth.loadSessions() },
        onRevokeSession = { auth.revokeSession(it) },
        onLogout = { auth.logout() },
        onLogoutAll = { auth.logoutAll() },
        onPerformSync = { library.performCloudSync() },
    )
}
