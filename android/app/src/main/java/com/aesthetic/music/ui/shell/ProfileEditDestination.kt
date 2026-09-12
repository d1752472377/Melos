package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.profile.ProfileEditScreen

/** 编辑个人资料页 */
@Composable
fun ProfileEditDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
) {
    val profileNickname by viewModel.profileNickname.collectAsState()
    val profileBio by viewModel.profileBio.collectAsState()
    val profileGender by viewModel.profileGender.collectAsState()
    val profileBirthday by viewModel.profileBirthday.collectAsState()
    val profileAvatar by viewModel.profileAvatar.collectAsState()
    val profileUserId by viewModel.profileUserId.collectAsState()
    ProfileEditScreen(
        initialNickname = profileNickname,
        initialBio = profileBio,
        initialGender = profileGender,
        initialBirthday = profileBirthday,
        initialAvatarPath = profileAvatar,
        userId = profileUserId,
        onBack = nav.popBack,
        onSave = { nick, bio, gender, bday, avatar ->
            viewModel.saveProfileInfo(nick, bio, gender, bday, avatar)
        },
        onShowToast = actions.toast,
    )
}
