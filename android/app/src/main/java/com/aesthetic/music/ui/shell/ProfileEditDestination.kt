package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.profile.ProfileEditScreen

/** 个人资料页（注册日期 / 登录用户名 / 邮箱绑定 / 昵称签名） */
@Composable
fun ProfileEditDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
) {
    val profileNickname by viewModel.profileNickname.collectAsState()
    val profileBio by viewModel.profileBio.collectAsState()
    val profileAvatar by viewModel.profileAvatar.collectAsState()
    val profileUserId by viewModel.profileUserId.collectAsState()
    val profileUsername by viewModel.profileUsername.collectAsState()
    val profileEmail by viewModel.profileEmail.collectAsState()
    val profileCreatedAt by viewModel.profileCreatedAt.collectAsState()
    ProfileEditScreen(
        initialNickname = profileNickname,
        initialBio = profileBio,
        initialAvatarPath = profileAvatar,
        username = profileUsername,
        userId = profileUserId,
        email = profileEmail,
        createdAt = profileCreatedAt,
        onBack = nav.popBack,
        onSave = { nick, bio, avatar -> viewModel.saveProfileInfo(nick, bio, avatar) },
        onBindEmail = { viewModel.bindEmail(it) },
        onShowToast = actions.toast,
    )
}
