package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.profile.ProfileController
import com.aesthetic.music.ui.profile.ProfileEditScreen

/** 个人资料页（注册日期 / 登录用户名 / 邮箱绑定 / 昵称签名） */
@Composable
fun ProfileEditDestination(
    profile: ProfileController,
    nav: ShellNav,
    actions: ShellActions,
) {
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
