package com.aesthetic.music.ui

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.album.AlbumController
import com.aesthetic.music.ui.artist.ArtistController
import com.aesthetic.music.ui.artist.ArtistRef
import com.aesthetic.music.ui.fm.FmChannel
import com.aesthetic.music.ui.fm.FmController
import com.aesthetic.music.ui.home.ContentController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.script.ScriptController
import com.aesthetic.music.ui.search.SearchController
import com.aesthetic.music.ui.search.SearchType
import com.aesthetic.music.ui.sheet.SheetController
import com.aesthetic.music.data.settings.FavoritePlaylistStore
import com.aesthetic.music.data.settings.FavoriteStore
import com.aesthetic.music.data.settings.HistoryStore
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.data.settings.PlaylistStore
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.UserStore
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.data.api.CloudSyncPullData
import com.aesthetic.music.data.api.SyncCollection
import com.aesthetic.music.data.api.SyncHistoryUpsert
import com.aesthetic.music.data.api.SyncPlaylistSongUpsert
import com.aesthetic.music.data.api.SyncPlaylistUpsert
import com.aesthetic.music.data.api.SyncPushPayload
import com.aesthetic.music.data.api.PlayEventDto
import com.aesthetic.music.service.StatsTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class AppTab { HOME, SHEET, PLAYLIST, PROFILE }

class MainViewModel : ViewModel() {

    private val repository = MusicRepository()

    // 用户音乐库（收藏/历史/我的歌单/云同步）：已抽到 LibraryController（见 ui/library/LibraryController.kt）
    private val library = LibraryController(repository, viewModelScope, onToast = { _appToast.value = it })

    // 首页内容体系：已抽到 ContentController（见 ui/home/ContentController.kt）
    private val content = ContentController(repository, viewModelScope)

    // 歌单广场：已抽到 SheetController（见 ui/sheet/SheetController.kt）
    private val sheet = SheetController(repository, viewModelScope)

    // 歌手页：已抽到 ArtistController（见 ui/artist/ArtistController.kt）
    private val artistPage = ArtistController(repository, viewModelScope)

    // 专辑详情：已抽到 AlbumController（见 ui/album/AlbumController.kt）
    private val albumPage = AlbumController(repository, viewModelScope)

    // 全局搜索：已抽到 SearchController（见 ui/search/SearchController.kt）
    private val search = SearchController(repository, viewModelScope)

    // 页面/弹层状态
    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab = _currentTab.asStateFlow()
    private val _showPlayer = MutableStateFlow(false)
    val showPlayer = _showPlayer.asStateFlow()
    private val _showThemeSheet = MutableStateFlow(false)
    val showThemeSheet = _showThemeSheet.asStateFlow()
    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()
    private val _showPlaylistDetail = MutableStateFlow<PlaylistItem?>(null)
    val showPlaylistDetail = _showPlaylistDetail.asStateFlow()
    private val _showImportDialog = MutableStateFlow(false)
    val showImportDialog = _showImportDialog.asStateFlow()
    private val _showFavorites = MutableStateFlow(false)
    val showFavorites = _showFavorites.asStateFlow()
    private val _showCacheList = MutableStateFlow(false)
    val showCacheList = _showCacheList.asStateFlow()
    /** 最近播放独立页（从"我的"页进入；不再跳转到歌单 Tab） */
    private val _showHistory = MutableStateFlow(false)
    val showHistory = _showHistory.asStateFlow()
    private val _showLogin = MutableStateFlow(false)
    val showLogin = _showLogin.asStateFlow()
    private val _showMyPlaylists = MutableStateFlow(false)
    val showMyPlaylists = _showMyPlaylists.asStateFlow()
    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    val showCreatePlaylistDialog = _showCreatePlaylistDialog.asStateFlow()

