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

/**
 * App 级组装根与壳层状态。
 *
 * 这里只做三件事：
 * 1. 创建并持有各功能 Controller（生命周期跟随 ViewModel）；
 * 2. 持有跨页面共享的壳层状态（Tab、播放器浮层、页面背景、全局提示、更新状态、统计）；
 * 3. 提供真正跨域的业务编排（切 Tab、退出登录后的清理、主动下载、检查更新等）。
 *
 * 功能状态与动作由调用方直接依赖对应 Controller，不在本类逐成员转发。
 */
class MainViewModel : ViewModel() {

    private val repository = MusicRepository()

    /** 全局一次性提示通道：控制器与底层都通过它弹 Snackbar */
    private val _appToast = MutableStateFlow<String?>(null)
    val appToast: StateFlow<String?> = _appToast.asStateFlow()

    // ==================== 功能 Controller ====================

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
        // 「导入歌单」弹窗的归属是收藏歌单：导入 = 把平台上的歌单收藏进来
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
            }
        },
    )

    val sheet = SheetController(repository, viewModelScope)
    val artistPage = ArtistController(repository, viewModelScope)
    val search = SearchController(repository, viewModelScope)
    val playlistDetail = PlaylistDetailController(repository, viewModelScope)
    val rank = RankController(repository, viewModelScope)

    /**
     * 账号与个人资料控制器：登录成功要刷新可同步状态并触发一次云端同步；
     * 401 失效时清空依赖登录的本地状态。
     */
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

    // ==================== 壳层状态 ====================

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

    // ==================== 统计（App 级，不归属单个功能页） ====================

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
        // 冷启动异步回填历史/收藏/歌单（与窗口期内已发生的变更合并）
        library.hydrate()
        // 登录态失效（401）处理
        auth.installAuthFailedHook()
        // 最近搜索异步回填
        search.hydrate()
        // 已登录 + 已配置服务器时，启动后自动做一次增量同步
        library.refreshSyncState()
        if (library.isSyncConfigured.value) library.performCloudSync()
        // 底层没有 UI 通道的提示统一接到全局 Snackbar
        UserNotice.register { msg -> _appToast.value = msg }
        // 启动时静默检查更新
        triggerAppStartupUpdateCheck()
    }

    // ==================== 壳层动作 ====================

    fun openPlayer() { _showPlayer.value = true }
    fun closePlayer() { _showPlayer.value = false }
    fun openImportDialog() { _showImportDialog.value = true }
    fun closeImportDialog() { _showImportDialog.value = false }

    fun switchTab(tab: AppTab) {
        _currentTab.value = tab
        // 切 Tab 时关闭二级页面，否则会盖住新 Tab 内容
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

    // ==================== 统计 ====================

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

    // ==================== 主动下载（跨域：仓库 + 缓存 + 提示） ====================

    /**
     * 用户主动下载。
     *
     * 与 PlayerManager 的后台预缓存共用 `AudioCacheService.cacheAudio` 与
     * `PlayerManager.saveSidecarsAfterDownload`，但入口保持分开：
     * 手动下载需要逐条提示失败原因，自动预缓存必须静默且可以先只预取直链。
     */
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
                showToast("下载失败：未能获取「${song.title}」直链")
                return@launch
            }
            showToast("开始下载「${song.title}」…")
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

    // ==================== 版本更新（App 级） ====================

    fun dismissAppUpdate() {
        _appUpdateInfo.value = null
    }

    /**
     * 全 App 唯一的更新检查入口。
     *
     * - [manual] = true：个人页/设置页手动检查，无更新或失败时给出提示；
     * - [onResult]：设置页用于更新一行文字状态；不传则只走全局弹窗/提示。
     */
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
