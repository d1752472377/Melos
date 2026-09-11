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



    // 历史记录（最近播放，持久化：重启不丢失）。
    // 初始为空、由 init 异步回填：避免冷启动在主线程解析可能达 MB 级的 JSON。
    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history: StateFlow<List<Song>> = _history.asStateFlow()

    // 收藏（持久化：重启不丢失；登录后同步云端），同样异步回填并带上限
    private val _favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs.asStateFlow()

    // 收藏 key 集合（供列表红心状态使用，与 favoriteSongs 同步维护）
    private val _favoriteKeys = MutableStateFlow(emptySet<String>())
    val favorites: StateFlow<Set<String>> = _favoriteKeys.asStateFlow()

    // 收藏的歌单（来自推荐/网络精选歌单，持久化：重启不丢失）
    private val _favoritePlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val favoritePlaylists: StateFlow<List<PlaylistItem>> = _favoritePlaylists.asStateFlow()
    private val _favoritePlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val favoritePlaylistIds: StateFlow<Set<String>> = _favoritePlaylistIds.asStateFlow()

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
        onToggleFavorite = { toggleFavorite(it) },
        // 注意：与 isFavorite() 不同——这里额外接受裸 song.id，
        // 用于兼容历史数据（旧版本只存了 id 未存 platform:id）。
        // 抽取时必须保持与原实现完全一致，否则老用户的收藏在 FM 里会被判为未收藏。
        isFavorite = { song ->
            val key = song.platform + ":" + song.id
            key in _favoriteKeys.value || song.id in _favoriteKeys.value
        },
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

    /**
     * 云同步串行互斥锁 + 待发标记：
     * 收藏/历史是后端"整表替换"语义，多个并发 PUT 乱序到达会让云端被旧快照覆盖。
     * 所有推送排进同一把锁按序执行；待发标记保证激增的推送合并为"只发最新一次"。
     */
    private val syncMutex = Mutex()
    private var favPushPending = false
    private var histPushPending = false

    /**
     * 本地已删、但云端删除失败/尚未完成的歌单 cloudId 集合。
     * 修复"离线删歌单 → 下次登录 syncCloudDown 从云端拉回"的已删歌单复活：
     * 删除时先登记，syncCloudDown 遇到该 cloudId 会重放删除而不是拉回；
     * 重放成功后从集合移除。
     */
    private val pendingDeletedCloudIds = mutableSetOf<String>()

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

    // 我的歌单（本地持久化，登录后同步云端）
    private val _myPlaylists = MutableStateFlow<List<MyPlaylist>>(emptyList())
    val myPlaylists: StateFlow<List<MyPlaylist>> = _myPlaylists.asStateFlow()

    // 统计（本地真实累计）
    private val _localStats = MutableStateFlow(StatsStore.load())
    val localStats: StateFlow<LocalStats> = _localStats.asStateFlow()

    private fun mergeSongs(local: List<Song>, other: List<Song>): List<Song> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Song>()
        for (s in (local + other)) {
            val key = s.platform + ":" + s.id
            if (seen.add(key)) out.add(s)
        }
        return out
    }

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
        // 冷启动异步加载持久化数据（历史/收藏/歌单），完成后与窗口期内已发生的变更合并，
        // 既消除主线程 JSON 解析，又保证用户极早期的操作不被回填覆盖。
        viewModelScope.launch(Dispatchers.IO) {
            val h = HistoryStore.load()
            val f = FavoriteStore.load().take(MAX_FAVORITES)
            val favP = FavoritePlaylistStore.load()
            val p = PlaylistStore.load()
            withContext(Dispatchers.Main) {
                _history.value = mergeSongs(_history.value, h).take(HISTORY_LIMIT)
                val fav = mergeSongs(_favoriteSongs.value, f).take(MAX_FAVORITES)
                _favoriteSongs.value = fav
                _favoriteKeys.value = fav.map { it.platform + ":" + it.id }.toSet()
                _favoritePlaylists.value = favP
                _favoritePlaylistIds.value = favP.map { it.id }.toSet()
                val cur = _myPlaylists.value
                _myPlaylists.value = cur + p.filter { lp ->
                    cur.none { it.id == lp.id || (lp.cloudId.isNotBlank() && it.cloudId == lp.cloudId) }
                }
            }
        }
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

    fun addToHistory(song: Song) {
        val list = _history.value.toMutableList().apply {
            removeAll { it.id == song.id && it.platform == song.platform }
            add(0, song)
        }
        _history.value = list.take(HISTORY_LIMIT)
        HistoryStore.save(_history.value)
        scheduleSyncPush()
    }

    /** 最近播放页：移除单条记录（本地 + 增量推送到私有云） */
    fun removeFromHistory(song: Song) {
        val next = _history.value.filterNot { it.id == song.id && it.platform == song.platform }
        _history.value = next
        HistoryStore.save(next)
        scheduleSyncPush()
    }

    /** 最近播放页：清空全部记录 */
    fun clearHistory() {
        _history.value = emptyList()
        HistoryStore.save(emptyList())
        scheduleSyncPush()
    }

    fun openHistory() { _showHistory.value = true }
    fun closeHistory() { _showHistory.value = false }

    // ==================== 收藏 ====================

    fun toggleFavorite(song: Song) {
        val list = _favoriteSongs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == song.id && it.platform == song.platform }
        if (idx >= 0) list.removeAt(idx) else list.add(0, song)
        val capped = list.take(MAX_FAVORITES)
        _favoriteSongs.value = capped
        _favoriteKeys.value = capped.map { it.platform + ":" + it.id }.toSet()
        FavoriteStore.save(capped)
        scheduleSyncPush()
    }

    fun isFavorite(song: Song): Boolean =
        (song.platform + ":" + song.id) in _favoriteKeys.value

    fun toggleFavoritePlaylist(playlist: PlaylistItem) {
        val list = _favoritePlaylists.value.toMutableList()
        val idx = list.indexOfFirst { it.id == playlist.id }
        val isNowFav: Boolean
        if (idx >= 0) {
            list.removeAt(idx)
            isNowFav = false
        } else {
            list.add(0, playlist)
            isNowFav = true
        }
        _favoritePlaylists.value = list
        _favoritePlaylistIds.value = list.map { it.id }.toSet()
        FavoritePlaylistStore.save(list)
        showToast(if (isNowFav) "已收藏歌单" else "已取消收藏歌单")
    }

    fun isPlaylistFavorite(id: String): Boolean =
        id in _favoritePlaylistIds.value

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

    // ==================== 我的歌单 (本地优先自治) ====================

    fun createPlaylist(name: String, initialSongs: List<Song> = emptyList()) {
        val n = name.trim()
        if (n.isEmpty()) return
        val p = MyPlaylist(id = PlaylistStore.newId(), name = n, songs = initialSongs)
        _myPlaylists.value = listOf(p) + _myPlaylists.value
        saveMyPlaylists()
        _showCreatePlaylistDialog.value = false
        scheduleSyncPush()
    }

    fun renamePlaylist(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        _myPlaylists.value = _myPlaylists.value.map {
            if (it.id == id) it.copy(name = n) else it
        }
        saveMyPlaylists()
        scheduleSyncPush()
    }

    fun deletePlaylist(id: String) {
        _myPlaylists.value = _myPlaylists.value.filterNot { it.id == id }
        saveMyPlaylists()
        scheduleSyncPush()
    }

    fun addSongsToPlaylist(id: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        val target = _myPlaylists.value.firstOrNull { it.id == id } ?: return
        val merged = (target.songs + songs).distinctBy { it.platform + ":" + it.id }.take(MAX_PLAYLIST_SONGS)
        _myPlaylists.value = _myPlaylists.value.map {
            if (it.id == id) it.copy(songs = merged) else it
        }
        saveMyPlaylists()
        scheduleSyncPush()
    }

    fun openMyPlaylist(id: String) {
        val p = _myPlaylists.value.firstOrNull { it.id == id } ?: return
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

    private fun saveMyPlaylists() {
        PlaylistStore.save(_myPlaylists.value)
    }

    private fun scheduleSyncPush() {
        if (!SyncServerStore.isConfigured()) return
        favPushPending = true
        viewModelScope.launch {
            syncMutex.withLock {
                if (!favPushPending) return@withLock
                favPushPending = false
                try {
                    val payload = buildSyncPushPayload()
                    val res = repository.syncPush(payload)
                    res.getOrNull()?.let { result ->
                        SyncServerStore.setLastSyncVersion(result.version)
                        val now = System.currentTimeMillis()
                        SyncServerStore.updateSyncStatus("同步成功", now)
                        _lastSyncedAt.value = now
                        _lastSyncStatus.value = "同步成功"
                    }
                } catch (_: Exception) {
                    // 后台静默重试，不打扰用户
                }
            }
        }
    }

    private fun buildSyncPushPayload(includeEvents: Boolean = false): SyncPushPayload {
        val favUpsert = _favoriteSongs.value
        val hisUpsert = _history.value.map {
            SyncHistoryUpsert(
                id = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                artwork = it.artwork,
                platform = it.platform,
            )
        }
        val plUpsert = _myPlaylists.value.map {
            SyncPlaylistUpsert(id = it.id, name = it.name, coverUrl = it.coverUrl)
        }
        val plSongUpsert = _myPlaylists.value.flatMap { p ->
            p.songs.mapIndexed { idx, s ->
                SyncPlaylistSongUpsert(playlistId = p.id, position = idx, song = s)
            }
        }
        val events = if (includeEvents) StatsTracker.takePendingEvents() else emptyList()

        return SyncPushPayload(
            favorites = SyncCollection(upsert = favUpsert),
            history = SyncCollection(upsert = hisUpsert),
            playlists = SyncCollection(upsert = plUpsert),
            playlistSongs = SyncCollection(upsert = plSongUpsert),
            playEvents = events.ifEmpty { null },
        )
    }

    // ==================== 音源脚本与私有云同步 ====================

    // 音源脚本：逻辑与状态已抽到 ScriptController（见 ui/script/ScriptController.kt）。
    // 提示文案经 _appToast 通道走全局 Snackbar，因此这里注入回调而非让控制器依赖 ViewModel。
    private val script = ScriptController(scope = viewModelScope, onToast = { _appToast.value = it })

    val activeScriptInfo get() = script.activeScriptInfo
    val isEngineReady get() = script.isEngineReady
    val engineError get() = script.engineError
    val scriptCatalog get() = script.catalog

    private val _syncServerUrl = MutableStateFlow(SyncServerStore.getServerUrl())
    val syncServerUrl = _syncServerUrl.asStateFlow()
    private val _syncToken = MutableStateFlow(SyncServerStore.getSyncToken())
    val syncToken = _syncToken.asStateFlow()
    private val _isSyncConfigured = MutableStateFlow(SyncServerStore.isConfigured())
    val isSyncConfigured = _isSyncConfigured.asStateFlow()
    private val _lastSyncedAt = MutableStateFlow(SyncServerStore.getLastSyncedAt())
    val lastSyncedAt = _lastSyncedAt.asStateFlow()
    private val _lastSyncStatus = MutableStateFlow(SyncServerStore.getLastSyncStatus())
    val lastSyncStatus = _lastSyncStatus.asStateFlow()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()
    private val _isTestingSync = MutableStateFlow(false)
    val isTestingSync = _isTestingSync.asStateFlow()

    fun saveSyncConfig(serverUrl: String, token: String) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanToken = token.trim()
        SyncServerStore.save(cleanUrl, cleanToken)
        _syncServerUrl.value = cleanUrl
        _syncToken.value = cleanToken
        _isSyncConfigured.value = SyncServerStore.isConfigured()
        _appToast.value = "私有云同步配置已保存"
    }

    fun testSyncConnection(onResult: ((Boolean, String) -> Unit)? = null) {
        if (!SyncServerStore.isConfigured()) {
            val msg = "请先填写完整的服务器地址和同步密钥"
            _appToast.value = msg
            onResult?.invoke(false, msg)
            return
        }
        viewModelScope.launch {
            _isTestingSync.value = true
            repository.testConnection().onSuccess {
                val msg = "连接正常！服务器同步功能就绪"
                _appToast.value = msg
                onResult?.invoke(true, msg)
            }.onFailure { e ->
                val msg = "连接失败：${e.message ?: "网络超时"}"
                _appToast.value = msg
                onResult?.invoke(false, msg)
            }
            _isTestingSync.value = false
        }
    }

    /**
     * 一键私有云增量同步 (多端 LWW + 墓碑模型)：
     * 1. 携带上次版本号 since 与事件游标 eventSince 从服务端增量拉取变动
     * 2. 本地应用删除墓碑与更新项（收藏、历史、歌单、歌单歌曲）
     * 3. 将本地最新数据快照与未上报听歌事件打包推送到云端
     * 4. 更新本地版本游标与同步状态
     */
    fun performCloudSync() {
        if (!SyncServerStore.isConfigured()) {
            _appToast.value = "未配置私有同步服务器，请前往「设置」配置"
            return
        }
        if (isSyncing.value) return

        viewModelScope.launch {
            _isSyncing.value = true
            syncMutex.withLock {
                var takenEvents: List<PlayEventDto> = emptyList()
                try {
                    val since = SyncServerStore.getLastSyncVersion()
                    val eventSince = SyncServerStore.getLastEventSince()
                    val pullResult = repository.syncPull(since = since, eventSince = eventSince)
                    if (pullResult.isFailure) {
                        val err = pullResult.exceptionOrNull()?.message ?: "拉取失败"
                        SyncServerStore.updateSyncStatus("同步失败: $err")
                        _lastSyncStatus.value = "同步失败: $err"
                        _appToast.value = "云同步失败：$err"
                        _isSyncing.value = false
                        return@withLock
                    }

                    val cloudData = pullResult.getOrNull()!!

                    // 1. 合并收藏 (LWW + 墓碑)
                    val favList = _favoriteSongs.value.toMutableList()
                    for (item in cloudData.favorites) {
                        if (item.deleted) {
                            favList.removeAll { it.platform == item.platform && it.id == item.id }
                        } else if (item.song != null) {
                            val idx = favList.indexOfFirst { it.platform == item.platform && it.id == item.id }
                            if (idx >= 0) {
                                favList[idx] = item.song
                            } else {
                                favList.add(0, item.song)
                            }
                        }
                    }
                    val cappedFav = favList.take(MAX_FAVORITES)
                    _favoriteSongs.value = cappedFav
                    _favoriteKeys.value = cappedFav.map { it.platform + ":" + it.id }.toSet()
                    FavoriteStore.save(cappedFav)

                    // 2. 合并播放历史 (LWW + 墓碑)
                    val hisList = _history.value.toMutableList()
                    for (item in cloudData.history) {
                        if (item.deleted) {
                            hisList.removeAll { it.platform == item.platform && it.id == item.id }
                        } else if (item.song != null) {
                            val idx = hisList.indexOfFirst { it.platform == item.platform && it.id == item.id }
                            if (idx >= 0) {
                                hisList[idx] = item.song
                            } else {
                                hisList.add(0, item.song)
                            }
                        }
                    }
                    val cappedHis = hisList.take(HISTORY_LIMIT)
                    _history.value = cappedHis
                    HistoryStore.save(cappedHis)

                    // 3. 合并歌单元数据 (LWW + 墓碑)
                    val plList = _myPlaylists.value.toMutableList()
                    for (cp in cloudData.playlists) {
                        if (cp.deleted) {
                            plList.removeAll { it.id == cp.id }
                        } else {
                            val existing = plList.firstOrNull { it.id == cp.id }
                            if (existing == null) {
                                plList.add(MyPlaylist(id = cp.id, name = cp.name, songs = emptyList()))
                            } else if (existing.name != cp.name && cp.name.isNotBlank()) {
                                val idx = plList.indexOfFirst { it.id == cp.id }
                                if (idx >= 0) plList[idx] = existing.copy(name = cp.name)
                            }
                        }
                    }
                    // 合并歌单内歌曲 (LWW + 墓碑)
                    for (cps in cloudData.playlistSongs) {
                        val pIdx = plList.indexOfFirst { it.id == cps.playlistId }
                        if (pIdx >= 0) {
                            val p = plList[pIdx]
                            val sList = p.songs.toMutableList()
                            if (cps.deleted) {
                                sList.removeAll { it.platform == cps.platform && it.id == cps.songId }
                            } else if (cps.song != null) {
                                val sIdx = sList.indexOfFirst { it.platform == cps.platform && it.id == cps.songId }
                                if (sIdx >= 0) {
                                    sList[sIdx] = cps.song
                                } else {
                                    val pos = cps.position.coerceIn(0, sList.size)
                                    sList.add(pos, cps.song)
                                }
                            }
                            plList[pIdx] = p.copy(songs = sList.take(MAX_PLAYLIST_SONGS))
                        }
                    }
                    _myPlaylists.value = plList
                    saveMyPlaylists()

                    // 4. 将合并后的最新数据回推服务端并附带听歌统计事件
                    takenEvents = StatsTracker.takePendingEvents()
                    val payload = buildSyncPushPayload(includeEvents = true)
                    val pushResult = repository.syncPush(payload)
                    if (pushResult.isFailure) {
                        StatsTracker.reenqueueEvents(takenEvents)
                        throw (pushResult.exceptionOrNull() ?: Exception("推送失败"))
                    }

                    val finalVer = pushResult.getOrNull()?.version ?: cloudData.version
                    SyncServerStore.setLastSyncVersion(finalVer)
                    SyncServerStore.setLastEventSince(cloudData.nextEventId)

                    val now = System.currentTimeMillis()
                    SyncServerStore.updateSyncStatus("同步成功", now)
                    _lastSyncedAt.value = now
                    _lastSyncStatus.value = "同步成功"
                    _appToast.value = "云端双向同步成功！数据已更新"
                } catch (e: Exception) {
                    if (takenEvents.isNotEmpty()) {
                        StatsTracker.reenqueueEvents(takenEvents)
                    }
                    val err = e.message ?: "未知错误"
                    SyncServerStore.updateSyncStatus("同步失败: $err")
                    _lastSyncStatus.value = "同步失败: $err"
                    _appToast.value = "同步异常：$err"
                } finally {
                    _isSyncing.value = false
                }
            }
        }
    }

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
            SyncServerStore.clear()
            _user.value = null
            _syncServerUrl.value = ""
            _syncToken.value = ""
            _isSyncConfigured.value = false
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
