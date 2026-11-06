package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.AppTab
import com.aesthetic.music.ui.account.AuthController
import com.aesthetic.music.ui.fm.FmController
import com.aesthetic.music.ui.fm.FmScreen
import com.aesthetic.music.ui.home.ContentController
import com.aesthetic.music.ui.home.HomeScreen
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.library.PlaylistImportController
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.playlist.PlaylistScreen
import com.aesthetic.music.ui.profile.ProfileScreen
import com.aesthetic.music.ui.source.SourceController
import com.aesthetic.music.ui.theme.FmBackground
import com.aesthetic.music.ui.theme.Palette

/** 主页 */
@Composable
fun HomeDestination(
    content: ContentController,
    playlistDetail: PlaylistDetailController,
    nav: ShellNav,
    actions: ShellActions,
    isPlaying: Boolean = false,
) {
    val dailyPlaylists by content.dailyPlaylists.collectAsState()
    val newSongs by content.newSongs.collectAsState()
    val hotSongs by content.hotSongs.collectAsState()
    val hotPlaylists by content.hotPlaylists.collectAsState()
    val ranks by content.ranks.collectAsState()
    val contentError by content.contentError.collectAsState()
    val isLoadingContent by content.isLoadingContent.collectAsState()
    val recommendPlaylistSongs by content.recommendPlaylistSongs.collectAsState()
    val isLoadingRecommendSongs by content.isLoadingRecommendSongs.collectAsState()
    val newSongRegions by content.newSongRegions.collectAsState()
    HomeScreen(
        dailyPlaylists = dailyPlaylists,
        newSongs = newSongs,
        hotSongs = hotSongs,
        hotPlaylists = hotPlaylists,
        ranks = ranks,
        recommendSongs = recommendPlaylistSongs,
        isLoadingRecommendSongs = isLoadingRecommendSongs,
        onLoadRecommendSongs = content::loadRecommendPlaylistSongs,
        newSongRegions = newSongRegions,
        contentError = contentError,
        isLoading = isLoadingContent,
        onRetryContent = { content.refreshContent() },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onSearchBarClick = { nav.navigateSingleTop(ScreenRoute.Search.route) },
        // GZ 的搜索栏末尾是「听歌识曲」（HomeScreenKt.java:11352-11378）。Melos 暂无识曲能力，
        // 这里沿用项目既有的「功能开发中」提示写法（AppShell 里对账号导入也是这么处理的）。
        onMicClick = { actions.toast("听歌识曲功能开发中，敬请期待") },
        onOpenPlaylistDetail = { pl ->
            playlistDetail.open(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        // 三大入口第一张卡「猜你喜欢」→ 独立推荐页（不再跳歌单详情）
        onOpenGuess = { nav.navigateSingleTop(ScreenRoute.GuessYouLike.route) },
        onOpenRanksPage = { nav.navigate(ScreenRoute.Ranks.route) },
        onOpenFm = { nav.switchTab(ScreenRoute.Fm.route, AppTab.SHEET) },
        isPlaying = isPlaying,
    )
}


/** 歌单广场 Tab */
@Composable
fun PlaylistTabDestination(
    library: LibraryController,
    playlistImport: PlaylistImportController,
    playlistDetail: PlaylistDetailController,
    nav: ShellNav,
    actions: ShellActions,
    onOpenImportDialog: () -> Unit,
) {
    val myPlaylists by library.myPlaylists.collectAsState()
    val favoritePlaylists by library.favoritePlaylists.collectAsState()
    val importPreview by playlistImport.preview.collectAsState()
    val context = LocalContext.current

    PlaylistScreen(
        myPlaylists = myPlaylists,
        favoritePlaylists = favoritePlaylists,
        onOpenMyPlaylist = { id ->
            library.myPlaylist(id)?.let(playlistDetail::showLocalPlaylist)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        onOpenPlaylistDetail = { pl ->
            playlistDetail.open(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        importPreview = importPreview,
        onResolveImport = playlistImport::resolveForPreview,
        onClearImport = playlistImport::clearPreview,
        onPickCover = library::pickPlaylistCover,
        onDeleteCover = library::deletePlaylistCover,
        onCreatePlaylist = { result ->
            val id = library.createPlaylist(
                name = result.name,
                initialSongs = result.importedSongs,
                cover = result.cover,
                sourcePlatform = result.sourcePlatform,
                sourcePlaylistId = result.sourcePlaylistId,
                sourceName = result.sourceName,
            )
            if (id != null) {
                actions.toast(
                    if (result.importedSongs.isEmpty()) {
                        "已创建歌单《${result.name}》"
                    } else {
                        "已创建《${result.name}》，含 ${result.importedSongs.size} 首原始曲目"
                    }
                )
            }
        },
        onEditPlaylist = { id, result ->
            library.updatePlaylistInfo(id, name = result.name, cover = result.cover)
            actions.toast("歌单信息已更新")
        },
        onDeletePlaylist = library::deletePlaylist,
        onOpenImport = onOpenImportDialog,
        onPlayPlaylist = { songs -> actions.playAll(songs) },
        onToast = actions.toast,
        onSharePlaylist = { name, subtitle ->
            val text = "歌单《$name》 · $subtitle" + "\n" + "—— 来自 Melos"
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, name)
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "分享歌单"))
        },
        onRefreshFavorite = library::refreshFavoritePlaylist,
        onRemoveFavorite = library::toggleFavoritePlaylist,
    )
}


/** 我的 / 个人中心 Tab */
@Composable
fun ProfileTabDestination(
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
    val authUser by auth.user.collectAsState()
    val profileNickname by auth.profileNickname.collectAsState()
    val profileAvatar by auth.profileAvatar.collectAsState()
    val profileBackground by auth.profileBackground.collectAsState()
    val isProfileBgBlur by auth.isProfileBgBlur.collectAsState()
    val profileUserId by auth.profileUserId.collectAsState()

    ProfileScreen(
        nickname = profileNickname,
        userId = profileUserId,
        username = authUser?.username.orEmpty(),
        createdAt = authUser?.createdAt ?: 0L,
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
        onPickAvatar = { uri -> auth.saveAvatarFromUri(context, uri) },
        onPickBackground = { uri -> auth.saveBackgroundFromUri(context, uri) },
        onToggleBgBlur = { auth.toggleProfileBgBlur() },
        onRestoreDefaults = { auth.restoreProfileDefaults(context) },
        onLogout = { auth.logout() },
        onCheckUpdate = onCheckUpdate,
        isCheckingUpdate = isCheckingUpdate,
        onShowToast = actions.toast,
    )
}


/** FM 电台 Tab */
@Composable
fun FmDestination(
    fm: FmController,
    library: LibraryController,
    actions: ShellActions,
    playerManager: PlayerManager,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    val fmQueue by fm.queue.collectAsState()
    val fmIndex by fm.index.collectAsState()
    val fmLoading by fm.loading.collectAsState()
    val fmError by fm.error.collectAsState()
    val fmTreasure by fm.treasure.collectAsState()
    val currentFmChannel by fm.currentChannel.collectAsState()
    val favorites by library.favorites.collectAsState()
    val playerState by playerManager.state.collectAsState()

    // 进入 FM 路由设置与主题匹配的背景，使底部 NavigationDock 呈现深/浅两套毛玻璃质感
    val fmPageBackground = FmBackground
    val fmIsDark = Palette.isDark
    DisposableEffect(fmPageBackground, fmIsDark) {
        onPageBackground(fmPageBackground, fmIsDark)
        onDispose {
            onPageBackground(null, false)
        }
    }

    // 首次切入若队列为空，自动开始调频并启播
    LaunchedEffect(Unit) {
        if (fmQueue.isEmpty() && !fmLoading) {
            fm.start(fm.currentChannel.value) { firstSong, list ->
                actions.playSong(firstSong, list)
            }
        }
    }

    FmScreen(
        fmQueue = fmQueue,
        fmIndex = fmIndex,
        fmLoading = fmLoading,
        fmError = fmError,
        fmTreasure = fmTreasure,
        currentChannel = currentFmChannel,
        channels = fm.channels,
        favorites = favorites,
        onPickCover = library::pickPlaylistCover,
        onDeleteCover = library::deletePlaylistCover,
        currentSong = playerState.currentSong,
        isPlaying = playerState.isPlaying,
        isBuffering = playerState.isLoading,
        lyrics = playerState.lyrics,
        syncedLyrics = playerState.syncedLyrics,
        lyricsLoading = playerState.lyricsLoading,
        onReloadLyrics = playerManager::reloadLyrics,
        progressFlow = playerManager.progress,
        onSelectChannel = { channel ->
            fm.start(channel) { firstSong, list ->
                actions.playSong(firstSong, list)
            }
        },
        onRefreshRecommendation = {
            fm.refresh { firstSong, list ->
                actions.playSong(firstSong, list)
            }
        },
        onSelect = { index ->
            fm.select(index)
            val q = fm.queue.value
            val target = q.getOrNull(index)
            val alreadySame = (target?.id != null && playerState.currentSong?.id == target.id)
            if (target != null && !alreadySame) actions.playSong(target, q)
        },
        onToggleLike = { song -> fm.toggleLike(song) },
        onDislike = { song ->
            fm.dislike(song) { nextSong, list ->
                actions.playSong(nextSong, list)
            }
        },
        onPlayPause = { playerManager.toggle() },
        onPrevious = {
            val q = fm.queue.value
            val prevIdx = (fmIndex - 1).coerceAtLeast(0)
            val prevSong = q.getOrNull(prevIdx)
            if (prevSong != null) {
                fm.select(prevIdx)
                actions.playSong(prevSong, q)
            }
        },
        onNext = {
            val q = fm.queue.value
            val nextIdx = (fmIndex + 1).coerceAtMost(q.size - 1)
            val nextSong = q.getOrNull(nextIdx)
            if (nextSong != null) {
                fm.select(nextIdx)
                actions.playSong(nextSong, q)
            }
        },
        onSeek = { ms -> playerManager.seekTo(ms) },
        onSeekToLyric = { line -> playerManager.seekToLyric(line) },
        onExportToPlaylist = { result ->
            // 宝藏歌曲整体放进新建的歌单
            val id = library.createPlaylist(
                name = result.name,
                initialSongs = fmTreasure,
                cover = result.cover,
                sourcePlatform = result.sourcePlatform,
                sourcePlaylistId = result.sourcePlaylistId,
                sourceName = result.sourceName,
            )
            if (id != null) {
                actions.toast("已创建《${result.name}》，收录 ${fmTreasure.size} 首")
            }
        },
    )
}
