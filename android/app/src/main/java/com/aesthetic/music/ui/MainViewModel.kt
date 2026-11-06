package com.aesthetic.music.ui

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesthetic.music.MelosApplication
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.ListeningStats
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.UserNotice
import com.aesthetic.music.data.settings.UserStore
import com.aesthetic.music.player.DownloadPolicy
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.service.PlaybackService
import com.aesthetic.music.service.UpdateInfo
import com.aesthetic.music.service.UpdateService
import com.aesthetic.music.ui.account.AuthController
import com.aesthetic.music.ui.artist.ArtistController
import com.aesthetic.music.ui.fm.FmController
import com.aesthetic.music.ui.guess.GuessController
import com.aesthetic.music.ui.home.ContentController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.library.PlaylistImportController
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.rank.RankController
import com.aesthetic.music.ui.search.SearchController
import com.aesthetic.music.ui.settings.SettingsController
import com.aesthetic.music.ui.sheet.SheetController
import com.aesthetic.music.ui.source.SourceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab { HOME, SHEET, PLAYLIST, PROFILE }

class MainViewModel : ViewModel() {

    private val repository = MusicRepository()

    private val _appToast = MutableStateFlow<String?>(null)
    val appToast: StateFlow<String?> = _appToast.asStateFlow()

    val library = LibraryController(
        repository = repository,
        scope = viewModelScope,
        onToast = { _appToast.value = it },
        onStatsChanged = { refreshStats() },
    )

    val playlistImport = PlaylistImportController(
        repository = repository,
        scope = viewModelScope,
        onToast = { _appToast.value = it },
        onCollectPlaylist = { library.collectPlaylist(it) },
    )

    val content = ContentController(repository, viewModelScope)

    val guess = GuessController(
        repository = repository,
        scope = viewModelScope,
        favoritesProvider = { library.favoriteSongs.value },
        recentsProvider = { library.history.value },
    )

    val settings = SettingsController(
        onApplied = { prefs ->
            runCatching {
                PlayerManager.get(MelosApplication.instance).applyUserPrefs(prefs)
                PlaybackService.refreshCard(MelosApplication.instance)
            }.onFailure {
                com.aesthetic.music.service.LogReporter.w("Settings", "应用播放设置失败", it)
            }
        },
    )

    val sheet = SheetController(repository, viewModelScope)
    val artistPage = ArtistController(repository, viewModelScope)
    val search = SearchController(repository, viewModelScope)
    val playlistDetail = PlaylistDetailController(repository, viewModelScope)
    val rank = RankController(repository, viewModelScope)

    val auth: AuthController = AuthController(
        repository = repository,
        scope = viewModelScope,
        onToast = { _appToast.value = it },
        onAuthChanged = {
            library.refreshSyncState()
            if (UserStore.isLoggedIn()) library.performCloudSync()
        },
        onLoggedOut = {
            library.refreshSyncState()
        },
    )

    val fm = FmController(
        repository = repository,
        scope = viewModelScope,
        onToggleFavorite = { library.toggleFavorite(it) },
        isFavorite = { song -> library.isFavoriteForFm(song) },
    )

    val sources = SourceController(
        scope = viewModelScope,
        onToast = { _appToast.value = it },
    )

    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab = _currentTab.asStateFlow()

    private val _showPlayer = MutableStateFlow(false)
    val showPlayer = _showPlayer.asStateFlow()

    private val _showImportDialog = MutableStateFlow(false)
    val showImportDialog = _showImportDialog.asStateFlow()

    // 更新状态必须早于 init 初始化：init 中的启动检查会立即读取。
    private val _appUpdateInfo = MutableStateFlow<UpdateInfo?>(null)
    val appUpdateInfo: StateFlow<UpdateInfo?> = _appUpdateInfo.asStateFlow()
    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate = _isCheckingUpdate.asStateFlow()

    private val _currentPageBackground = MutableStateFlow<Color?>(null)
    val currentPageBackground: StateFlow<Color?> = _currentPageBackground.asStateFlow()
    private val _isDarkPage = MutableStateFlow(false)
    val isDarkPage = _isDarkPage.asStateFlow()

    private val _localStats = MutableStateFlow(StatsStore.load())
    val localStats: StateFlow<LocalStats> = _localStats.asStateFlow()

    private val _listeningStats = MutableStateFlow(StatsStore.snapshot())
    val listeningStats: StateFlow<ListeningStats> = _listeningStats.asStateFlow()

