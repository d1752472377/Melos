package com.aesthetic.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.service.UpdateService
import com.aesthetic.music.ui.components.AppDialog
import com.aesthetic.music.ui.components.MelosToastHost
import com.aesthetic.music.ui.theme.TextSoft
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.aesthetic.music.ui.home.GlobalStatusBarGlass
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.applyThemeMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun AppShell(viewModel: MainViewModel, playerManager: PlayerManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 官方 Jetpack Navigation 导航控制器：统一管理页面切换与回退�?
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

    // ===== 缓存信息（Profile / Downloads / CacheList 共用�? =====
    val cacheState = rememberCacheState(context, scope)

    LaunchedEffect(themeMode, systemDark) {
        applyThemeMode(themeMode, systemDark)
    }

    // ===== 系统状态栏显示方式（设�? �? 主题与外�? �? 系统状态栏�? =====
    // ALWAYS：始终显示；PLAYER_ONLY：只在播放页沉浸隐藏；HIDDEN：所有页面隐�?
    val userPrefs by viewModel.settings.prefs.collectAsState()
    val activity = context as? androidx.activity.ComponentActivity
    // 提到组合外，供下�? Scaffold 决定是否为状态栏预留高度（隐藏时应当让内容顶上去�?
    val hideStatusBar = when (userPrefs.statusBarMode) {
        com.aesthetic.music.data.settings.StatusBarMode.ALWAYS -> false
        com.aesthetic.music.data.settings.StatusBarMode.PLAYER_ONLY -> showPlayer
        com.aesthetic.music.data.settings.StatusBarMode.HIDDEN -> true
    }
    LaunchedEffect(hideStatusBar, currentRoute) {
        val controller = activity?.let {
            androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView)
        } ?: return@LaunchedEffect
        // 隐藏时允许从屏幕边缘临时划出（BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE），避免"状态栏再也划不出来"
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideStatusBar) controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
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

    // 全局错误提示
    val lastError = playerState.lastError
    LaunchedEffect(lastError) {
        if (lastError != null) {
            viewModel.showToast(lastError)
            playerManager.consumeError()
        }
    }

    val appToast by viewModel.appToast.collectAsState()

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

    /**
     * 底部导航切 Tab：Tab 是一级页面，切走时把该 Tab 上叠加的所有子页面清掉。
     *
     * 之前用了 saveState + restoreState，会把"进入多深"的栈原样恢复：
     * 设置 → 子页 → 子页，切到首页再切回来仍停在最深那页，只能一页页退。
     * 现在不保存也不恢复，每次点 Tab 都回到该 Tab 的根。
     */
    fun navigateToTab(route: String, tabEnum: AppTab) {
        viewModel.switchTab(tabEnum)
        viewModel.updatePageBackground(null, false)
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    // 壳层 holder：收敛导航 / 播放 / 提示回调，destination 只接收 holder 而不逐个透传
    val nav = remember(navController) {
        var lastPopBackTime = 0L
        var lastNavigateTime = 0L
        ShellNav(
            navigate = { route: String ->
                val now = SystemClock.uptimeMillis()
                // 返回动效过程中（260ms内）忽略对旧页面的误触跳转，且防重复点击
                if (now - lastPopBackTime > 260L && now - lastNavigateTime > 300L) {
                    lastNavigateTime = now
                    navController.navigate(route)
                }
            },
            navigateSingleTop = { route: String ->
                val now = SystemClock.uptimeMillis()
                if (now - lastPopBackTime > 260L && now - lastNavigateTime > 300L) {
                    lastNavigateTime = now
                    navController.navigate(route) { launchSingleTop = true }
                }
            },
            popBack = {
                val now = SystemClock.uptimeMillis()
                if (now - lastPopBackTime > 200L) {
                    lastPopBackTime = now
                    navController.popBackStack()
                }
            },
            switchTab = { route: String, tabEnum: AppTab -> navigateToTab(route, tabEnum) },
        )
    }
    val actions = remember(playerManager, context) {
        ShellActions(
            playSong = { song, list -> playSong(song, list) },
            playAll = { list -> if (list.isNotEmpty()) playSong(list.first(), list) },
            shareSongs = { songs -> shareSongs(songs) },
            toast = { msg -> viewModel.showToast(msg) },
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

    // 底部不再为悬浮 Dock 预留高度：内容一直铺到屏幕底部（列表末尾会从 Dock 底下穿过）
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {},
        bottomBar = {},
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            var scrollOffset by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(currentRoute) {
                scrollOffset = 0f
            }
            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val delta = available.y
                        if (delta < 0f) {
                            scrollOffset = (scrollOffset - delta).coerceAtMost(160f)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        val delta = consumed.y + available.y
                        if (delta > 0f) {
                            scrollOffset = (scrollOffset - delta).coerceAtLeast(0f)
                        }
                        return Offset.Zero
                    }
                }
            }

            val glassProgress by animateFloatAsState(
                targetValue = if (scrollOffset > 6f) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "statusBarGlassProgress"
            )

            val hazeState = remember { HazeState() }

            // 整个内容层作为 Haze 真实高斯模糊数据源
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
                // 全局底层背景：全应用共享主页同款 iOS 拟态环境光底色（特定页面有 pageBackground 则优先使用）
                if (pageBackground != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(pageBackground!!)
                    )
                } else {
                    HomeAmbientGlowBackground()
                }

                // ===== Jetpack Navigation 导航容器 =====
                NavHost(
                    navController = navController,
                    startDestination = ScreenRoute.Home.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                            initialOffset = { it / 4 },
                        ) + fadeIn(animationSpec = tween(200))
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            targetOffset = { -it / 5 },
                        ) + fadeOut(animationSpec = tween(180))
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            initialOffset = { -it / 5 },
                        ) + fadeIn(animationSpec = tween(200))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(200, easing = FastOutSlowInEasing),
                            targetOffset = { it },
                        ) + fadeOut(animationSpec = tween(160))
                    },
                ) {
                // 主页
                composable(ScreenRoute.Home.route) {
                    HomeDestination(viewModel, nav, actions, playerState.isPlaying)
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

                // 全局搜索�?
                composable(ScreenRoute.Search.route) {
                    SearchDestination(viewModel, nav, actions, playerManager)
                }

                // 排行榜列表页
                composable(ScreenRoute.Ranks.route) { RanksDestination(viewModel, nav) }

                // 歌单广场（分类标签歌单）
                composable(ScreenRoute.SheetSquare.route) {
                    SheetSquareDestination(viewModel, nav)
                }

                // 歌单详情�?
                composable(ScreenRoute.PlaylistDetail.route) {
                    PlaylistDetailDestination(viewModel, nav, actions, playerManager)
                }

                // 专辑详情�?
                composable(ScreenRoute.AlbumDetail.route) {
                    AlbumDetailDestination(viewModel, nav, actions, playerManager)
                }

                // 歌手�?
                composable(ScreenRoute.ArtistDetail.route) {
                    ArtistDetailDestination(viewModel, nav, actions, playerManager)
                }
                composable(ScreenRoute.ArtistAllSongs.route) {
                    ArtistAllSongsDestination(viewModel, nav, actions, playerManager)
                }

                // 我的收藏�?
                composable(ScreenRoute.Favorites.route) {
                    FavoritesDestination(viewModel, nav, actions, playerManager)
                }

                // 缓存列表�?
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

                // 编辑个人资料�?
                composable(ScreenRoute.ProfileEdit.route) {
                    ProfileEditDestination(viewModel, nav, actions)
                }

                // 本地下载�?
                composable(ScreenRoute.Downloads.route) {
                    DownloadsDestination(nav, actions, playerManager, cacheState, scope)
                }

                // 听歌时长�?
                composable(ScreenRoute.ListeningStats.route) {
                    ListeningStatsDestination(viewModel, nav)
                }

                // 云端管理�?
                composable(ScreenRoute.CloudManage.route) {
                    CloudManageDestination(viewModel, nav)
                }

                // 插件管理
                composable(ScreenRoute.PluginManage.route) {
                    PluginManageDestination(viewModel, nav, actions.toast)
                }

                // 专业调音台 (10段图形均衡器)
                composable(ScreenRoute.Equalizer.route) {
                    com.aesthetic.music.ui.settings.EqualizerScreen(
                        onBack = nav.popBack
                    )
                }

                // 设置页
                composable(ScreenRoute.Settings.route) {
                    SettingsDestination(viewModel, nav, actions, scope)
                }
            }
        }

            // ===== 全局顶部通知栏/状态栏毛玻璃悬浮遮罩 =====
            if (!hideStatusBar) {
                GlobalStatusBarGlass(
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    glassProgress = glassProgress,
                )
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
                // 复用内容层已经注册过的 Haze 数据源（本文件 :308 创建、:314 `.hazeSource`），
                // 让导航条得到 GZ 真机那种「真实背景模糊」的透明玻璃（而非半透明纯色）。
                hazeState = hazeState,
            )

            // ===== 全屏播放器 Drawer =====
            PlayerOverlay(
                visible = showPlayer,
                playerState = playerState,
                playerManager = playerManager,
                viewModel = viewModel,
                nav = nav,
                hazeState = hazeState,
            )

            // ===== 歌单导入弹窗：粘贴链? / 识别文案 / 登录账号（占位） =====
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
                        viewModel.showToast("?$platformName」账号导入功能开发中，敬请期?")
                    },
                )
            }

            // ===== 自动版本更新提醒弹窗（支持上线即弹窗提醒与强制更新拦截） =====
            val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()
            if (appUpdateInfo != null) {
                val info = appUpdateInfo!!
                var updateProgressText by remember { mutableStateOf<String?>(null) }
                val isReady = remember(info, updateProgressText) { UpdateService.isApkReady(context, info) }

                AppDialog(
                    onDismissRequest = {
                        if (updateProgressText == null && !info.forceUpdate) {
                            viewModel.dismissAppUpdate()
                        }
                    },
                    title = if (info.forceUpdate) "发现重要更新 ${info.versionName}" else "发现新版本 ${info.versionName}",
                    confirmText = if (updateProgressText == null) {
                        if (isReady) "立即安装" else "立即更新"
                    } else null,
                    onConfirm = {
                        scope.launch {
                            val alreadyDownloaded = UpdateService.isApkReady(context, info)
                            updateProgressText = if (alreadyDownloaded) "正在打开安装程序…" else "准备下载…"
                            try {
                                val path = UpdateService.downloadApk(context, info) { received, total ->
                                    if (total > 0) {
                                        updateProgressText = "下载中 ${received * 100 / total}%"
                                    }
                                }
                                updateProgressText = "正在打开安装程序…"
                                if (UpdateService.installApk(context, path)) {
                                    if (!info.forceUpdate) viewModel.dismissAppUpdate()
                                    updateProgressText = null
                                } else {
                                    viewModel.showToast("请允许「安装未知应用」后重新安装")
                                    updateProgressText = null
                                }
                            } catch (e: Exception) {
                                viewModel.showToast("更新失败: ${e.message ?: "未知错误"}")
                                updateProgressText = null
                                if (!info.forceUpdate) viewModel.dismissAppUpdate()
                            }
                        }
                    },
                    cancelText = if (updateProgressText == null && !info.forceUpdate) "稍后再说" else null,
                    onCancel = { viewModel.dismissAppUpdate() },
                ) {
                    Column {
                        if (updateProgressText != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(updateProgressText ?: "正在处理…", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Text(
                                text = if (info.updateLog.isNotBlank()) info.updateLog else "修复已知问题，提升使用体验。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "大小: ${AudioCacheService.formatSize(info.apkSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSoft,
                                )
                                if (isReady) {
                                    Text(
                                        text = "已就绪 (免流量)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ===== 系统返回键处? =====
            // 仅需处理全屏浮层播放器：其余所有页面回退统一? NavController 回退栈原生全自动接管?
            BackHandler(enabled = showPlayer) {
                viewModel.closePlayer()
            }

            // ===== 顶部灵动胶囊通知（Top Floating Capsule Toast，彻底解决原生底部遮挡与丑陋） =====
            MelosToastHost(
                message = appToast,
                onDismiss = { viewModel.consumeToast() },
            )
        }
    }
}