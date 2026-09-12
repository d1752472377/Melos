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
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.artist.ArtistAllSongsDestination
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.shell.AlbumDetailDestination
import com.aesthetic.music.ui.shell.ArtistDetailDestination
import com.aesthetic.music.ui.shell.CacheListDestination
import com.aesthetic.music.ui.shell.CloudManageDestination
import com.aesthetic.music.ui.shell.DownloadsDestination
import com.aesthetic.music.ui.shell.FavoritesDestination
import com.aesthetic.music.ui.shell.FmDestination
import com.aesthetic.music.ui.shell.HistoryDestination
import com.aesthetic.music.ui.shell.HomeDestination
import com.aesthetic.music.ui.shell.ListeningStatsDestination
import com.aesthetic.music.ui.shell.MyPlaylistsDestination
import com.aesthetic.music.ui.shell.PlayerOverlay
import com.aesthetic.music.ui.shell.PlaylistDetailDestination
import com.aesthetic.music.ui.shell.PlaylistTabDestination
import com.aesthetic.music.ui.shell.PluginManageDestination
import com.aesthetic.music.ui.shell.ProfileEditDestination
import com.aesthetic.music.ui.shell.ProfileTabDestination
import com.aesthetic.music.ui.shell.RanksDestination
import com.aesthetic.music.ui.shell.SearchDestination
import com.aesthetic.music.ui.shell.SettingsDestination
import com.aesthetic.music.ui.shell.SheetSquareDestination
import com.aesthetic.music.ui.shell.ShellActions
import com.aesthetic.music.ui.shell.ShellNav
import com.aesthetic.music.ui.shell.dialogs.ImportPlaylistDialog
import com.aesthetic.music.ui.shell.SettingsAppearanceDestination
import com.aesthetic.music.ui.shell.SettingsCacheDestination
import com.aesthetic.music.ui.shell.SettingsDownloadDestination
import com.aesthetic.music.ui.shell.SettingsLyricsDestination
import com.aesthetic.music.ui.shell.SettingsDownloadDestination
import com.aesthetic.music.ui.shell.SettingsPlaybackDestination
import com.aesthetic.music.ui.shell.SettingsQualityDestination
import com.aesthetic.music.ui.shell.rememberCacheState
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.applyThemeMode
import kotlinx.coroutines.launch

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
    val showImportDialog by viewModel.showImportDialog.collectAsState()

    val playerState by playerManager.state.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()

    // 动态背景色
    val pageBackground by viewModel.currentPageBackground.collectAsState()
    val isDarkPage by viewModel.isDarkPage.collectAsState()

    // ===== 缓存信息（Profile / Downloads / CacheList 共用） =====
    val cacheState = rememberCacheState(context, scope)

    LaunchedEffect(themeMode, systemDark) {
        applyThemeMode(themeMode, systemDark)
    }

    // ===== 系统状态栏显示方式（设置 → 主题与外观 → 系统状态栏） =====
    // ALWAYS：始终显示；PLAYER_ONLY：只在播放页沉浸隐藏；HIDDEN：所有页面隐藏
    val userPrefs by viewModel.settings.prefs.collectAsState()
    val activity = context as? androidx.activity.ComponentActivity
    LaunchedEffect(userPrefs.statusBarMode, showPlayer, currentRoute) {
        val controller = activity?.let {
            androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView)
        } ?: return@LaunchedEffect
        val shouldHide = when (userPrefs.statusBarMode) {
            com.aesthetic.music.data.settings.StatusBarMode.ALWAYS -> false
            com.aesthetic.music.data.settings.StatusBarMode.PLAYER_ONLY -> showPlayer
            com.aesthetic.music.data.settings.StatusBarMode.HIDDEN -> true
        }
        // 隐藏时允许从屏幕边缘临时划出（BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE），避免"状态栏再也划不出来"
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (shouldHide) controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        else controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
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
                        HomeDestination(viewModel, nav, actions)
                    }

                    // FM 电台
                    composable(ScreenRoute.Fm.route) {
                        FmDestination(viewModel, nav, actions, playerManager)
                    }

                    // 歌单广场 Tab
                    composable(ScreenRoute.Playlist.route) {
                        PlaylistTabDestination(viewModel, nav, actions)
                    }

                    // 我的/个人中心 Tab
                    composable(ScreenRoute.Profile.route) {
                        ProfileTabDestination(viewModel, nav, actions, cacheState)
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
                    composable(ScreenRoute.ArtistAllSongs.route) {
                        ArtistAllSongsDestination(viewModel, nav, actions, playerManager)
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
                        CacheListDestination(viewModel, nav, cacheState, scope)
                    }

                    // ===== 设置子页面（设置主页只做入口，各项独立成页） =====
                    composable(ScreenRoute.SettingsAppearance.route) {
                        SettingsAppearanceDestination(viewModel, nav)
                    }

                    composable(ScreenRoute.SettingsQuality.route) {
                        SettingsQualityDestination(viewModel, nav)
                    }

                    composable(ScreenRoute.SettingsPlayback.route) {
                        SettingsPlaybackDestination(viewModel, nav)
                    }

                    composable(ScreenRoute.SettingsLyrics.route) {
                        SettingsLyricsDestination(viewModel, nav)
                    }

                    composable(ScreenRoute.SettingsDownload.route) {
                        SettingsDownloadDestination(viewModel, nav)
                    }

                    composable(ScreenRoute.SettingsCache.route) {
                        SettingsCacheDestination(viewModel, nav, cacheState, scope)
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
                        DownloadsDestination(nav, actions, playerManager, cacheState, scope)
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
                        PluginManageDestination(viewModel, nav, actions.toast)
                    }

                    // 设置页
                    composable(ScreenRoute.Settings.route) {
                        SettingsDestination(viewModel, nav, actions, scope)
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

                // ===== 歌单导入弹窗：粘贴链接 / 识别文案 / 登录账号（占位） =====
                if (showImportDialog) {
                    val importState by viewModel.playlistImportState.collectAsState()
                    ImportPlaylistDialog(
                        state = importState,
                        onDismiss = {
                            viewModel.resetPlaylistImportState()
                            viewModel.closeImportDialog()
                        },
                        onImport = { text ->
                            viewModel.importPlaylistFromText(text) { item ->
                                viewModel.closeImportDialog()
                                viewModel.resetPlaylistImportState()
                                // 导入结果在收藏歌单里，走与收藏行一致的详情打开路径
                                viewModel.openPlaylistDetail(item)
                                navController.navigate(ScreenRoute.PlaylistDetail.route)
                            }
                        },
                        onLoginImport = { platformName ->
                            viewModel.showToast("「$platformName」账号导入功能开发中，敬请期待")
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