    init {
        com.aesthetic.music.player.MediaCardFavorites.register(
            read = { song -> library.isFavorite(song) },
            toggle = { song ->
                library.toggleFavorite(song)
                library.isFavorite(song)
            },
        )
    }

    init {
        library.hydrate()
        auth.installAuthFailedHook()
        search.hydrate()
        library.refreshSyncState()
        if (library.isSyncConfigured.value) library.performCloudSync()
        UserNotice.register { msg -> _appToast.value = msg }
        triggerAppStartupUpdateCheck()
    }

    fun openPlayer() { _showPlayer.value = true }
    fun closePlayer() { _showPlayer.value = false }
    fun openImportDialog() { _showImportDialog.value = true }
    fun closeImportDialog() { _showImportDialog.value = false }

    fun switchTab(tab: AppTab) {
        _currentTab.value = tab
        playlistDetail.close()
        artistPage.close()
        fm.setDriftActive(tab == AppTab.SHEET)
        if (tab == AppTab.PROFILE) refreshStats()
    }

    fun updatePageBackground(color: Color?, isDark: Boolean) {
        _currentPageBackground.value = color
        _isDarkPage.value = isDark
    }

    fun showToast(msg: String) {
        _appToast.value = msg
    }

    fun consumeToast() {
        _appToast.value = null
    }

    fun refreshStats() {
        _localStats.value = StatsStore.load()
        refreshListeningStats()
    }

    fun refreshListeningStats() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { StatsStore.snapshot() }
            _listeningStats.value = snapshot
        }
    }

    fun downloadSong(context: android.content.Context, song: Song) {
        viewModelScope.launch {
            val quality = SettingsStore.getQuality().value
            val cached = withContext(Dispatchers.IO) {
                com.aesthetic.music.player.AudioCacheService.getCachedPath(context, song.platform, song.id, quality)
            }
            if (cached != null) {
                showToast("「${song.title}」已在本地下载")
                return@launch
            }
            val prefs = SettingsStore.readPrefs()
            if (!DownloadPolicy.allowed(context, prefs, manual = true)) {
                showToast("「${song.title}」未下载：${DownloadPolicy.blockedReason(prefs)}")
                return@launch
            }
            showToast("正在获取「${song.title}」下载链接…")
            val url = repository.getSongUrl(song, quality).getOrNull()?.url
            if (url.isNullOrBlank()) {
                com.aesthetic.music.service.LogReporter.w(
                    "Download",
                    "手动下载取链失败 song=${song.platform}:${song.id} quality=$quality",
                )
                showToast("下载失败：未能获取「${song.title}」直链")
                return@launch
            }
            showToast("开始下载「${song.title}」…")
            com.aesthetic.music.service.LogReporter.breadcrumb(
                "Download",
                "manual ${song.platform}:${song.id} quality=$quality",
            )
            com.aesthetic.music.player.AudioCacheService.cacheAudio(
                context = context,
                platformCode = song.platform,
                songId = song.id,
                url = url,
                name = song.title,
                artist = song.artist,
                quality = quality,
                scope = viewModelScope,
                manual = true,
            ) { path ->
                if (path != null) {
                    showToast("「${song.title}」下载完成")
                    PlayerManager.get(context).saveSidecarsAfterDownload(song)
                } else {
                    showToast("「${song.title}」下载失败")
                }
            }
        }
    }

    fun dismissAppUpdate() {
        _appUpdateInfo.value = null
    }

    fun checkForUpdate(manual: Boolean = false, onResult: ((String) -> Unit)? = null) {
        if (_isCheckingUpdate.value) return
        _isCheckingUpdate.value = true
        viewModelScope.launch {
            val message = try {
                when (val result = UpdateService.checkUpdate()) {
                    is UpdateService.CheckResult.Available -> {
                        _appUpdateInfo.value = result.info
                        "发现新版本 ${result.info.versionName}"
                    }
                    is UpdateService.CheckResult.UpToDate -> "当前已是最新版本"
                    is UpdateService.CheckResult.Failed ->
                        "检查更新失败: ${result.reason ?: "网络错误"}"
                }
            } catch (e: Exception) {
                com.aesthetic.music.service.LogReporter.w("Update", "检查更新异常", e)
                "检查更新失败: ${e.message ?: "网络错误"}"
            }
            _isCheckingUpdate.value = false
            if (manual && _appUpdateInfo.value == null) showToast(message)
            onResult?.invoke(message)
        }
    }

    fun triggerAppStartupUpdateCheck() {
        checkForUpdate()
    }
}
