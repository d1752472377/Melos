package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.profile.ProfileScreen

/** 我的 / 个人中心 Tab */
@Composable
fun ProfileTabDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    cacheState: CacheState,
) {
    val context = LocalContext.current
    val history by viewModel.history.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val localStats by viewModel.localStats.collectAsState()
    val isSyncConfigured by viewModel.isSyncConfigured.collectAsState()
    val sourcePlugins by viewModel.sourcePlugins.collectAsState()
    val profileNickname by viewModel.profileNickname.collectAsState()
    val profileAvatar by viewModel.profileAvatar.collectAsState()
    val profileBackground by viewModel.profileBackground.collectAsState()
    val isProfileBgBlur by viewModel.isProfileBgBlur.collectAsState()
    val profileUserId by viewModel.profileUserId.collectAsState()
    val profileUsername by viewModel.profileUsername.collectAsState()
    val profileCreatedAt by viewModel.profileCreatedAt.collectAsState()
    ProfileScreen(
        nickname = profileNickname,
        userId = profileUserId,
        username = profileUsername,
        createdAt = profileCreatedAt,
        avatarPath = profileAvatar,
        backgroundPath = profileBackground,
        isBackgroundBlur = isProfileBgBlur,
        historyCount = history.size,
        favoritesCount = favorites.size,
        playlistsCount = myPlaylists.size,
        cacheCount = cacheState.count,
        cacheSize = cacheState.size,
        localStats = localStats,
        isSyncConfigured = isSyncConfigured,
        sourceSummary = when {
            sourcePlugins.isEmpty() -> "内置直连"
            sourcePlugins.count { it.enable } == 0 -> "${sourcePlugins.size} 个插件（全部停用）"
            else -> "${sourcePlugins.size} 个音源插件"
        },
        onOpenEditProfile = { nav.navigate(ScreenRoute.ProfileEdit.route) },
        onOpenFavorites = { nav.navigate(ScreenRoute.Favorites.route) },
        onOpenMyPlaylists = { nav.navigate(ScreenRoute.MyPlaylists.route) },
        onOpenDownloads = {
            cacheState.refresh()
            nav.navigate(ScreenRoute.Downloads.route)
        },
        onOpenHistory = {
            viewModel.openHistory()
            nav.navigate(ScreenRoute.History.route)
        },
        onOpenListeningStats = { nav.navigate(ScreenRoute.ListeningStats.route) },
        onOpenCloudManage = { nav.navigate(ScreenRoute.CloudManage.route) },
        onOpenSettings = { nav.navigate(ScreenRoute.Settings.route) },
        onOpenPluginManage = { nav.navigate(ScreenRoute.PluginManage.route) },
        onUpdateAvatar = { path -> viewModel.setProfileAvatar(path) },
        onUpdateBackground = { path -> viewModel.setProfileBackground(path) },
        onToggleBgBlur = { viewModel.toggleProfileBgBlur() },
        onRestoreDefaults = { viewModel.restoreProfileDefaults(context) },
        onLogout = { viewModel.logout() },
        onShowToast = actions.toast,
    )
}
