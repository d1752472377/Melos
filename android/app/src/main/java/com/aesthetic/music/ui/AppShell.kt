package com.aesthetic.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.aesthetic.music.ui.components.MiniPlayer
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.artist.ArtistScreen
import com.aesthetic.music.ui.discover.DiscoverScreen
import com.aesthetic.music.ui.favorites.FavoritesScreen
import com.aesthetic.music.ui.home.HomeScreen
import com.aesthetic.music.ui.player.PlayerScreen
import com.aesthetic.music.ui.playlist.PlaylistScreen
import com.aesthetic.music.ui.profile.LoginSheet
import com.aesthetic.music.ui.profile.MyPlaylistsScreen
import com.aesthetic.music.ui.profile.ProfileScreen
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
import kotlinx.coroutines.launch

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
    val showLogin by viewModel.showLogin.collectAsState()
    val showMyPlaylists by viewModel.showMyPlaylists.collectAsState()
    val showArtist by viewModel.showArtist.collectAsState()

    val playerState by playerManager.state.collectAsState()
    val history by viewModel.history.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val matchedArtists by viewModel.searchArtists.collectAsState()
    val artistDetail by viewModel.artistDetail.collectAsState()
    val isLoadingArtist by viewModel.isLoadingArtist.collectAsState()
    val hotPlaylists by viewModel.hotPlaylists.collectAsState()
    val isLoadingHot by viewModel.isLoadingHot.collectAsState()
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

    // 用户体系
    val user by viewModel.user.collectAsState()
    val authBusy by viewModel.authBusy.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val localStats by viewModel.localStats.collectAsState()
    val cloudStats by viewModel.cloudStats.collectAsState()

    var cacheSize by remember { mutableStateOf(AudioCacheService.getCacheSize(context)) }
    var cacheCount by remember { mutableStateOf(AudioCacheService.getCacheCount(context)) }
    var cachedSongs by remember { mutableStateOf(AudioCacheService.listCached(context)) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

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

    // 收藏歌曲明细（持久化数据，直接来自 FavoriteStore）
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()

    // ===== 全局错误提示（参考 App 的 lastError 消费机制，防重复弹） =====
    val lastError = playerState.lastError
    LaunchedEffect(lastError) {
        if (lastError != null) {
            snackbarHostState.showSnackbar(lastError)
            playerManager.consumeError()
        }
    }

    fun playSong(song: Song, list: List<Song>) {
        val idx = list.indexOfFirst { it.id == song.id && it.platform == song.platform }.coerceAtLeast(0)
        playerManager.playQueue(list, idx)
        viewModel.addToHistory(song)
        playerManager.startProgress()
    }

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
            when (tab) {
                AppTab.HOME -> HomeScreen(
                    dailyPlaylists = dailyPlaylists,
                    newSongs = newSongs,
                    hotSongs = hotSongs,
                    hotPlaylists = hotPlaylists,
                    ranks = ranks,
                    homeCategories = homeCategories,
                    favorites = favorites,
                    onPlaySong = { song, list -> playSong(song, list) },
                    onSearchBarClick = { viewModel.switchTab(AppTab.PLAYLIST) },
                    onOpenPlaylistDetail = viewModel::openPlaylistDetail,
                    onOpenRank = viewModel::openRankDetail,
                    onOpenDiscover = { viewModel.switchTab(AppTab.DISCOVER) },
                    onToggleFavorite = viewModel::toggleFavorite,
                )

                AppTab.DISCOVER -> DiscoverScreen(
                    hotPlaylists = hotPlaylists,
                    dailyPlaylists = dailyPlaylists,
                    newSongs = newSongs,
                    ranks = ranks,
                    isLoading = isLoadingContent,
                    isHotLoading = isLoadingHot,
                    onOpenPlaylist = viewModel::openPlaylistDetail,
                    onOpenRank = viewModel::openRankDetail,
                    onPlaySong = { playSong(it, newSongs) },
                )

                AppTab.PLAYLIST -> PlaylistScreen(
                    myPlaylists = myPlaylists,
                    collectedPlaylists = hotPlaylists,
                    favoriteSongs = favoriteSongs,
                    history = history,
                    searchResults = searchResults,
                    searchQuery = searchQuery,
                    isSearching = isSearching,
                    favorites = favorites,
                    matchedArtists = matchedArtists,
                    onSearch = viewModel::search,
                    onPlaySong = { song, list -> playSong(song, list) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onOpenPlaylistDetail = viewModel::openPlaylistDetail,
                    onOpenMyPlaylist = { id -> viewModel.openMyPlaylist(id) },
                    onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                    onRenamePlaylist = viewModel::renamePlaylist,
                    onDeletePlaylist = viewModel::deletePlaylist,
                    onOpenImport = { viewModel.showImportDialog.value = true },
                    onOpenFavorites = { viewModel.showFavorites.value = true },
                    onOpenArtist = viewModel::openArtist,
                )

                AppTab.PROFILE -> ProfileScreen(
                    user = user,
                    localStats = localStats,
                    cloudStats = cloudStats,
                    historyCount = history.size,
                    favoritesCount = favorites.size,
                    cacheCount = cacheCount,
                    cacheSize = cacheSize,
                    onOpenSettings = { viewModel.showSettings.value = true },
                    onOpenRecent = { viewModel.switchTab(AppTab.PLAYLIST) },
                    onOpenFavorites = { viewModel.showFavorites.value = true },
                    onOpenCacheList = {
                        cachedSongs = AudioCacheService.listCached(context)
                        cacheSize = AudioCacheService.getCacheSize(context)
                        cacheCount = AudioCacheService.getCacheCount(context)
                        viewModel.showCacheList.value = true
                    },
                    onOpenMyPlaylists = { viewModel.showMyPlaylists.value = true },
                    onLogin = { viewModel.showLoginSheet() },
                    onLogout = {
                        viewModel.logout()
                        scope.launch { snackbarHostState.showSnackbar("已退出登录") }
                    },
                    onUpdateNickname = viewModel::updateNickname,
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

            // ===== 歌手页 =====
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
                    onRemoveOne = { path ->
                        AudioCacheService.removeCacheByPath(path)
                        cachedSongs = AudioCacheService.listCached(context)
                        cacheSize = AudioCacheService.getCacheSize(context)
                        cacheCount = AudioCacheService.getCacheCount(context)
                    },
                    onClearAll = {
                        AudioCacheService.clearCache(context)
                        cachedSongs = emptyList()
                        cacheSize = 0L
                        cacheCount = 0
                    },
                )
            }

            // ===== 登录 / 注册弹窗 =====
            if (showLogin) {
                LoginSheet(
                    busy = authBusy,
                    error = authError,
                    onDismiss = { viewModel.dismissLogin() },
                    onLogin = viewModel::login,
                    onRegister = viewModel::register,
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

            // ===== 迷你播放器 + 底部导航 Dock（通铺连接，无圆角无空隙） =====
            Column(Modifier.align(Alignment.BottomCenter)) {
                MiniPlayer(
                    song = playerState.currentSong,
                    isPlaying = playerState.isPlaying,
                    positionMs = playerState.positionMs,
                    durationMs = playerState.durationMs,
                    onOpenPlayer = {
                        playerManager.startProgress()
                        viewModel.showPlayer.value = true
                    },
                    onToggle = playerManager::toggle,
                    onNext = playerManager::next,
                )
                NavigationDock(
                    currentTab = tab,
                    currentArtwork = playerState.currentSong?.artwork,
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
                    floatingCapsuleEnabled = SettingsStore.isFloatingCapsuleEnabled(),
                    onBack = { viewModel.showSettings.value = false },
                    onThemeModeChange = { viewModel.setThemeMode(it, systemDark) },
                    onQualityChange = viewModel::setQuality,
                    onFloatingCapsuleChange = { enabled ->
                        if (enabled) {
                            if (FloatCapsuleManager.hasPermission(context)) {
                                SettingsStore.setFloatingCapsuleEnabled(true)
                            } else {
                                SettingsStore.setFloatingCapsuleEnabled(false)
                                FloatCapsuleManager.openPermissionSettings(context)
                                scope.launch {
                                    snackbarHostState.showSnackbar("请在系统设置中开启「悬浮窗」权限，返回后重新打开开关")
                                }
                            }
                        } else {
                            SettingsStore.setFloatingCapsuleEnabled(false)
                            FloatCapsuleManager.hide()
                        }
                    },
                    onCheckUpdate = { onResult ->
                        scope.launch {
                            val info = UpdateService.checkUpdate()
                            onResult(
                                if (info != null) "发现新版本 ${info.versionName}，开始下载…"
                                else "已是最新版本"
                            )
                            if (info != null) {
                                try {
                                    val path = UpdateService.downloadApk(context, info) { _, _ -> }
                                    UpdateService.installApk(context, path)
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("更新失败: ${e.message}")
                                }
                            }
                        }
                    },
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
            BackHandler(enabled = showPlaylistDetail != null) {
                viewModel.showPlaylistDetail.value = null
            }
            BackHandler(enabled = showFavorites) {
                viewModel.showFavorites.value = false
            }
            BackHandler(enabled = showMyPlaylists) {
                viewModel.showMyPlaylists.value = false
            }
            BackHandler(enabled = showCacheList) {
                viewModel.showCacheList.value = false
            }
            BackHandler(enabled = showLogin) {
                viewModel.dismissLogin()
            }
            BackHandler(enabled = showPlayer) {
                viewModel.showPlayer.value = false
            }
            BackHandler(enabled = showSettings) {
                viewModel.showSettings.value = false
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
                .padding(bottom = 150.dp),
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
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(songs.size) { index ->
                        val song = songs[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Stone200.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .shadow(0.5.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black.copy(alpha = 0.02f))
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