    // 弹层/播放器的显式开关。
    // 这些状态对外只读（暴露 StateFlow 而非 MutableStateFlow），外部只能经这些方法变更，
    // 保证"谁在什么时候改了什么"可追溯——此前 UI 可直接对 MutableStateFlow 赋值，
    // 状态变更散落在视图层，排查问题时无法确定来源。
    fun openPlayer() { _showPlayer.value = true }
    fun closePlayer() { _showPlayer.value = false }
    fun openImportDialog() { _showImportDialog.value = true }
    fun closeImportDialog() { _showImportDialog.value = false }
    fun closePlaylistDetail() { _showPlaylistDetail.value = null }



    // 历史 / 收藏 / 收藏歌单：已抽到 LibraryController，保留同名只读委托
    val history get() = library.history
    val favoriteSongs get() = library.favoriteSongs
    val favorites get() = library.favorites
    val favoritePlaylists get() = library.favoritePlaylists
    val favoritePlaylistIds get() = library.favoritePlaylistIds

    // （全局搜索相关状态见下方「==================== 全局搜索 ====================」区块）

    // 内容体系（首页 / 分类页 / 排行榜页共用）：已抽到 ContentController，保留同名只读委托
    val hotPlaylists get() = content.hotPlaylists
    val isLoadingHot get() = content.isLoadingHot
    val hotPlaylistsError get() = content.hotPlaylistsError
    val dailyPlaylists get() = content.dailyPlaylists
    val newSongs get() = content.newSongs
    val ranks get() = content.ranks
    val hotSongs get() = content.hotSongs
    val homeCategories get() = content.homeCategories
    val isLoadingContent get() = content.isLoadingContent
    val contentError get() = content.contentError

    // 歌单广场（覆盖层，从首页分类歌单进入）：已抽到 SheetController，保留同名只读委托
    val sheetTags get() = sheet.tags
    val sheetCat get() = sheet.cat
    val sheetOrder get() = sheet.order
    val sheetPlaylists get() = sheet.playlists
    val isLoadingSheets get() = sheet.isLoading
    val sheetError get() = sheet.error
    private val _showSheet = MutableStateFlow(false)
    val showSheet = _showSheet.asStateFlow()

    // FM 电台：逻辑与状态已抽到 FmController（见 ui/fm/FmController.kt）。
    // 这里保留同名的只读委托，视图层无需改动；后续可让 FM 页直接持有控制器。
    private val fm = FmController(
        repository = repository,
        scope = viewModelScope,
        onToggleFavorite = { library.toggleFavorite(it) },
        // 兼容旧数据（额外接受裸 song.id）的判断由 LibraryController.isFavoriteForFm 提供，
        // 保持与原实现完全一致，否则老用户的收藏在 FM 里会被判为未收藏。
        isFavorite = { song -> library.isFavoriteForFm(song) },
    )

    val fmChannels get() = fm.channels
    val fmStyles get() = fm.styles
    val currentFmChannel get() = fm.currentChannel
    val fmActive get() = fm.active
    val fmStyle get() = fm.style
    val fmQueue get() = fm.queue
    val fmIndex get() = fm.index
    val fmLoading get() = fm.loading
    val fmError get() = fm.error
    val fmTreasure get() = fm.treasure

    // 排行榜页（覆盖层）
    private val _showRanks = MutableStateFlow(false)
    val showRanks = _showRanks.asStateFlow()

    // 歌单详情
    private val _playlistSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlistSongs: StateFlow<List<Song>> = _playlistSongs.asStateFlow()
    private val _isLoadingPlaylist = MutableStateFlow(false)
    val isLoadingPlaylist = _isLoadingPlaylist.asStateFlow()
    /** 歌单/榜单详情加载失败信息：此前失败静默成空白列表页，无文案无重试 */
    private val _playlistError = MutableStateFlow<String?>(null)
    val playlistError = _playlistError.asStateFlow()
    /** 歌单详情请求令牌：快速连开两个歌单时丢弃慢的旧响应，防止串台 */
    @Volatile
    private var playlistRequestId = 0L

    // 云同步的串行互斥锁与待发标记已随收藏/历史/歌单一起抽到 LibraryController。

    // 用户体系
    private val _user = MutableStateFlow(UserStore.user())
    val user: StateFlow<UserInfo?> = _user.asStateFlow()
    private val _authBusy = MutableStateFlow(false)
    val authBusy = _authBusy.asStateFlow()
    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()

