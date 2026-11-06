package com.aesthetic.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.shell.ArtistAllSongsDestination
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.shell.AlbumDetailDestination
import com.aesthetic.music.ui.shell.AppUpdateDialog
import com.aesthetic.music.ui.shell.ArtistDetailDestination
import com.aesthetic.music.ui.shell.CacheListDestination
import com.aesthetic.music.ui.shell.CloudManageDestination
import com.aesthetic.music.ui.shell.DownloadsDestination
import com.aesthetic.music.ui.shell.FavoritesDestination
import com.aesthetic.music.ui.shell.FmDestination
import com.aesthetic.music.ui.shell.GuessDestination
import com.aesthetic.music.ui.shell.HistoryDestination
import com.aesthetic.music.ui.shell.HomeDestination
import com.aesthetic.music.ui.shell.MelosToastHost
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
import com.aesthetic.music.ui.theme.applyThemeMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun AppShell(viewModel: MainViewModel, playerManager: PlayerManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        com.aesthetic.music.service.LogContext.currentPage = { route }
        com.aesthetic.music.service.LogReporter.breadcrumb("Nav", route)
    }

    val tab by viewModel.currentTab.collectAsState()
    val showPlayer by viewModel.showPlayer.collectAsState()
    val showImportDialog by viewModel.showImportDialog.collectAsState()

    val playerState by playerManager.state.collectAsState()
    val themeMode by viewModel.settings.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()

    val pageBackground by viewModel.currentPageBackground.collectAsState()
    val isDarkPage by viewModel.isDarkPage.collectAsState()
    val localStats by viewModel.localStats.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()

    val cacheState = rememberCacheState(context, scope)

    LaunchedEffect(themeMode, systemDark) {
        applyThemeMode(themeMode, systemDark)
    }

    val userPrefs by viewModel.settings.prefs.collectAsState()
    val activity = context as? androidx.activity.ComponentActivity
    val hideStatusBar = when (userPrefs.statusBarMode) {
        com.aesthetic.music.data.settings.StatusBarMode.ALWAYS -> false
        com.aesthetic.music.data.settings.StatusBarMode.PLAYER_ONLY -> showPlayer
        com.aesthetic.music.data.settings.StatusBarMode.HIDDEN -> true
    }
    LaunchedEffect(hideStatusBar, currentRoute) {
        val controller = activity?.let {
            androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView)
        } ?: return@LaunchedEffect
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideStatusBar) controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        else controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
    }

    val sessionRestored = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!sessionRestored.value) {
            playerManager.restoreSession()
            viewModel.content.loadHotPlaylists()
            viewModel.content.loadContent()
            sessionRestored.value = true
        }
    }

    val lastError = playerState.lastError
    LaunchedEffect(lastError) {
        if (lastError != null) {
            viewModel.showToast(lastError)
            playerManager.consumeError()
        }
    }

    val appToast by viewModel.appToast.collectAsState()

    LaunchedEffect(Unit) {
        playerManager.onPlaybackStarted = { song -> viewModel.library.addToHistory(song) }
        playerManager.onSongCompleted = { song -> viewModel.fm.onSignal(song) }
        com.aesthetic.music.service.LogContext.playerState = {
            val st = playerManager.state.value
            val song = st.currentSong
            val prog = playerManager.progress.value
            buildMap {
                if (song != null) {
                    put("song", "${song.title} - ${song.artist}")
                    put("songId", "${song.platform}:${song.id}")
                }
                put("isPlaying", st.isPlaying.toString())
                put("positionMs", prog.positionMs.toString())
                put("durationMs", prog.durationMs.toString())
                put("playMode", st.playMode.name)
                put("speed", "${st.speed}x")
                st.errorMessage?.let { put("lastPlayError", it) }
            }
        }
        com.aesthetic.music.service.LogContext.activeSource = {
            com.aesthetic.music.data.source.SourceManager.lastSourceName.value ?: "官方直连"
        }
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
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    val nav = remember(navController) {
        var lastPopBackTime = 0L
        var lastNavigateTime = 0L
        ShellNav(
            navigate = { route: String ->
                val now = SystemClock.uptimeMillis()
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
            downloadSong = { song -> viewModel.downloadSong(context, song) },
            toast = { msg -> viewModel.showToast(msg) },
        )
    }

    val activeTab = when (currentRoute) {
        ScreenRoute.Home.route -> AppTab.HOME
        ScreenRoute.Fm.route -> AppTab.SHEET
        ScreenRoute.Playlist.route -> AppTab.PLAYLIST
        ScreenRoute.Profile.route -> AppTab.PROFILE
        else -> tab
    }

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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
                if (pageBackground != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(pageBackground!!)
                    )
                } else {
                    HomeAmbientGlowBackground()
                }

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
                composable(ScreenRoute.Home.route) {
                    HomeDestination(viewModel.content, viewModel.playlistDetail, nav, actions, playerState.isPlaying)
                }

                composable(ScreenRoute.Fm.route) {
                    FmDestination(viewModel.fm, viewModel.library, actions, playerManager, viewModel::updatePageBackground)
                }

                composable(ScreenRoute.Playlist.route) {
                    PlaylistTabDestination(viewModel.library, viewModel.playlistImport, viewModel.playlistDetail, nav, actions, viewModel::openImportDialog)
                }

                composable(ScreenRoute.Profile.route) {
                    ProfileTabDestination(viewModel.library, viewModel.sources, viewModel.auth, localStats, isCheckingUpdate, { viewModel.checkForUpdate(manual = true) }, nav, actions, cacheState)
                }

                composable(ScreenRoute.Search.route) {
                    SearchDestination(viewModel.search, viewModel.artistPage, viewModel.playlistDetail, viewModel.library, nav, actions, playerManager)
                }

                composable(ScreenRoute.Ranks.route) { RanksDestination(viewModel.rank, viewModel.playlistDetail, nav) }

                composable(ScreenRoute.GuessYouLike.route) {
                    GuessDestination(viewModel.guess, viewModel.library, nav, actions, playerManager)
                }

                composable(ScreenRoute.SheetSquare.route) {
                    SheetSquareDestination(viewModel.sheet, viewModel.playlistDetail, nav)
                }

                composable(ScreenRoute.PlaylistDetail.route) {
                    PlaylistDetailDestination(viewModel.playlistDetail, viewModel.library, nav, actions, playerManager, viewModel::updatePageBackground)
                }

                composable(ScreenRoute.AlbumDetail.route) {
                    AlbumDetailDestination(viewModel.playlistDetail, viewModel.library, nav, actions, playerManager, viewModel::updatePageBackground)
                }

                composable(ScreenRoute.ArtistDetail.route) {
                    ArtistDetailDestination(viewModel.artistPage, viewModel.library, nav, actions, playerManager)
                }
                composable(ScreenRoute.ArtistAllSongs.route) {
                    ArtistAllSongsDestination(viewModel.artistPage, viewModel.library, actions, playerManager)
                }

                composable(ScreenRoute.Favorites.route) {
                    FavoritesDestination(viewModel.library, nav, actions, playerManager, viewModel::updatePageBackground, viewModel::openPlayer)
                }

                composable(ScreenRoute.CacheList.route) {
                    CacheListDestination(cacheState, scope)
                }

                composable(ScreenRoute.SettingsAppearance.route) {
                    SettingsAppearanceDestination(viewModel.settings, nav, viewModel::updatePageBackground)
                }

                composable(ScreenRoute.SettingsQuality.route) {
                    SettingsQualityDestination(viewModel.settings, nav, viewModel::updatePageBackground)
                }

                composable(ScreenRoute.SettingsPlayback.route) {
                    SettingsPlaybackDestination(viewModel.settings, nav, viewModel::updatePageBackground)
                }

                composable(ScreenRoute.SettingsLyrics.route) {
                    SettingsLyricsDestination(viewModel.settings, nav, viewModel::updatePageBackground)
                }

                composable(ScreenRoute.SettingsDownload.route) {
                    SettingsDownloadDestination(viewModel.settings, nav, viewModel::updatePageBackground)
                }

                composable(ScreenRoute.SettingsCache.route) {
                    SettingsCacheDestination(viewModel.settings, nav, cacheState, scope, viewModel::updatePageBackground, actions.toast)
                }

                composable(ScreenRoute.History.route) {
                    HistoryDestination(viewModel.library, nav, actions, playerManager, viewModel::updatePageBackground, viewModel::openPlayer)
                }

                composable(ScreenRoute.ProfileEdit.route) {
                    ProfileEditDestination(viewModel.auth, nav, actions)
                }

                composable(ScreenRoute.Downloads.route) {
                    DownloadsDestination(nav, actions, playerManager, cacheState, scope)
                }

                composable(ScreenRoute.ListeningStats.route) {
                    ListeningStatsDestination(viewModel.listeningStats, viewModel::refreshListeningStats, viewModel.settings, nav)
                }

                composable(ScreenRoute.CloudManage.route) {
                    CloudManageDestination(viewModel.auth, viewModel.library, nav)
                }

                composable(ScreenRoute.PluginManage.route) {
                    PluginManageDestination(viewModel.sources, viewModel.settings, nav, actions.toast)
                }

                composable(ScreenRoute.Equalizer.route) {
                    com.aesthetic.music.ui.settings.EqualizerScreen(
                        onBack = nav.popBack
                    )
                }

                composable(ScreenRoute.Settings.route) {
                    SettingsDestination(viewModel.settings, isCheckingUpdate, { cb -> viewModel.checkForUpdate(onResult = cb) }, nav)
                }
            }
        }

            if (!hideStatusBar) {
                GlobalStatusBarGlass(
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    glassProgress = glassProgress,
                )
            }

            NavigationDock(
                modifier = Modifier.align(Alignment.BottomCenter),
                currentTab = activeTab,
                currentArtwork = playerState.currentSong?.artwork,
                isSpinning = playerState.isPlaying,
                progressFlow = playerManager.progress,
                onHome = { nav.switchTab(ScreenRoute.Home.route, AppTab.HOME) },
                onProfile = { nav.switchTab(ScreenRoute.Profile.route, AppTab.PROFILE) },
                onOpenPlayer = {
                    playerManager.startProgress()
                    viewModel.openPlayer()
                },
                isDarkBackground = isDarkPage,
                hazeState = hazeState,
            )

            // favoriteKeys 必须在这里 collect：播放页的「喜欢」按钮要跟着收藏状态重组，
            val favoriteKeys by viewModel.library.favorites.collectAsState()
            PlayerOverlay(
                visible = showPlayer,
                playerState = playerState,
                playerManager = playerManager,
                library = viewModel.library,
                artist = viewModel.artistPage,
                search = viewModel.search,
                settings = viewModel.settings,
                nav = nav,
                actions = actions,
                onClose = { viewModel.closePlayer() },
                favoriteKeys = favoriteKeys,
                hazeState = hazeState,
            )

            if (showImportDialog) {
                val importState by viewModel.playlistImport.importState.collectAsState()
                ImportPlaylistDialog(
                    state = importState,
                    onDismiss = {
                        viewModel.playlistImport.resetImport()
                        viewModel.closeImportDialog()
                    },
                    onImport = { text ->
                        viewModel.playlistImport.importFromText(text) { item ->
                            viewModel.closeImportDialog()
                            viewModel.playlistImport.resetImport()
                            viewModel.playlistDetail.open(item)
                            navController.navigate(ScreenRoute.PlaylistDetail.route)
                        }
                    },
                    onLoginImport = { platformName ->
                        viewModel.showToast("?$platformName」账号导入功能开发中，敬请期?")
                    },
                )
            }

            val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()
            appUpdateInfo?.let { info ->
                AppUpdateDialog(
                    info = info,
                    onDismiss = { viewModel.dismissAppUpdate() },
                    onError = viewModel::showToast,
                )
            }

            BackHandler(enabled = showPlayer) {
                viewModel.closePlayer()
            }

            MelosToastHost(
                message = appToast,
                onDismiss = { viewModel.consumeToast() },
            )
        }
    }
}