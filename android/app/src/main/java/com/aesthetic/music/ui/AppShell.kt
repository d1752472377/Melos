package com.aesthetic.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.service.FloatCapsuleManager
import com.aesthetic.music.service.UpdateService
import com.aesthetic.music.ui.artist.ArtistScreen
import com.aesthetic.music.ui.cache.CacheListScreen
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.favorites.FavoritesScreen
import com.aesthetic.music.ui.fm.FmScreen
import com.aesthetic.music.ui.home.HomeScreen
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistDetailScreen
import com.aesthetic.music.ui.playlist.PlaylistScreen
import com.aesthetic.music.ui.profile.CloudManageScreen
import com.aesthetic.music.ui.profile.DownloadsScreen
import com.aesthetic.music.ui.profile.HistoryScreen
import com.aesthetic.music.ui.profile.ListeningStatsScreen
import com.aesthetic.music.ui.profile.MyPlaylistsScreen
import com.aesthetic.music.ui.profile.PluginManageScreen
import com.aesthetic.music.ui.profile.ProfileEditScreen
import com.aesthetic.music.ui.profile.ProfileScreen
import com.aesthetic.music.ui.rank.RankPage
import com.aesthetic.music.ui.search.SearchScreen
import com.aesthetic.music.ui.settings.SettingsScreen
import com.aesthetic.music.ui.shell.AlbumDetailDestination
import com.aesthetic.music.ui.shell.ArtistDetailDestination
import com.aesthetic.music.ui.shell.CacheListDestination
import com.aesthetic.music.ui.shell.CloudManageDestination
import com.aesthetic.music.ui.shell.DownloadsDestination
import com.aesthetic.music.ui.shell.FavoritesDestination
import com.aesthetic.music.ui.shell.HistoryDestination
import com.aesthetic.music.ui.shell.ListeningStatsDestination
import com.aesthetic.music.ui.shell.MyPlaylistsDestination
import com.aesthetic.music.ui.shell.PlayerOverlay
import com.aesthetic.music.ui.shell.PlaylistDetailDestination
import com.aesthetic.music.ui.shell.PluginManageDestination
import com.aesthetic.music.ui.shell.ProfileEditDestination
import com.aesthetic.music.ui.shell.RanksDestination
import com.aesthetic.music.ui.shell.SearchDestination
import com.aesthetic.music.ui.shell.SettingsDestination
import com.aesthetic.music.ui.shell.SheetSquareDestination
import com.aesthetic.music.ui.shell.ShellActions
import com.aesthetic.music.ui.shell.ShellNav
import com.aesthetic.music.ui.shell.dialogs.ImportPlaylistDialog
import com.aesthetic.music.ui.shell.rememberCacheState
import com.aesthetic.music.ui.sheet.PlaylistPage
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.applyThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppShell(viewModel: MainViewModel, playerManager: PlayerManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 官方 Jetpack Navigation 导航控制器：统一管理页面切换与回退栈
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val tab by viewModel.currentTab.collectAsState()
    val showPlayer by viewModel.showPlayer.collectAsState()
    val showPlaylistDetail by viewModel.showPlaylistDetail.collectAsState()
    val showImportDialog by viewModel.showImportDialog.collectAsState()
    val showArtist by viewModel.showArtist.collectAsState()
    val showAlbumDetail by viewModel.showAlbumDetail.collectAsState()

    val playerState by playerManager.state.collectAsState()
    val history by viewModel.history.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val artistDetail by viewModel.artistDetail.collectAsState()
    val isLoadingArtist by viewModel.isLoadingArtist.collectAsState()
    val hotPlaylists by viewModel.hotPlaylists.collectAsState()
    val playlistSongs by viewModel.playlistSongs.collectAsState()
    val isLoadingPlaylist by viewModel.isLoadingPlaylist.collectAsState()
    val favoritePlaylists by viewModel.favoritePlaylists.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val qualityLevel by viewModel.quality.collectAsState()
    val systemDark = isSystemInDarkTheme()

    // 内容体系
    val dailyPlaylists by viewModel.dailyPlaylists.collectAsState()
    val newSongs by viewModel.newSongs.collectAsState()
    val hotSongs by viewModel.hotSongs.collectAsState()
    val homeCategories by viewModel.homeCategories.collectAsState()
    val ranks by viewModel.ranks.collectAsState()
    val isLoadingContent by viewModel.isLoadingContent.collectAsState()
    val contentError by viewModel.contentError.collectAsState()

    // 歌单广场 + 排行榜页
    val sheetPlaylists by viewModel.sheetPlaylists.collectAsState()
    val sheetCat by viewModel.sheetCat.collectAsState()
    val sheetOrder by viewModel.sheetOrder.collectAsState()
    val isLoadingSheets by viewModel.isLoadingSheets.collectAsState()
    val sheetError by viewModel.sheetError.collectAsState()
    val fmStyles = viewModel.fmStyles
    val fmStyle by viewModel.fmStyle.collectAsState()
    val currentFmChannel by viewModel.currentFmChannel.collectAsState()
    val fmQueue by viewModel.fmQueue.collectAsState()
    val fmIndex by viewModel.fmIndex.collectAsState()
    val fmLoading by viewModel.fmLoading.collectAsState()
    val fmError by viewModel.fmError.collectAsState()
    val fmTreasure by viewModel.fmTreasure.collectAsState()

    // 用户与同步体系
    val user by viewModel.user.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val localStats by viewModel.localStats.collectAsState()
    val activeScriptInfo by viewModel.activeScriptInfo.collectAsState()
    val scriptCatalog by viewModel.scriptCatalog.collectAsState()
    val isEngineReady by viewModel.isEngineReady.collectAsState()
    val engineError by viewModel.engineError.collectAsState()
    val isSyncConfigured by viewModel.isSyncConfigured.collectAsState()
    val syncServerUrl by viewModel.syncServerUrl.collectAsState()
    val syncToken by viewModel.syncToken.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val lastSyncStatus by viewModel.lastSyncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val artistError by viewModel.artistError.collectAsState()

    // 个人中心个性化状态
    val profileAvatar by viewModel.profileAvatar.collectAsState()
    val profileBackground by viewModel.profileBackground.collectAsState()
    val isProfileBgBlur by viewModel.isProfileBgBlur.collectAsState()
    val profileNickname by viewModel.profileNickname.collectAsState()
    val profileBio by viewModel.profileBio.collectAsState()
    val profileGender by viewModel.profileGender.collectAsState()
    val profileBirthday by viewModel.profileBirthday.collectAsState()
    val profileUserId by viewModel.profileUserId.collectAsState()

    // 动态背景色
    val pageBackground by viewModel.currentPageBackground.collectAsState()
    val isDarkPage by viewModel.isDarkPage.collectAsState()

    // ===== 缓存信息（Profile / Downloads / CacheList 共用） =====
    val cacheState = rememberCacheState(context, scope)

    LaunchedEffect(themeMode, systemDark) {
        applyThemeMode(themeMode, systemDark)
    }

    // 启动恢复会话与内容预拉取
    val sessionRestored = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!sessionRestored.value) {
            playerManager.restoreSession()
            viewModel.loadHotPlaylists()
            viewModel.loadContent()
            sessionRestored.value = true
        }
    }

    var splashFinished by rememberSaveable { mutableStateOf(false) }
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()

    val albumDetailSongs by viewModel.albumDetailSongs.collectAsState()
    val isLoadingAlbum by viewModel.isLoadingAlbum.collectAsState()
    val albumError by viewModel.albumError.collectAsState()
    val playlistError by viewModel.playlistError.collectAsState()

    // 全局错误提示
    val lastError = playerState.lastError
    LaunchedEffect(lastError) {
        if (lastError != null) {
            snackbarHostState.showSnackbar(lastError)
            playerManager.consumeError()
        }
    }

    val appToast by viewModel.appToast.collectAsState()
    LaunchedEffect(appToast) {
        val msg = appToast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeToast()
    }

    LaunchedEffect(Unit) {
        playerManager.onPlaybackStarted = { song -> viewModel.addToHistory(song) }
        playerManager.onSongCompleted = { song -> viewModel.fmOnSignal(song) }
    }

    fun shareSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        val text = songs.mapIndexed { i, s -> "${i + 1}. ${s.title} - ${s.artist}" }.joinToString("\n")
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "我本次淘到的宝藏歌单")
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "导出歌名列表"))
    }

    fun playSong(song: Song, list: List<Song>) {
        val idx = list.indexOfFirst { it.id == song.id && it.platform == song.platform }.coerceAtLeast(0)
        playerManager.playQueue(list, idx)
        playerManager.startProgress()
    }

    fun navigateToTab(route: String, tabEnum: AppTab) {
        viewModel.switchTab(tabEnum)
        viewModel.updatePageBackground(null, false)
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 壳层 holder：收敛导航 / 播放 / 提示回调，destination 只接收 holder 而不逐个透传
    val nav = remember(navController) {
        ShellNav(
            navigate = { route: String -> navController.navigate(route) },
            navigateSingleTop = { route: String -> navController.navigate(route) { launchSingleTop = true } },
            popBack = { navController.popBackStack() },
            switchTab = { route: String, tabEnum: AppTab -> navigateToTab(route, tabEnum) },
        )
    }
    val actions = remember(playerManager, context) {
        ShellActions(
            playSong = { song, list -> playSong(song, list) },
            playAll = { list -> if (list.isNotEmpty()) playSong(list.first(), list) },
            shareSongs = { songs -> shareSongs(songs) },
            toast = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
        )
    }

    // 匹配当前 Tab
    val activeTab = when (currentRoute) {
        ScreenRoute.Home.route -> AppTab.HOME
        ScreenRoute.Fm.route -> AppTab.SHEET
        ScreenRoute.Playlist.route -> AppTab.PLAYLIST
        ScreenRoute.Profile.route -> AppTab.PROFILE
        else -> tab
    }

    val bottomBarPadding = 78.dp

    CompositionLocalProvider(LocalBottomBarPadding provides bottomBarPadding) {
        Scaffold(
            containerColor = Background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {},
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Background),
            ) {
                // ===== Jetpack Navigation 导航容器 =====
                NavHost(
                    navController = navController,
                    startDestination = ScreenRoute.Home.route,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 主页
                    composable(ScreenRoute.Home.route) {
                        HomeScreen(
                            dailyPlaylists = dailyPlaylists,
                            newSongs = newSongs,
                            hotSongs = hotSongs,
                            hotPlaylists = hotPlaylists,
                            ranks = ranks,
                            homeCategories = homeCategories,
                            favorites = favorites,
                            contentError = contentError,
                            isLoading = isLoadingContent,
                            onRetryContent = { viewModel.refreshContent() },
                            onPlaySong = { song, list -> playSong(song, list) },
                            onSearchBarClick = {
                                navController.navigate(ScreenRoute.Search.route) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenPlaylistDetail = { pl ->
                                viewModel.openPlaylistDetail(pl)
                                navController.navigate(ScreenRoute.PlaylistDetail.route)
                            },
                            onOpenSheet = { navController.navigate(ScreenRoute.SheetSquare.route) },
                            onOpenRanksPage = { navController.navigate(ScreenRoute.Ranks.route) },
                            onToggleFavorite = viewModel::toggleFavorite,
                        )
                    }

                    // FM 电台
                    composable(ScreenRoute.Fm.route) {
                        // 进入 FM 路由自动设置暗色背景，使底部 NavigationDock 呈现暗黑毛玻璃质感
                        DisposableEffect(Unit) {
                            viewModel.updatePageBackground(androidx.compose.ui.graphics.Color(0xFF0D0D11), true)
                            onDispose {
                                viewModel.updatePageBackground(null, false)
                            }
                        }

                        // 首次切入若队列为空，自动开始调频并启播
                        LaunchedEffect(Unit) {
                            if (fmQueue.isEmpty() && !fmLoading) {
                                viewModel.fmStart(viewModel.currentFmChannel.value) { firstSong, list ->
                                    playSong(firstSong, list)
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
                            channels = viewModel.fmChannels,
                            favorites = favorites,
                            currentSong = playerState.currentSong,
                            isPlaying = playerState.isPlaying,
                            isBuffering = playerState.isLoading,
                            lyrics = playerState.lyrics,
                            syncedLyrics = playerState.syncedLyrics,
                            progressFlow = playerManager.progress,
                            onSelectChannel = { channel ->
                                viewModel.fmStart(channel) { firstSong, list ->
                                    playSong(firstSong, list)
                                }
                            },
                            onRefreshRecommendation = {
                                viewModel.fmRefresh { firstSong, list ->
                                    playSong(firstSong, list)
                                }
                            },
                            onSelect = { index ->
                                viewModel.fmSelect(index)
                                val q = viewModel.fmQueue.value
                                val target = q.getOrNull(index)
                                val alreadySame = (target?.id != null && playerState.currentSong?.id == target.id)
                                if (target != null && !alreadySame) playSong(target, q)
                            },
                            onToggleLike = { song -> viewModel.fmToggleLike(song) },
                            onDislike = { song ->
                                viewModel.fmDislike(song) { nextSong, list ->
                                    playSong(nextSong, list)
                                }
                            },
                            onPlayPause = { playerManager.toggle() },
                            onNext = {
                                val q = viewModel.fmQueue.value
                                val nextIdx = (fmIndex + 1).coerceAtMost(q.size - 1)
                                val nextSong = q.getOrNull(nextIdx)
                                if (nextSong != null) {
                                    viewModel.fmSelect(nextIdx)
                                    playSong(nextSong, q)
                                }
                            },
                            onPrevious = {
                                val q = viewModel.fmQueue.value
                                val prevIdx = (fmIndex - 1).coerceAtLeast(0)
                                val prevSong = q.getOrNull(prevIdx)
                                if (prevSong != null) {
                                    viewModel.fmSelect(prevIdx)
                                    playSong(prevSong, q)
                                }
                            },
                            onSeek = { ms -> playerManager.seekTo(ms) },
                            onSeekToLyric = { line -> playerManager.seekToLyric(line) },
                            onExport = { songs -> shareSongs(songs) }
                        )
                    }

                    // 歌单广场 Tab
                    composable(ScreenRoute.Playlist.route) {
                        PlaylistScreen(
                            myPlaylists = myPlaylists,
                            favoritePlaylists = favoritePlaylists,
                            onOpenMyPlaylist = { id ->
                                viewModel.openMyPlaylist(id)
                                navController.navigate(ScreenRoute.PlaylistDetail.route)
                            },
                            onOpenPlaylistDetail = { pl ->
                                viewModel.openPlaylistDetail(pl)
                                navController.navigate(ScreenRoute.PlaylistDetail.route)
                            },
                            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                            onRenamePlaylist = viewModel::renamePlaylist,
                            onDeletePlaylist = viewModel::deletePlaylist,
                            onOpenImport = { viewModel.openImportDialog() },
                            onPlayPlaylist = { songs ->
                                if (songs.isNotEmpty()) {
                                    playSong(songs.first(), songs)
                                }
                            },
                        )
                    }

                    // 我的/个人中心 Tab
                    composable(ScreenRoute.Profile.route) {
                        ProfileScreen(
                            nickname = profileNickname,
                            userId = profileUserId,
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
                            activeScript = activeScriptInfo,
                            onOpenEditProfile = { navController.navigate(ScreenRoute.ProfileEdit.route) },
                            onOpenFavorites = { navController.navigate(ScreenRoute.Favorites.route) },
                            onOpenMyPlaylists = { navController.navigate(ScreenRoute.MyPlaylists.route) },
                            onOpenDownloads = {
                                cacheState.refresh()
                                navController.navigate(ScreenRoute.Downloads.route)
                            },
                            onOpenHistory = {
                                viewModel.openHistory()
                                navController.navigate(ScreenRoute.History.route)
                            },
                            onOpenListeningStats = { navController.navigate(ScreenRoute.ListeningStats.route) },
                            onOpenCloudManage = { navController.navigate(ScreenRoute.CloudManage.route) },
                            onOpenSettings = { navController.navigate(ScreenRoute.Settings.route) },
                            onOpenPluginManage = { navController.navigate(ScreenRoute.PluginManage.route) },
                            onUpdateAvatar = { path -> viewModel.setProfileAvatar(path) },
                            onUpdateBackground = { path -> viewModel.setProfileBackground(path) },
                            onToggleBgBlur = { viewModel.toggleProfileBgBlur() },
                            onRestoreDefaults = { viewModel.restoreProfileDefaults(context) },
                            onLogout = { viewModel.logout() },
                            onShowToast = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                        )
                    }

                    // 全局搜索页
                    composable(ScreenRoute.Search.route) {
                        SearchDestination(viewModel, nav, actions, playerManager)
                    }

                    // 排行榜列表页
                    composable(ScreenRoute.Ranks.route) { RanksDestination(viewModel, nav) }

                    // 歌单广场（分类标签歌单）
                    composable(ScreenRoute.SheetSquare.route) {
                        SheetSquareDestination(viewModel, nav)
                    }

                    // 歌单详情页
                    composable(ScreenRoute.PlaylistDetail.route) {
                        PlaylistDetailDestination(viewModel, nav, actions, playerManager)
                    }

                    // 专辑详情页
                    composable(ScreenRoute.AlbumDetail.route) {
                        AlbumDetailDestination(viewModel, nav, actions, playerManager)
                    }

                    // 歌手页
                    composable(ScreenRoute.ArtistDetail.route) {
                        ArtistDetailDestination(viewModel, nav, actions, playerManager)
                    }

                    // 我的收藏页
                    composable(ScreenRoute.Favorites.route) {
                        FavoritesDestination(viewModel, nav, actions, playerManager)
                    }

                    // 我的歌单页
                    composable(ScreenRoute.MyPlaylists.route) {
                        MyPlaylistsDestination(viewModel, nav, actions, playerManager)
                    }

                    // 缓存列表页
                    composable(ScreenRoute.CacheList.route) {
                        CacheListDestination(viewModel, nav, cacheState)
                    }

                    // 最近播放页
                    composable(ScreenRoute.History.route) {
                        HistoryDestination(viewModel, nav, actions, playerManager)
                    }

                    // 编辑个人资料页
                    composable(ScreenRoute.ProfileEdit.route) {
                        ProfileEditDestination(viewModel, nav, actions)
                    }

                    // 本地下载页
                    composable(ScreenRoute.Downloads.route) {
                        DownloadsDestination(nav, actions, playerManager, cacheState)
                    }

                    // 听歌时长页
                    composable(ScreenRoute.ListeningStats.route) {
                        ListeningStatsDestination(viewModel, nav)
                    }

                    // 云端管理页
                    composable(ScreenRoute.CloudManage.route) {
                        CloudManageDestination(viewModel, nav)
                    }

                    // 插件管理页
                    composable(ScreenRoute.PluginManage.route) {
                        PluginManageDestination(viewModel, nav)
                    }

                    // 设置页
                    composable(ScreenRoute.Settings.route) {
                        SettingsDestination(viewModel, nav, actions)
                    }
                }

                // ===== 底部导航 Dock =====
                NavigationDock(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    currentTab = activeTab,
                    currentArtwork = playerState.currentSong?.artwork,
                    isSpinning = playerState.isPlaying,
                    progressFlow = playerManager.progress,
                    onHome = { nav.switchTab(ScreenRoute.Home.route, AppTab.HOME) },
                    onSheet = { nav.switchTab(ScreenRoute.Fm.route, AppTab.SHEET) },
                    onPlaylist = { nav.switchTab(ScreenRoute.Playlist.route, AppTab.PLAYLIST) },
                    onProfile = { nav.switchTab(ScreenRoute.Profile.route, AppTab.PROFILE) },
                    onOpenPlayer = {
                        playerManager.startProgress()
                        viewModel.openPlayer()
                    },
                    isDarkBackground = isDarkPage,
                    containerColor = pageBackground as? Color ?: Color.White,
                )

                // ===== 全屏播放器 Drawer =====
                PlayerOverlay(
                    visible = showPlayer,
                    playerState = playerState,
                    playerManager = playerManager,
                    viewModel = viewModel,
                )

                // ===== 歌单导入弹窗 =====
                if (showImportDialog) {
                    ImportPlaylistDialog(
                        onDismiss = { viewModel.closeImportDialog() },
                        onImport = { id ->
                            viewModel.closeImportDialog()
                            viewModel.importPlaylist(id)
                        },
                    )
                }

                // ===== 系统返回键处理 =====
                // 仅需处理全屏浮层播放器：其余所有页面回退统一由 NavController 回退栈原生全自动接管！
                BackHandler(enabled = showPlayer) {
                    viewModel.closePlayer()
                }

                // ===== 品牌开屏 =====
                if (!splashFinished) {
                    SplashScreen(onFinished = { splashFinished = true })
                }
            }
        }
    }
}