    // 个人中心个性化状态
    private val _profileAvatar = MutableStateFlow(UserStore.getAvatarPath())
    val profileAvatar = _profileAvatar.asStateFlow()
    private val _profileBackground = MutableStateFlow(UserStore.getBackgroundPath())
    val profileBackground = _profileBackground.asStateFlow()
    private val _isProfileBgBlur = MutableStateFlow(UserStore.isBackgroundBlur())
    val isProfileBgBlur = _isProfileBgBlur.asStateFlow()
    private val _profileNickname = MutableStateFlow(UserStore.getNickname())
    val profileNickname = _profileNickname.asStateFlow()
    private val _profileBio = MutableStateFlow(UserStore.getBio())
    val profileBio = _profileBio.asStateFlow()
    private val _profileGender = MutableStateFlow(UserStore.getGender())
    val profileGender = _profileGender.asStateFlow()
    private val _profileBirthday = MutableStateFlow(UserStore.getBirthday())
    val profileBirthday = _profileBirthday.asStateFlow()
    private val _profileUserId = MutableStateFlow(UserStore.getUserId())
    val profileUserId = _profileUserId.asStateFlow()

    /** 一次性全局提示（导入歌单失败、改昵称失败、登录态过期等）：
     *  此前这些场景的错误要么写进没人消费的状态（albumError），要么复用只被登录弹窗读的
     *  authError，用户"点了没反应"。AppShell 订阅本流弹 Snackbar 后调用 consumeToast() 清空 */
    private val _appToast = MutableStateFlow<String?>(null)
    val appToast: StateFlow<String?> = _appToast.asStateFlow()

    fun consumeToast() { _appToast.value = null }

    // 我的歌单：已抽到 LibraryController，保留同名只读委托
    val myPlaylists get() = library.myPlaylists

    // 统计（本地真实累计）
    private val _localStats = MutableStateFlow(StatsStore.load())
    val localStats: StateFlow<LocalStats> = _localStats.asStateFlow()

    // 主题模式
    private val _themeMode = MutableStateFlow(SettingsStore.getThemeMode())
    val themeMode = _themeMode.asStateFlow()

    // 音质等级
    private val _quality = MutableStateFlow(SettingsStore.getQuality())
    val quality = _quality.asStateFlow()

    // 歌手页：已抽到 ArtistController，保留同名只读委托
    val showArtist get() = artistPage.showArtist
    val artistDetail get() = artistPage.artistDetail
    val isLoadingArtist get() = artistPage.isLoading
    val artistError get() = artistPage.error

    init {
        // 冷启动异步回填历史/收藏/歌单（与窗口期内已发生的变更合并），委托 LibraryController
        library.hydrate()
        // 登录态失效（401）：清登录态并提示。此前 token 过期/被吊销后界面仍显示"已登录"，
        // 收藏/历史/歌单云同步全部静默失败，用户只看到"同步没生效"
        com.aesthetic.music.data.api.ApiClient.setOnAuthFailed {
            if (UserStore.isLoggedIn()) {
                UserStore.logout()
                _user.value = null
                _appToast.value = "登录已过期，请重新登录"
            }
        }
        // 最近搜索 + 热门搜索词（委托 SearchController 异步回填）
        search.hydrate()
    }

    // ==================== 全局搜索（委托 SearchController） ====================

    private val _showSearch = MutableStateFlow(false)
    val showSearch = _showSearch.asStateFlow()

    // 搜索状态与逻辑已抽到 SearchController（见 ui/search/SearchController.kt），保留同名只读委托
    val searchQuery get() = search.query
    val searchType get() = search.type
    val searchPlatform get() = search.platform
    val songResults get() = search.songResults
    val lyricResults get() = search.lyricResults
    val artistResults get() = search.artistResults
    val playlistResults get() = search.playlistResults
    val albumResults get() = search.albumResults
    val isSearching get() = search.isSearching
    val searchError get() = search.searchError
    val recentSearches get() = search.recentSearches
    val hotSearchWords get() = search.hotSearchWords

