package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.ui.AppTab
import com.aesthetic.music.ui.account.AuthController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.profile.ProfileController
import com.aesthetic.music.ui.profile.ProfileScreen
import com.aesthetic.music.ui.source.SourceController

/** 我的 / 个人中心 Tab */
@Composable
fun ProfileTabDestination(
    profile: ProfileController,
    library: LibraryController,
    sources: SourceController,
    auth: AuthController,
    localStats: LocalStats,
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    nav: ShellNav,
    actions: ShellActions,
    cacheState: CacheState,
) {
    val context = LocalContext.current
    val history by library.history.collectAsState()
    val favorites by library.favorites.collectAsState()
    val myPlaylists by library.myPlaylists.collectAsState()
    val sourcePlugins by sources.plugins.collectAsState()
    val profileNickname by profile.profileNickname.collectAsState()
    val profileAvatar by profile.profileAvatar.collectAsState()
    val profileBackground by profile.profileBackground.collectAsState()
    val isProfileBgBlur by profile.isProfileBgBlur.collectAsState()
    val profileUserId by profile.profileUserId.collectAsState()
    val profileUsername by profile.username.collectAsState()
    val profileCreatedAt by profile.createdAt.collectAsState()

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
        // 「下载音乐」只数用户主动下载的（播放缓存在「本地缓存」里，不算下载）
        cacheCount = cacheState.songs.count { it.manual },
        localStats = localStats,
        sourceSummary = when {
            sourcePlugins.isEmpty() -> "内置直连"
            sourcePlugins.count { it.enable } == 0 -> "${sourcePlugins.size} 个插件（全部停用）"
            else -> "${sourcePlugins.size} 个音源插件"
        },
        onOpenEditProfile = { nav.navigate(ScreenRoute.ProfileEdit.route) },
        onOpenFavorites = { nav.navigate(ScreenRoute.Favorites.route) },
        // 我的歌单已并入「歌单」Tab（其中就有「我的歌单」分组），不再单开页面
        onOpenMyPlaylists = { nav.switchTab(ScreenRoute.Playlist.route, AppTab.PLAYLIST) },
        onOpenDownloads = {
            cacheState.refresh()
            nav.navigate(ScreenRoute.Downloads.route)
        },
        onOpenHistory = {
            nav.navigate(ScreenRoute.History.route)
        },
        onOpenListeningStats = { nav.navigate(ScreenRoute.ListeningStats.route) },
        onOpenCloudManage = { nav.navigate(ScreenRoute.CloudManage.route) },
        onOpenEqualizer = { nav.navigate(ScreenRoute.Equalizer.route) },
        onOpenSettings = { nav.navigate(ScreenRoute.Settings.route) },
        onOpenPluginManage = { nav.navigate(ScreenRoute.PluginManage.route) },
        onUpdateAvatar = { path -> profile.setProfileAvatar(path) },
        onUpdateBackground = { path -> profile.setProfileBackground(path) },
        onToggleBgBlur = { profile.toggleProfileBgBlur() },
        onRestoreDefaults = { profile.restoreProfileDefaults(context) },
        onLogout = { auth.logout() },
        onCheckUpdate = onCheckUpdate,
        isCheckingUpdate = isCheckingUpdate,
        onShowToast = actions.toast,
    )
}
