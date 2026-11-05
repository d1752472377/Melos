package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.ui.account.AuthController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.profile.CloudManageScreen
import com.aesthetic.music.ui.profile.ProfileController
import com.aesthetic.music.ui.profile.ProfileEditScreen

/** 个人资料页（注册日期 / 登录用户名 / 邮箱绑定 / 昵称签名） */
@Composable
fun ProfileEditDestination(
    profile: ProfileController,
    nav: ShellNav,
    actions: ShellActions,
) {
    val context = LocalContext.current
    val profileNickname by profile.profileNickname.collectAsState()
    val profileBio by profile.profileBio.collectAsState()
    val profileAvatar by profile.profileAvatar.collectAsState()
    val profileUserId by profile.profileUserId.collectAsState()
    val profileUsername by profile.username.collectAsState()
    val profileEmail by profile.email.collectAsState()
    val profileCreatedAt by profile.createdAt.collectAsState()
    ProfileEditScreen(
        initialNickname = profileNickname,
        initialBio = profileBio,
        initialAvatarPath = profileAvatar,
        username = profileUsername,
        userId = profileUserId,
        email = profileEmail,
        createdAt = profileCreatedAt,
        onBack = nav.popBack,
        onSave = { nick, bio, avatar -> profile.saveProfileInfo(nick, bio, avatar) },
        onPickAvatar = { uri -> profile.saveAvatarFromUri(context, uri) },
        onSendEmailCode = { email, scene, onSent ->
            profile.sendEmailCode(email, scene, onSent)
        },
        onBindEmail = { email, code, onSuccess ->
            profile.bindEmail(email, code, onSuccess)
        },
        onUnbindEmail = { code, onSuccess ->
            profile.unbindEmail(code, onSuccess)
        },
        onChangePassword = { oldPwd, newPwd, onSuccess ->
            profile.changePassword(oldPwd, newPwd, onSuccess)
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