    fun openSearch() { _showSearch.value = true }
    fun closeSearch() { _showSearch.value = false }

    /** 专辑详情覆盖层（搜索页专辑 Tab 点击进入）：已抽到 AlbumController，保留同名只读委托 */
    val showAlbumDetail get() = albumPage.showAlbumDetail
    val albumDetailSongs get() = albumPage.albumDetailSongs
    val isLoadingAlbum get() = albumPage.isLoading
    val albumError get() = albumPage.error

    fun openAlbum(album: AlbumItem) = albumPage.open(album)

    fun closeAlbum() = albumPage.close()

    // 搜索操作：委托 SearchController
    fun onSearchQueryChanged(q: String) = search.onQueryChanged(q)

    fun submitSearch(word: String? = null) = search.submit(word)

    fun setSearchType(type: SearchType) = search.setType(type)

    fun setSearchPlatform(platform: String?) = search.setPlatform(platform)

    fun clearRecentSearches() = search.clearRecent()

    // ==================== 歌手页（委托 ArtistController） ====================

    fun openArtist(artist: ArtistItem) = artistPage.open(artist)

    fun openArtist(ref: ArtistRef) = artistPage.open(ref)

    fun closeArtist() = artistPage.close()

    // ==================== 最近播放 / 收藏（委托 LibraryController） ====================

    fun addToHistory(song: Song) = library.addToHistory(song)

    fun removeFromHistory(song: Song) = library.removeFromHistory(song)

    fun clearHistory() = library.clearHistory()

    fun openHistory() { _showHistory.value = true }
    fun closeHistory() { _showHistory.value = false }

    fun toggleFavorite(song: Song) = library.toggleFavorite(song)

    fun isFavorite(song: Song): Boolean = library.isFavorite(song)

    fun toggleFavoritePlaylist(playlist: PlaylistItem) = library.toggleFavoritePlaylist(playlist)

    fun isPlaylistFavorite(id: String): Boolean = library.isPlaylistFavorite(id)

    fun showToast(msg: String) {
        _appToast.value = msg
    }

