package com.aesthetic.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.service.FloatCapsuleManager
import com.aesthetic.music.service.UpdateService
import com.aesthetic.music.ui.cache.CacheListScreen
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.MiniPlayer
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.artist.ArtistScreen
import com.aesthetic.music.ui.discover.DiscoverScreen
import com.aesthetic.music.ui.favorites.FavoritesScreen
import com.aesthetic.music.ui.home.HomeScreen
import com.aesthetic.music.ui.player.PlayerScreen
import com.aesthetic.music.ui.playlist.PlaylistScreen
import com.aesthetic.music.ui.profile.HistoryScreen
import com.aesthetic.music.ui.profile.MyPlaylistsScreen
import com.aesthetic.music.ui.profile.ProfileScreen
import com.aesthetic.music.ui.search.SearchScreen
import com.aesthetic.music.ui.settings.SettingsScreen
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.applyThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun AppShell(viewModel: MainViewModel, playerManager: PlayerManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val tab by viewModel.currentTab.collectAsState()
    val showPlayer by viewModel.showPlayer.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val showPlaylistDetail by viewModel.showPlaylistDetail.collectAsState()
    val showImportDialog by viewModel.showImportDialog.collectAsState()
    val showFavorites by viewModel.showFavorites.collectAsState()
    val showCacheList by viewModel.showCacheList.collectAsState()
    val showMyPlaylists by viewModel.showMyPlaylists.collectAsState()
    val showArtist by viewModel.showArtist.collectAsState()

    val playerState by playerManager.state.collectAsState()
    val history by viewModel.history.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val artistDetail by viewModel.artistDetail.collectAsState()
    val isLoadingArtist by viewModel.isLoadingArtist.collectAsState()
    val hotPlaylists by viewModel.hotPlaylists.collectAsState()
    val isLoadingHot by viewModel.isLoadingHot.collectAsState()
    val hotPlaylistsError by viewModel.hotPlaylistsError.collectAsState()
    val playlistSongs by viewModel.playlistSongs.collectAsState()
    val isLoadingPlaylist by viewModel.isLoadingPlaylist.collectAsState()
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

    // 用户与同步体系
    val user by viewModel.user.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val localStats by viewModel.localStats.collectAsState()
    val activeScriptInfo by viewModel.activeScriptInfo.collectAsState()
    val isEngineReady by viewModel.isEngineReady.collectAsState()
    val engineError by viewModel.engineError.collectAsState()
    val isSyncConfigured by viewModel.isSyncConfigured.collectAsState()
    val syncServerUrl by viewModel.syncServerUrl.collectAsState()
    val syncToken by viewModel.syncToken.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val lastSyncStatus by viewModel.lastSyncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val artistError by viewModel.artistError.collectAsState()

    // ===== 缓存信息（异步加载：避免启动组合时在主线程做磁盘 I/O + JSON 解析）=====
    var cacheSize by remember { mutableStateOf(0L) }
    var cacheCount by remember { mutableStateOf(0) }
    var cachedSongs by remember { mutableStateOf<List<AudioCacheService.CachedSong>>(emptyList()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    // 悬浮胶囊开关：状态提升到 AppShell 并用 remember 持有（直接读 SettingsStore 是瞬时快照，
    // 设置页内开关切换后不会触发重组，Switch 会显示旧值）
    var capsuleEnabled by remember { mutableStateOf(SettingsStore.isFloatingCapsuleEnabled()) }

    // 统一的缓存信息刷新入口：IO 线程读盘，主线程回写状态
    fun refreshCacheInfo() {
        scope.launch(Dispatchers.IO) {
            val songs = AudioCacheService.listCached(context)
            val size = AudioCacheService.getCacheSize(context)
            val count = AudioCacheService.getCacheCount(context)
            withContext(Dispatchers.Main) {
                cachedSongs = songs
                cacheSize = size
                cacheCount = count
            }
        }
    }

    LaunchedEffect(Unit) { refreshCacheInfo() }

    // 主题模式（跟随系统变化时也保持生效）
    LaunchedEffect(themeMode, systemDark) {
        applyThemeMode(themeMode, systemDark)
    }

    // ===== 启动：恢复断点播放 + 预加载内容 =====
    val sessionRestored = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!sessionRestored.value) {
            playerManager.restoreSession()
            viewModel.loadHotPlaylists()
            viewModel.loadContent()
            sessionRestored.value = true
        }
    }

    // 品牌开屏（3 秒）：rememberSaveable —— 此前进程重建/配置变更后 splash 重新播 3 秒，
    // 用户误以为 App 卡死
    var splashFinished by rememberSaveable { mutableStateOf(false) }

    // 收藏歌曲明细（持久化数据，直接来自 FavoriteStore）
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()

    // 全局搜索 / 最近播放 / 专辑详情 覆盖层
    val showSearch by viewModel.showSearch.collectAsState()
    val showHistory by viewModel.showHistory.collectAsState()
    val showAlbumDetail by viewModel.showAlbumDetail.collectAsState()
    val albumDetailSongs by viewModel.albumDetailSongs.collectAsState()
    val isLoadingAlbum by viewModel.isLoadingAlbum.collectAsState()
    val albumError by viewModel.albumError.collectAsState()
    val playlistError by viewModel.playlistError.collectAsState()

    // ===== 全局错误提示（参考 App 的 lastError 消费机制，防重复弹） =====
    val lastError = playerState.lastError
    LaunchedEffect(lastError) {
        if (lastError != null) {
            snackbarHostState.showSnackbar(lastError)
            playerManager.consumeError()
        }
    }

    // ===== 一次性全局提示（导入失败/改昵称失败/登录态过期等，来自 MainViewModel.appToast） =====
    val appToast by viewModel.appToast.collectAsState()
    LaunchedEffect(appToast) {
        // 委托属性不能 smart cast，先落到局部变量
        val msg = appToast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeToast()
    }

    // 播放真实开始（isPlaying 首次翻真）才写历史：此前 playSong 一调就记，
    // 点了但加载失败/立即切走的一次性点击也会污染最近播放
    LaunchedEffect(Unit) {
        playerManager.onPlaybackStarted = { song -> viewModel.addToHistory(song) }
    }

    fun playSong(song: Song, list: List<Song>) {
        val idx = list.indexOfFirst { it.id == song.id && it.platform == song.platform }.coerceAtLeast(0)
        playerManager.playQueue(list, idx)
        playerManager.startProgress()
    }

    val hasActiveSong = playerState.currentSong != null
    val targetBottomPadding = if (hasActiveSong) 124.dp else 68.dp
    val animatedBottomPadding by animateDpAsState(
        targetValue = targetBottomPadding,
        animationSpec = spring(stiffness = 380f),
        label = "bottomPadding"
    )

    CompositionLocalProvider(LocalBottomBarPadding provides animatedBottomPadding) {
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
            // ===== 内容页 =====
            // SaveableStateHolder：切 Tab 离开组合时保存滚动位置/输入等 rememberSaveable 状态，
            // 回来时恢复（此前每次切 Tab 滚动位置直接丢失回到顶部）
            val pageStateHolder = rememberSaveableStateHolder()
            when (tab) {
                AppTab.HOME -> pageStateHolder.SaveableStateProvider("home") {
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
                        onSearchBarClick = { viewModel.openSearch() },
                        onOpenPlaylistDetail = viewModel::openPlaylistDetail,
                        onOpenRank = viewModel::openRankDetail,
                        onOpenDiscover = { viewModel.switchTab(AppTab.DISCOVER) },
                        onToggleFavorite = viewModel::toggleFavorite,
                    )
                }

                AppTab.DISCOVER -> pageStateHolder.SaveableStateProvider("discover") {
                    DiscoverScreen(
                        hotPlaylists = hotPlaylists,
                        dailyPlaylists = dailyPlaylists,
                        newSongs = newSongs,
                        ranks = ranks,
                        isLoading = isLoadingContent,
                        isHotLoading = isLoadingHot,
                        hotError = hotPlaylistsError,
                        onRetryHot = { viewModel.loadHotPlaylists(force = true) },
                        contentError = contentError,
                        onRetryContent = { viewModel.refreshContent() },
                        onOpenPlaylist = viewModel::openPlaylistDetail,
                        onOpenRank = viewModel::openRankDetail,
                        onPlaySong = { playSong(it, newSongs) },
                    )
                }

                AppTab.PLAYLIST -> pageStateHolder.SaveableStateProvider("playlist") {
                    PlaylistScreen(
                        myPlaylists = myPlaylists,
                        collectedPlaylists = hotPlaylists,
                        onOpenSearch = { viewModel.openSearch() },
                        onPlaySong = { song, list -> playSong(song, list) },
                        onOpenPlaylistDetail = viewModel::openPlaylistDetail,
                        onOpenMyPlaylist = { id -> viewModel.openMyPlaylist(id) },
                        onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                        onRenamePlaylist = viewModel::renamePlaylist,
                        onDeletePlaylist = viewModel::deletePlaylist,
                        onOpenImport = { viewModel.showImportDialog.value = true },
                    )
                }

                AppTab.PROFILE -> pageStateHolder.SaveableStateProvider("profile") {
                    ProfileScreen(
                        localStats = localStats,
                        historyCount = history.size,
                        favoritesCount = favorites.size,
                        cacheCount = cacheCount,
                        cacheSize = cacheSize,
                        isSyncConfigured = isSyncConfigured,
                        isSyncing = isSyncing,
                        activeScript = activeScriptInfo,
                        onOpenSettings = { viewModel.showSettings.value = true },
                        onOpenRecent = { viewModel.openHistory() },
                        onOpenFavorites = { viewModel.showFavorites.value = true },
                        onOpenCacheList = {
                            refreshCacheInfo()
                            viewModel.showCacheList.value = true
                        },
                        onOpenMyPlaylists = { viewModel.showMyPlaylists.value = true },
                        onSyncClick = { viewModel.performCloudSync() },
                    )
                }
            }

            // ===== 全局搜索页（平台聚合 + 多类型结果） =====
            // 视觉层级：搜索在歌单/专辑/歌手详情之下 —— 从搜索打开详情后返回键回到搜索结果
            if (showSearch) {
                SearchScreen(
                    viewModel = viewModel,
                    currentSong = playerState.currentSong,
                    onClose = { viewModel.closeSearch() },
                    onPlaySong = { song, list -> playSong(song, list) },
                    // 打开详情不再强制关闭搜索：详情盖在搜索上层，返回时搜索结果还在
                    onOpenArtist = viewModel::openArtist,
                    onOpenPlaylist = viewModel::openPlaylistDetail,
                    onOpenAlbum = viewModel::openAlbum,
                )
            }

            // ===== 歌单详情 =====
            val playlistDetail = showPlaylistDetail
            if (playlistDetail != null) {
                PlaylistDetailOverlay(
                    title = playlistDetail.name,
                    songs = playlistSongs,
                    isLoading = isLoadingPlaylist,
                    isLocal = playlistDetail.platform == "local",
                    error = playlistError,
                    onRetry = { viewModel.openPlaylistDetail(playlistDetail) },
                    onBack = { viewModel.showPlaylistDetail.value = null },
                    onPlaySong = { playSong(it, playlistSongs) },
                    onPlayAll = { if (playlistSongs.isNotEmpty()) playSong(playlistSongs.first(), playlistSongs) },
                    onAddCurrent = {
                        if (playerState.currentSong != null) {
                            showAddToPlaylist = true
                        }
                    },
                )
            }

            // ===== 专辑详情页（搜索页专辑 Tab 进入） =====
            val albumDetail = showAlbumDetail
            if (albumDetail != null) {
                PlaylistDetailOverlay(
                    title = albumDetail.name,
                    songs = albumDetailSongs,
                    isLoading = isLoadingAlbum,
                    isLocal = false,
                    error = albumError,
                    onRetry = { viewModel.openAlbum(albumDetail) },
                    onBack = { viewModel.closeAlbum() },
                    onPlaySong = { playSong(it, albumDetailSongs) },
                    onPlayAll = { if (albumDetailSongs.isNotEmpty()) playSong(albumDetailSongs.first(), albumDetailSongs) },
                    onAddCurrent = {
                        if (playerState.currentSong != null) {
                            showAddToPlaylist = true
                        }
                    },
                )
            }

            // ===== 歌手页（视觉最上层的详情覆盖层） =====
            val artistRef = showArtist
            if (artistRef != null) {
                val info = artistDetail?.info
                val artistSongs = artistDetail?.songs.orEmpty()
                ArtistScreen(
                    name = info?.name?.takeIf { it.isNotBlank() } ?: artistRef.name,
                    alias = info?.alias,
                    avatar = info?.avatar,
                    songs = artistSongs,
                    isLoading = isLoadingArtist,
                    errorMessage = artistError,
                    onRetry = { viewModel.openArtist(artistRef) },
                    favorites = favorites,
                    onBack = viewModel::closeArtist,
                    onPlaySong = { playSong(it, artistSongs) },
                    onPlayAll = {
                        if (artistSongs.isNotEmpty()) playSong(artistSongs.first(), artistSongs)
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                )
            }

            // ===== 我的收藏页 =====
            if (showFavorites) {
                FavoritesScreen(
                    songs = favoriteSongs,
                    currentSong = playerState.currentSong,
                    onBack = { viewModel.showFavorites.value = false },
                    onPlaySong = { playSong(it, favoriteSongs) },
                    onPlayAll = { if (favoriteSongs.isNotEmpty()) playSong(favoriteSongs.first(), favoriteSongs) },
                    onRemoveFavorite = viewModel::toggleFavorite,
                )
            }

            // ===== 我的歌单页 =====
            if (showMyPlaylists) {
                MyPlaylistsScreen(
                    playlists = myPlaylists,
                    currentSong = playerState.currentSong,
                    onBack = { viewModel.showMyPlaylists.value = false },
                    onOpenPlaylist = { id ->
                        viewModel.openMyPlaylist(id)
                        viewModel.showMyPlaylists.value = false
                    },
                    onCreate = { name -> viewModel.createPlaylist(name) },
                    onRename = viewModel::renamePlaylist,
                    onDelete = viewModel::deletePlaylist,
                    onAddCurrent = { id, song ->
                        viewModel.addSongsToPlaylist(id, listOf(song))
                        scope.launch { snackbarHostState.showSnackbar("已加入歌单") }
                    },
                )
            }

            // ===== 缓存列表页 =====
            if (showCacheList) {
                CacheListScreen(
                    songs = cachedSongs,
                    onBack = { viewModel.showCacheList.value = false },
                    // 文件删除与索引写入走 IO 线程：缓存多时主线程批量 File.delete() 会卡顿甚至 ANR
                    onRemoveOne = { path ->
                        scope.launch(Dispatchers.IO) {
                            AudioCacheService.removeCacheByPath(context, path)
                            withContext(Dispatchers.Main) { refreshCacheInfo() }
                        }
                    },
                    onClearAll = {
                        scope.launch(Dispatchers.IO) {
                            AudioCacheService.clearCache(context)
                            withContext(Dispatchers.Main) { refreshCacheInfo() }
                        }
                    },
                )
            }

            // ===== 最近播放页（独立页面，不再跳转歌单 Tab） =====
            if (showHistory) {
                HistoryScreen(
                    songs = history,
                    currentSong = playerState.currentSong,
                    onBack = { viewModel.closeHistory() },
                    onPlaySong = { playSong(it, history) },
                    onPlayAll = { if (history.isNotEmpty()) playSong(history.first(), history) },
                    onRemoveOne = viewModel::removeFromHistory,
                    onClearAll = viewModel::clearHistory,
                )
            }

            // ===== 把当前播放加入我的歌单 =====
            if (showAddToPlaylist) {
                val current = playerState.currentSong
                if (current != null) {
                    AlertDialog(
                        onDismissRequest = { showAddToPlaylist = false },
                        title = { Text("加入歌单") },
                        text = {
                            Column {
                                Text(
                                    "把「${current.title}」加入：",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSoft,
                                )
                                Spacer(Modifier.size(10.dp))
                                if (myPlaylists.isEmpty()) {
                                    Text("还没有歌单，先去「个人中心 - 我的歌单」新建一个吧", color = TextSoft)
                                } else {
                                    LazyColumn {
                                        myPlaylists.forEach { p ->
                                            item {
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            viewModel.addSongsToPlaylist(p.id, listOf(current))
                                                            showAddToPlaylist = false
                                                            scope.launch { snackbarHostState.showSnackbar("已加入「${p.name}」") }
                                                        }
                                                        .padding(vertical = 10.dp, horizontal = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    SongCover(p.coverUrl, Modifier.size(36.dp), corner = 0.dp)
                                                    Spacer(Modifier.size(10.dp))
                                                    Text(p.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showAddToPlaylist = false }) { Text("取消") }
                        },
                    )
                }
            }

            // ===== 迷你播放器 + 底部导航 Dock =====
            Column(Modifier.align(Alignment.BottomCenter)) {
                // 进度在 MiniPlayer 内部订阅：播放中每 500ms 的进度跳动只重组 MiniPlayer 自身，
                // 不再触发 AppShell 顶层（含当前 Tab 整页）重组
                AnimatedVisibility(
                    visible = hasActiveSong,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                    MiniPlayer(
                        song = playerState.currentSong,
                        isPlaying = playerState.isPlaying,
                        isLoading = playerState.isLoading,
                        progressFlow = playerManager.progress,
                        onOpenPlayer = {
                            playerManager.startProgress()
                            viewModel.showPlayer.value = true
                        },
                        onToggle = playerManager::toggle,
                        onNext = playerManager::next,
                    )
                }
                NavigationDock(
                    currentTab = tab,
                    currentArtwork = playerState.currentSong?.artwork,
                    isSpinning = playerState.isPlaying,
                    onHome = { viewModel.switchTab(AppTab.HOME) },
                    onDiscover = { viewModel.switchTab(AppTab.DISCOVER) },
                    onPlaylist = { viewModel.switchTab(AppTab.PLAYLIST) },
                    onProfile = { viewModel.switchTab(AppTab.PROFILE) },
                    onOpenPlayer = {
                        playerManager.startProgress()
                        viewModel.showPlayer.value = true
                    },
                )
            }

            // ===== 全屏播放器 Drawer =====
            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                PlayerScreen(
                    state = playerState,
                    progressFlow = playerManager.progress,
                    isFavorite = playerState.currentSong?.let { viewModel.isFavorite(it) } == true,
                    onClose = { viewModel.showPlayer.value = false },
                    onToggle = playerManager::toggle,
                    onNext = playerManager::next,
                    onPrevious = playerManager::previous,
                    onSeek = playerManager::seekTo,
                    onSeekToLyric = playerManager::seekToLyric,
                    onTogglePlayMode = playerManager::togglePlayMode,
                    onToggleFavorite = { playerState.currentSong?.let { viewModel.toggleFavorite(it) } },
                    onPlayQueueItem = playerManager::playQueueItem,
                    onRemoveFromQueue = playerManager::removeFromQueue,
                    onSpeedChange = playerManager::setSpeed,
                    onSleepTimer = playerManager::startSleepTimer,
                    onCancelSleepTimer = playerManager::cancelSleepTimer,
                )
            }

            // ===== 设置页 =====
            if (showSettings) {
                SettingsScreen(
                    themeMode = themeMode,
                    quality = qualityLevel,
                    floatingCapsuleEnabled = capsuleEnabled,
                    activeScript = activeScriptInfo,
                    isEngineReady = isEngineReady,
                    engineError = engineError,
                    onRetryLoadScript = viewModel::retryLoadScript,
                    isSyncConfigured = isSyncConfigured,
                    syncServerUrl = syncServerUrl,
                    syncToken = syncToken,
                    lastSyncedAt = lastSyncedAt,
                    lastSyncStatus = lastSyncStatus,
                    isSyncing = isSyncing,
                    onBack = { viewModel.showSettings.value = false },
                    onThemeModeChange = { viewModel.setThemeMode(it, systemDark) },
                    onQualityChange = viewModel::setQuality,
                    onFloatingCapsuleChange = { enabled ->
                        if (enabled) {
                            if (FloatCapsuleManager.hasPermission(context)) {
                                SettingsStore.setFloatingCapsuleEnabled(true)
                                capsuleEnabled = true
                            } else {
                                SettingsStore.setFloatingCapsuleEnabled(false)
                                capsuleEnabled = false
                                FloatCapsuleManager.openPermissionSettings(context)
                                scope.launch {
                                    snackbarHostState.showSnackbar("请在系统设置中开启「悬浮窗」权限，返回后重新打开开关")
                                }
                            }
                        } else {
                            SettingsStore.setFloatingCapsuleEnabled(false)
                            capsuleEnabled = false
                            FloatCapsuleManager.hide()
                        }
                    },
                    onCheckUpdate = { onResult ->
                        scope.launch {
                            // 三态结果：网络失败必须显式提示，不能再静默当成"已是最新版本"
                            when (val result = UpdateService.checkUpdate()) {
                                is UpdateService.CheckResult.Available -> {
                                    val info = result.info
                                    onResult("发现新版本 ${info.versionName}，开始下载…")
                                    try {
                                        val path = UpdateService.downloadApk(context, info) { received, total ->
                                            // 进度按百分比回写（相同字符串不会触发多余重组）
                                            if (total > 0) onResult("下载中 ${received * 100 / total}%")
                                        }
                                        onResult("下载完成，正在打开安装程序…")
                                        // 无"安装未知应用"权限时已自动引导去设置页，返回 false
                                        if (UpdateService.installApk(context, path)) {
                                            onResult("已打开安装程序")
                                        } else {
                                            onResult("请允许「安装未知应用」后重新下载安装")
                                        }
                                    } catch (e: Exception) {
                                        // 失败必须复位状态文案，否则永远停在"开始下载…"
                                        onResult("更新失败：${e.message ?: "未知错误"}")
                                        snackbarHostState.showSnackbar("更新失败: ${e.message}")
                                    }
                                }
                                is UpdateService.CheckResult.UpToDate -> {
                                    onResult("当前已是最新版本")
                                }
                                is UpdateService.CheckResult.Failed -> {
                                    onResult("检查更新失败: ${result.reason ?: "网络错误"}")
                                }
                            }
                        }
                    },
                    onSaveSyncConfig = viewModel::saveSyncConfig,
                    onTestSyncConnection = viewModel::testSyncConnection,
                    onPerformSync = viewModel::performCloudSync,
                    onImportScriptText = viewModel::importScriptFromText,
                    onImportScriptUrl = viewModel::importScriptFromUrl,
                    onUnloadScript = viewModel::unloadScript,
                )
            }

            // ===== 歌单导入弹窗 =====
            if (showImportDialog) {
                ImportPlaylistDialog(
                    onDismiss = { viewModel.showImportDialog.value = false },
                    onImport = { id ->
                        viewModel.showImportDialog.value = false
                        viewModel.importPlaylist(id)
                    },
                )
            }

            // ===== 系统返回键处理 =====
            // 顺序必须与视觉堆栈一致（后组合的 BackHandler 优先）：
            // 底层页 < 搜索 < 歌单详情 < 专辑详情 < 歌手页 < 登录 < 播放器 < 设置。
            // 此前歌手页 BackHandler 排在搜索之前：从搜索进歌手页按返回会先关搜索，
            // 歌手页还挂在屏上，返回行为与视觉层级相反
            BackHandler(enabled = showFavorites) {
                viewModel.showFavorites.value = false
            }
            BackHandler(enabled = showMyPlaylists) {
                viewModel.showMyPlaylists.value = false
            }
            BackHandler(enabled = showCacheList) {
                viewModel.showCacheList.value = false
            }
            BackHandler(enabled = showHistory) {
                viewModel.closeHistory()
            }
            BackHandler(enabled = showSearch) {
                viewModel.closeSearch()
            }
            BackHandler(enabled = showPlaylistDetail != null) {
                viewModel.showPlaylistDetail.value = null
            }
            BackHandler(enabled = showAlbumDetail != null) {
                viewModel.closeAlbum()
            }
            BackHandler(enabled = showArtist != null) {
                viewModel.closeArtist()
            }
            BackHandler(enabled = showPlayer) {
                viewModel.showPlayer.value = false
            }
            BackHandler(enabled = showSettings) {
                viewModel.showSettings.value = false
            }

            // ===== 品牌开屏：覆盖所有层级之上（最后组合 = 最上层）=====
            if (!splashFinished) {
                SplashScreen(onFinished = { splashFinished = true })
            }
        }
    }
}
}

/** 歌单导入弹窗 */
@Composable
private fun ImportPlaylistDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入歌单") },
        text = {
            Column {
                Text(
                    "粘贴网易云歌单链接或直接输入歌单 ID\n如: https://music.163.com/#/playlist?id=5202687076",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
                Spacer(Modifier.size(10.dp))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Stone100, RoundedCornerShape(0.dp))
                        .padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text("歌单链接 / ID", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                        }
                        inner()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val id = Regex("""\d{5,}""").find(input.trim())?.value ?: input.trim()
                if (id.isNotEmpty()) onImport(id)
            }) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 歌单详情全屏覆盖层 */
@Composable
private fun PlaylistDetailOverlay(
    title: String,
    songs: List<Song>,
    isLoading: Boolean,
    isLocal: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onAddCurrent: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp)
                .padding(bottom = LocalBottomBarPadding.current),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "共 ${songs.size} 首曲目",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft
                    )
                }
                if (isLocal) {
                    Row(
                        modifier = Modifier
                            .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface)
                            .clickable(onClick = onAddCurrent)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = PrimaryBrown,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text("加入当前", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = PrimaryBrown)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (songs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = PrimaryBrown.copy(alpha = 0.3f))
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryBrown)
                            .clickable(onClick = onPlayAll)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "播放全部",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading && songs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("探索曲目中…", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                }
            } else if (!error.isNullOrEmpty() && songs.isEmpty()) {
                // 加载失败态：此前失败 = 空列表白页，无文案无重试
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorBox(message = error, onRetry = onRetry)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // index 参与 key：同平台同曲重复入列时避免 key 碰撞崩溃，数据变化时只重组受影响行
                    itemsIndexed(
                        songs,
                        key = { i, s -> "$i:${s.platform}:${s.id}" },
                    ) { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Stone200.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .clickable { onPlaySong(song) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (index < 3) PrimaryBrown else TextSoft,
                                fontWeight = if (index < 3) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(22.dp),
                            )
                            SongCover(song.artwork, Modifier.size(44.dp), corner = 8.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = song.subtitleText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