    /** 异步下载/缓存歌曲并提供全局提示 */
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
            showToast("正在获取「${song.title}」下载链接…")
            val r = repository.getSongUrl(song, quality)
            val url = r.getOrNull()?.url
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
            ) { path ->
                if (path != null) {
                    showToast("「${song.title}」下载完成")
                } else {
                    showToast("「${song.title}」下载失败")
                }
            }
        }
    }

    // ==================== 内容加载（委托 ContentController） ====================

    fun loadHotPlaylists(force: Boolean = false) = content.loadHotPlaylists(force)

    fun loadContent(force: Boolean = false) = content.loadContent(force)

    /** 用户点"重试"时强制重新拉取内容 */
    fun refreshContent() = content.refreshContent()

    // ==================== 歌单广场（委托 SheetController） ====================

    fun setSheetCat(cat: String) = sheet.setCat(cat)

    fun setSheetOrder(order: String) = sheet.setOrder(order)

    fun loadSheetPlaylists(force: Boolean = false) = sheet.load(force)

    fun openSheet() {
        _showSheet.value = true
        sheet.load()
    }

    fun closeSheet() { _showSheet.value = false }

    // ==================== FM 电台（委托 FmController） ====================

    fun fmStart(channel: FmChannel = fm.channels[0], onReady: ((Song, List<Song>) -> Unit)? = null) =
        fm.start(channel, onReady)

    fun fmStart(style: String) = fm.start(style)

    fun fmRefresh(onReady: ((Song, List<Song>) -> Unit)? = null) = fm.refresh(onReady)

    fun fmSelect(index: Int) = fm.select(index)

    fun fmDislike(song: Song, onPlayNext: ((Song, List<Song>) -> Unit)? = null) =
        fm.dislike(song, onPlayNext)

    fun fmToggleLike(song: Song) = fm.toggleLike(song)

    fun fmOnSignal(song: Song) = fm.onSignal(song)

    fun fmStopDrift() = fm.stopDrift()

    // ==================== 排行榜页 ====================

    fun openRanks() { _showRanks.value = true }
    fun closeRanks() { _showRanks.value = false }

    // ==================== 歌单 ====================

    fun openPlaylistDetail(item: PlaylistItem) {
        _showPlaylistDetail.value = item
        _playlistSongs.value = emptyList()
        _playlistError.value = null
        val req = ++playlistRequestId
        viewModelScope.launch {
            _isLoadingPlaylist.value = true
            repository.getPlaylist(item.id).onSuccess { list ->
                if (req == playlistRequestId) _playlistSongs.value = list
            }.onFailure { e ->
                // 失败要可见：此前只写 onSuccess，失败后列表空 + 无 loading，
                // Overlay 渲染 0 项 LazyColumn 白页，无文案无重试
                if (req == playlistRequestId) _playlistError.value = e.message ?: "歌单加载失败"
            }
            if (req == playlistRequestId) _isLoadingPlaylist.value = false
        }
    }

    fun openRankDetail(item: PlaylistItem) {
        _showPlaylistDetail.value = item
        _playlistSongs.value = emptyList()
        _playlistError.value = null
        val req = ++playlistRequestId
        viewModelScope.launch {
            _isLoadingPlaylist.value = true
            repository.getRankSongs(item.id).onSuccess { list ->
                if (req == playlistRequestId) _playlistSongs.value = list
            }.onFailure { e ->
                if (req == playlistRequestId) _playlistError.value = e.message ?: "榜单加载失败"
            }
            if (req == playlistRequestId) _isLoadingPlaylist.value = false
        }
    }

    fun importPlaylist(id: String) {
        val req = ++playlistRequestId
        viewModelScope.launch {
            _isLoadingPlaylist.value = true
            repository.getPlaylist(id).onSuccess { list ->
                if (req != playlistRequestId) return@launch
                _playlistSongs.value = list
                _showPlaylistDetail.value = PlaylistItem(id = id, name = "导入的歌单")
            }.onFailure { e ->
                // 此前失败静默：弹窗关闭后"什么都没发生"
                if (req == playlistRequestId) {
                    _appToast.value = "导入歌单失败：${e.message ?: "网络异常"}"
                }
            }
            if (req == playlistRequestId) _isLoadingPlaylist.value = false
        }
    }

    // ==================== 我的歌单（委托 LibraryController） ====================

    fun createPlaylist(name: String, initialSongs: List<Song> = emptyList()) {
        library.createPlaylist(name, initialSongs)
        _showCreatePlaylistDialog.value = false
    }

    fun renamePlaylist(id: String, name: String) = library.renamePlaylist(id, name)

    fun deletePlaylist(id: String) = library.deletePlaylist(id)

    fun addSongsToPlaylist(id: String, songs: List<Song>) = library.addSongsToPlaylist(id, songs)

    fun openMyPlaylist(id: String) {
        val p = library.myPlaylist(id) ?: return
        ++playlistRequestId
        _playlistError.value = null
        _playlistSongs.value = p.songs
        _showPlaylistDetail.value = PlaylistItem(
            id = p.id,
            name = p.name,
            coverUrl = p.coverUrl,
            trackCount = p.songs.size.toLong(),
            platform = "local",
        )
    }

    fun addCurrentToPlaylist(song: Song) {
        _showCreatePlaylistDialog.value = true
        pendingSongForNewPlaylist = song
    }

    private var pendingSongForNewPlaylist: Song? = null

    fun consumePendingSong(): Song? = pendingSongForNewPlaylist.also { pendingSongForNewPlaylist = null }

    fun createPlaylistWithPendingSong(name: String) {
        val song = pendingSongForNewPlaylist
        createPlaylist(name, listOfNotNull(song))
    }

    // ==================== 音源脚本与私有云同步 ====================

    // 音源脚本：逻辑与状态已抽到 ScriptController（见 ui/script/ScriptController.kt）。
    // 提示文案经 _appToast 通道走全局 Snackbar，因此这里注入回调而非让控制器依赖 ViewModel。
    private val script = ScriptController(scope = viewModelScope, onToast = { _appToast.value = it })

    val activeScriptInfo get() = script.activeScriptInfo
    val isEngineReady get() = script.isEngineReady
    val engineError get() = script.engineError
    val scriptCatalog get() = script.catalog

    // 私有云同步状态：已抽到 LibraryController，保留同名只读委托
    val syncServerUrl get() = library.syncServerUrl
    val syncToken get() = library.syncToken
    val isSyncConfigured get() = library.isSyncConfigured
    val lastSyncedAt get() = library.lastSyncedAt
    val lastSyncStatus get() = library.lastSyncStatus
    val isSyncing get() = library.isSyncing
    val isTestingSync get() = library.isTestingSync

    fun saveSyncConfig(serverUrl: String, token: String) = library.saveSyncConfig(serverUrl, token)

    fun testSyncConnection(onResult: ((Boolean, String) -> Unit)? = null) =
        library.testSyncConnection(onResult)

    fun performCloudSync() = library.performCloudSync()

    // ==================== 音源脚本（委托 ScriptController） ====================

    fun importScriptFromText(content: String, name: String? = null) = script.importFromText(content, name)

    fun importScriptFromUrl(url: String, onFinished: (Boolean, String) -> Unit) =
        script.importFromUrl(url, onFinished)

    fun retryLoadScript() = script.retryLoad()

    fun unloadScript() = script.unload()

    fun switchScript(id: String) = script.switchTo(id)

    // ==================== 用户体系 (Auth) ====================

    fun showLoginSheet() { _showLogin.value = true }
    fun clearAuthError() { _authError.value = null }
    fun dismissLogin() { _showLogin.value = false; _authError.value = null }

    fun login(u: String, p: String) {
        if (u.isBlank() || p.isBlank()) {
            _authError.value = "用户名与密码不能为空"
            return
        }
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            repository.login(u, p).onSuccess { data ->
                UserStore.save(data.token, data.user)
                _user.value = data.user
                _authBusy.value = false
                _showLogin.value = false
                _appToast.value = "登录成功"
                // 登录成功后触发一次增量同步
                performCloudSync()
            }.onFailure { e ->
                _authBusy.value = false
                _authError.value = e.message ?: "登录失败"
            }
        }
    }

    fun register(u: String, p: String, n: String) {
        if (u.isBlank() || p.isBlank()) {
            _authError.value = "用户名与密码不能为空"
            return
        }
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            repository.register(u, p, n).onSuccess { data ->
                UserStore.save(data.token, data.user)
                _user.value = data.user
                _authBusy.value = false
                _showLogin.value = false
                _appToast.value = "注册成功并已登录"
                performCloudSync()
            }.onFailure { e ->
                _authBusy.value = false
                _authError.value = e.message ?: "注册失败"
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                repository.logout()
            } catch (e: Exception) {
                // 服务端登出失败：本地仍继续登出（用户体验优先），但必须留痕——
                // 否则服务端 token 可能仍然有效，属于需要被发现的鉴权残留。
                Log.w(TAG, "服务端登出失败，已继续本地登出", e)
            }
            UserStore.logout()
            library.resetSyncState()
            _user.value = null
            _profileNickname.value = UserStore.getNickname()
            _profileUserId.value = UserStore.getUserId()
            _appToast.value = "已退出登录并清空私有服务器配置"
        }
    }

    fun updateNickname(nickname: String) {
        if (nickname.isBlank()) return
        viewModelScope.launch {
            repository.updateProfile(nickname = nickname).onSuccess { updated ->
                _user.value = updated
                UserStore.updateUser(updated)
                _profileNickname.value = updated.nickname.ifBlank { updated.username }
                _appToast.value = "昵称已更新"
            }.onFailure { e ->
                _appToast.value = "更新失败: ${e.message ?: "网络异常"}"
            }
        }
    }

    // ===== 个人中心个性化管理 =====

    fun setProfileAvatar(path: String?) {
        UserStore.setAvatarPath(path)
        _profileAvatar.value = path
    }

    fun setProfileBackground(path: String?) {
        UserStore.setBackgroundPath(path)
        _profileBackground.value = path
    }

    fun toggleProfileBgBlur() {
        val next = !isProfileBgBlur.value
        UserStore.setBackgroundBlur(next)
        _isProfileBgBlur.value = next
    }

    fun restoreProfileDefaults(context: android.content.Context) {
        UserStore.restoreProfileDefaults(context)
        _profileAvatar.value = null
        _profileBackground.value = null
        _isProfileBgBlur.value = false
        _profileNickname.value = UserStore.getNickname()
        _profileBio.value = ""
        _profileGender.value = "保密"
        _profileBirthday.value = ""
        _profileUserId.value = UserStore.getUserId()
        _appToast.value = "已恢复默认设置"
    }

    fun saveProfileInfo(
        nickname: String,
        bio: String,
        gender: String,
        birthday: String,
        avatarPath: String? = null
    ) {
        if (nickname.isNotBlank()) {
            UserStore.setNickname(nickname)
            _profileNickname.value = nickname
        }
        UserStore.setBio(bio)
        _profileBio.value = bio
        UserStore.setGender(gender)
        _profileGender.value = gender
        UserStore.setBirthday(birthday)
        _profileBirthday.value = birthday
        if (avatarPath != null) {
            UserStore.setAvatarPath(avatarPath)
            _profileAvatar.value = avatarPath
        }
        if (UserStore.isLoggedIn() && nickname.isNotBlank()) {
            updateNickname(nickname)
        } else {
            _appToast.value = "个人信息已保存"
        }
    }

    // ==================== 统计 ====================

    fun refreshStats() {
        _localStats.value = StatsStore.load()
    }

    // ==================== 主题 ====================

    fun setThemeMode(mode: ThemeMode, systemDark: Boolean) {
        _themeMode.value = mode
        SettingsStore.setThemeMode(mode)
        com.aesthetic.music.ui.theme.applyThemeMode(mode, systemDark)
    }

    fun setQuality(level: QualityLevel) {
        _quality.value = level
        SettingsStore.setQuality(level)
    }

    // ==================== 导航 ====================

    fun switchTab(tab: AppTab) {
        _currentTab.value = tab
        // 切 Tab 时关闭二级页面（搜索/歌单详情/歌手页/收藏页/最近播放等覆盖层），否则会盖住新 Tab 内容
        _showPlaylistDetail.value = null
        artistPage.close()
        _showFavorites.value = false
        _showCacheList.value = false
        _showMyPlaylists.value = false
        _showHistory.value = false
        _showSearch.value = false
        albumPage.close()
        _showRanks.value = false
        _showSheet.value = false
        // FM 漂移开关由 FmController 持有（切走 FM 页即暂停漂移）
        fm.setDriftActive(tab == AppTab.SHEET)
        if (tab == AppTab.PROFILE) refreshStats()
    }

    // 在 MainViewModel 类中添加
    // 在 MainViewModel 中添加
    private val _currentPageBackground = MutableStateFlow<Color?>(null)
    val currentPageBackground: StateFlow<Color?> = _currentPageBackground.asStateFlow()
    private val _isDarkPage = MutableStateFlow(false)
    val isDarkPage: StateFlow<Boolean> = _isDarkPage.asStateFlow()

    fun updatePageBackground(color: Color?, isDark: Boolean) {
        _currentPageBackground.value = color
        _isDarkPage.value = isDark
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val HISTORY_LIMIT = 100
        /** 收藏上限：SharedPreferences 存整组 JSON，无上限会随使用无限膨胀 */
        private const val MAX_FAVORITES = 1000
        /** 单个歌单曲目数上限 */
        private const val MAX_PLAYLIST_SONGS = 1000
        /** FM 默认风格：当前无风格选择 UI，进入 FM 页直接用这个开拉 */
        private const val DEFAULT_FM_STYLE = "推荐新歌"
    }
}
