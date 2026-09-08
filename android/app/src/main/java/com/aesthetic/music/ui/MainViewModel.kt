package com.aesthetic.music.ui

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistDetailData
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.FavoritePlaylistStore
import com.aesthetic.music.data.settings.FavoriteStore
import com.aesthetic.music.data.settings.HistoryStore
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.data.settings.PlaylistStore
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.settings.RecentSearchStore
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.UserStore
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.data.settings.ScriptCatalogStore
import com.aesthetic.music.data.settings.ScriptCatalogEntry
import com.aesthetic.music.data.userapi.UserApiEngine
import com.aesthetic.music.data.userapi.UserApiStore
import com.aesthetic.music.data.userapi.ScriptMetadata
import com.aesthetic.music.data.api.CloudSyncPullData
import com.aesthetic.music.data.api.SyncCollection
import com.aesthetic.music.data.api.SyncHistoryUpsert
import com.aesthetic.music.data.api.SyncPlaylistSongUpsert
import com.aesthetic.music.data.api.SyncPlaylistUpsert
import com.aesthetic.music.data.api.SyncPushPayload
import com.aesthetic.music.data.api.PlayEventDto
import com.aesthetic.music.service.StatsTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

    // 页面/弹层状态
    val currentTab = MutableStateFlow(AppTab.HOME)
    val showPlayer = MutableStateFlow(false)
    val showThemeSheet = MutableStateFlow(false)
    val showSettings = MutableStateFlow(false)
    val showPlaylistDetail = MutableStateFlow<PlaylistItem?>(null)
    val showImportDialog = MutableStateFlow(false)
    val showFavorites = MutableStateFlow(false)
    val showCacheList = MutableStateFlow(false)
    /** 最近播放独立页（从"我的"页进入；不再跳转到歌单 Tab） */
    val showHistory = MutableStateFlow(false)
    val showLogin = MutableStateFlow(false)
    val showMyPlaylists = MutableStateFlow(false)
    val showCreatePlaylistDialog = MutableStateFlow(false)

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

    // 内容体系（首页 / 分类页 / 排行榜页共用）：热门歌单、每日、新歌、榜单、分类
    private val _hotPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val hotPlaylists: StateFlow<List<PlaylistItem>> = _hotPlaylists.asStateFlow()
    val isLoadingHot = MutableStateFlow(false)
    /** 热门歌单加载失败信息（此前失败静默，区块直接消失） */
    val hotPlaylistsError = MutableStateFlow<String?>(null)

    private val _dailyPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val dailyPlaylists: StateFlow<List<PlaylistItem>> = _dailyPlaylists.asStateFlow()
    private val _newSongs = MutableStateFlow<List<Song>>(emptyList())
    val newSongs: StateFlow<List<Song>> = _newSongs.asStateFlow()
    private val _ranks = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val ranks: StateFlow<List<PlaylistItem>> = _ranks.asStateFlow()
    private val _hotSongs = MutableStateFlow<List<Song>>(emptyList())
    val hotSongs: StateFlow<List<Song>> = _hotSongs.asStateFlow()
    private val _homeCategories = MutableStateFlow<List<HomeCategoryDto>>(emptyList())
    val homeCategories: StateFlow<List<HomeCategoryDto>> = _homeCategories.asStateFlow()
    val isLoadingContent = MutableStateFlow(false)
    val contentError = MutableStateFlow<String?>(null)
    /** 内容已成功加载过一次：切 Tab 重入不再全量重拉（重试用 refreshContent） */
    @Volatile
    private var contentLoaded = false

    // 歌单广场（覆盖层，从首页分类歌单进入）：标签 + 最新/最热 + 方块歌单
    val sheetTags: List<String> = repository.sheetTags()
    val sheetCat = MutableStateFlow("全部")
    val sheetOrder = MutableStateFlow("hot") // "hot"=最热 / "new"=最新
    private val _sheetPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val sheetPlaylists: StateFlow<List<PlaylistItem>> = _sheetPlaylists.asStateFlow()
    val isLoadingSheets = MutableStateFlow(false)
    val sheetError = MutableStateFlow<String?>(null)
    val showSheet = MutableStateFlow(false)

    // FM 电台（免登录会话级推荐）：SHEET Tab 全屏刷歌
    private val _fmActive = MutableStateFlow(false)
    val fmActive: StateFlow<Boolean> = _fmActive.asStateFlow()
    /** FM 可选风格：推荐新歌 + 歌单广场全部标签 */
    val fmStyles: List<String> = listOf("推荐新歌") + repository.sheetTags()
    private val _fmStyle = MutableStateFlow<String?>(null)
    val fmStyle: StateFlow<String?> = _fmStyle.asStateFlow()
    private val _fmQueue = MutableStateFlow<List<Song>>(emptyList())
    val fmQueue: StateFlow<List<Song>> = _fmQueue.asStateFlow()
    private val _fmIndex = MutableStateFlow(0)
    val fmIndex: StateFlow<Int> = _fmIndex.asStateFlow()
    private val _fmLoading = MutableStateFlow(false)
    val fmLoading: StateFlow<Boolean> = _fmLoading.asStateFlow()
    private val _fmError = MutableStateFlow<String?>(null)
    val fmError: StateFlow<String?> = _fmError.asStateFlow()
    private val _fmTreasure = MutableStateFlow<List<Song>>(emptyList())
    val fmTreasure: StateFlow<List<Song>> = _fmTreasure.asStateFlow()
    /** FM 漂移歌手池（点赞/听完收集；「歌手ID to 歌手名」） */
    private val fmDriftArtists = ArrayDeque<Pair<String,String>>()
    /** FM 已入队曲目 id（去重补充） */
    private val fmQueuedIds = hashSetOf<String>()
    // 排行榜页（覆盖层）
    val showRanks = MutableStateFlow(false)

    // 歌单详情
    private val _playlistSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlistSongs: StateFlow<List<Song>> = _playlistSongs.asStateFlow()
    val isLoadingPlaylist = MutableStateFlow(false)
    /** 歌单/榜单详情加载失败信息：此前失败静默成空白列表页，无文案无重试 */
    val playlistError = MutableStateFlow<String?>(null)
    /** 歌单详情请求令牌：快速连开两个歌单时丢弃慢的旧响应，防止串台 */
    @Volatile
    private var playlistRequestId = 0L
    /** 专辑详情请求令牌（openAlbum 用，防快速切换专辑时旧响应覆盖新数据） */
    @Volatile
    private var albumRequestId = 0L
    /** 歌手详情请求令牌（openArtist 用，同上） */
    @Volatile
    private var artistRequestId = 0L
    /** 歌单广场请求令牌（快速切标签或顺序时丢弃旧响应） */
    @Volatile
    private var sheetRequestId = 0L

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
    val authBusy = MutableStateFlow(false)
    val authError = MutableStateFlow<String?>(null)

    // 个人中心个性化状态
    val profileAvatar = MutableStateFlow(UserStore.getAvatarPath())
    val profileBackground = MutableStateFlow(UserStore.getBackgroundPath())
    val isProfileBgBlur = MutableStateFlow(UserStore.isBackgroundBlur())
    val profileNickname = MutableStateFlow(UserStore.getNickname())
    val profileBio = MutableStateFlow(UserStore.getBio())
    val profileGender = MutableStateFlow(UserStore.getGender())
    val profileBirthday = MutableStateFlow(UserStore.getBirthday())
    val profileUserId = MutableStateFlow(UserStore.getUserId())

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
    val themeMode = MutableStateFlow(SettingsStore.getThemeMode())

    // 音质等级
    val quality = MutableStateFlow(SettingsStore.getQuality())

    // 歌手页
    val showArtist = MutableStateFlow<ArtistRef?>(null)
    private val _artistDetail = MutableStateFlow<ArtistDetailData?>(null)
    val artistDetail: StateFlow<ArtistDetailData?> = _artistDetail.asStateFlow()
    val isLoadingArtist = MutableStateFlow(false)
    val artistError = MutableStateFlow<String?>(null)

    /** 歌手页定位信息（平台内标识） */
    data class ArtistRef(val platform: String, val id: String, val name: String)

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
        // 最近搜索词（本地持久化）
        viewModelScope.launch(Dispatchers.IO) {
            val words = RecentSearchStore.load()
            withContext(Dispatchers.Main) { _recentSearches.value = words }
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
        // 热门搜索词（netease 官方；失败用本地兜底词）
        viewModelScope.launch(Dispatchers.IO) {
            val hot = repository.hotSearchWords().getOrNull().orEmpty()
                .ifEmpty { defaultHotWords }
            withContext(Dispatchers.Main) { _hotSearchWords.value = hot }
        }
    }

    // ==================== 全局搜索 ====================

    /** 搜索页结果类型 */
    enum class SearchType(val label: String) {
        SONG("单曲"), ARTIST("歌手"), PLAYLIST("歌单"), ALBUM("专辑"), LYRIC("歌词")
    }

    val showSearch = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val searchType = MutableStateFlow(SearchType.SONG)
    /** 平台过滤（仅单曲 Tab 生效）：null = 全部平台聚合；否则为音源 id（netease/kugou/qq/kuwo） */
    val searchPlatform = MutableStateFlow<String?>(null)

    private val _songResults = MutableStateFlow<List<Song>>(emptyList())
    val songResults: StateFlow<List<Song>> = _songResults.asStateFlow()

    private val _lyricResults = MutableStateFlow<List<Song>>(emptyList())
    val lyricResults: StateFlow<List<Song>> = _lyricResults.asStateFlow()

    private val _artistResults = MutableStateFlow<List<ArtistItem>>(emptyList())
    val artistResults: StateFlow<List<ArtistItem>> = _artistResults.asStateFlow()

    private val _playlistResults = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlistResults: StateFlow<List<PlaylistItem>> = _playlistResults.asStateFlow()

    private val _albumResults = MutableStateFlow<List<AlbumItem>>(emptyList())
    val albumResults: StateFlow<List<AlbumItem>> = _albumResults.asStateFlow()

    val isSearching = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)

    /** 最近搜索（持久化，最多 10 条，新的在前） */
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    /** 热门搜索词（netease 官方热搜；不可用时本地兜底） */
    private val _hotSearchWords = MutableStateFlow<List<String>>(emptyList())
    val hotSearchWords: StateFlow<List<String>> = _hotSearchWords.asStateFlow()

    private var searchJob: Job? = null
    /** 各类型上次已执行的搜索签名（keyword|platform），切换类型时命中则不重复请求 */
    private val executedSearchKeys = mutableMapOf<SearchType, String>()

    /** 热搜兜底词：后端无 netease 源或网络失败时展示 */
    private val defaultHotWords =
        listOf("周杰伦", "热歌", "经典老歌", "抖音热门", "粤语经典", "华语流行", "民谣", "轻音乐")

    fun openSearch() { showSearch.value = true }
    fun closeSearch() { showSearch.value = false }

    /** 专辑详情覆盖层（搜索页专辑 Tab 点击进入） */
    val showAlbumDetail = MutableStateFlow<com.aesthetic.music.data.api.AlbumItem?>(null)
    private val _albumDetailSongs = MutableStateFlow<List<Song>>(emptyList())
    val albumDetailSongs: StateFlow<List<Song>> = _albumDetailSongs.asStateFlow()
    val isLoadingAlbum = MutableStateFlow(false)
    val albumError = MutableStateFlow<String?>(null)

    fun openAlbum(album: com.aesthetic.music.data.api.AlbumItem) {
        showAlbumDetail.value = album
        _albumDetailSongs.value = emptyList()
        albumError.value = null
        isLoadingAlbum.value = true
        val req = ++albumRequestId
        viewModelScope.launch {
            repository.getAlbum(album.id).onSuccess { detail ->
                // 请求在途期间用户已打开别的专辑：丢弃过期响应，防止串台
                if (req != albumRequestId) return@launch
                _albumDetailSongs.value = detail.songs
                // 详情返回的 info 更完整（封面/曲目数），同 id 时替换占位数据
                if (detail.info != null && showAlbumDetail.value?.id == album.id) {
                    showAlbumDetail.value = detail.info
                }
            }.onFailure { e ->
                if (req != albumRequestId) return@launch
                albumError.value = e.message ?: "获取专辑失败"
            }
            if (req == albumRequestId) isLoadingAlbum.value = false
        }
    }

    fun closeAlbum() {
        showAlbumDetail.value = null
        _albumDetailSongs.value = emptyList()
        albumError.value = null
    }

    /** 输入框内容变化：清空即复位；否则 400ms 防抖后自动搜索当前类型 */
    fun onSearchQueryChanged(q: String) {
        searchQuery.value = q
        searchJob?.cancel()
        val kw = q.trim()
        if (kw.isEmpty()) {
            resetSearchResults()
            isSearching.value = false
            searchError.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            runSearch(kw, force = false)
        }
    }

    /** 提交搜索（IME 搜索键 / 点最近搜索、热搜词）：立即执行并记入最近搜索 */
    fun submitSearch(word: String? = null) {
        val kw = (word ?: searchQuery.value).trim()
        if (kw.isEmpty()) return
        if (word != null && searchQuery.value != word) {
            searchQuery.value = word
        }
        recordRecentSearch(kw)
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(kw, force = true) }
    }

    /** 切换结果类型：有未执行的组合就补一次请求 */
    fun setSearchType(type: SearchType) {
        if (searchType.value == type) return
        searchType.value = type
        val kw = searchQuery.value.trim()
        if (kw.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(kw, force = false) }
        }
    }

    /** 切换平台（仅单曲生效）：重新搜索 */
    fun setSearchPlatform(platform: String?) {
        if (searchPlatform.value == platform) return
        searchPlatform.value = platform
        val kw = searchQuery.value.trim()
        if (searchType.value == SearchType.SONG && kw.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(kw, force = false) }
        }
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        RecentSearchStore.clear()
    }

    private fun resetSearchResults() {
        _songResults.value = emptyList()
        _lyricResults.value = emptyList()
        _artistResults.value = emptyList()
        _playlistResults.value = emptyList()
        _albumResults.value = emptyList()
        executedSearchKeys.clear()
    }

    private fun recordRecentSearch(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        val next = (listOf(w) + _recentSearches.value)
            .distinctBy { it.lowercase() }
            .take(10)
        _recentSearches.value = next
        RecentSearchStore.save(next)
    }

    /**
     * 执行当前类型的搜索。
     * - 单曲：走聚合接口，platform 非空时只搜指定源；
     * - 歌手/歌单/专辑/歌词：走 netease 官方接口（后端能力所限，与所选平台无关）。
     * 已执行过相同 keyword|platform 且非强制的请求直接跳过（切 Tab 不重复打接口）。
     */
    private suspend fun runSearch(kw: String, force: Boolean) {
        val type = searchType.value
        val platform = if (type == SearchType.SONG) searchPlatform.value else null
        val key = "$kw|${platform ?: "*"}"
        if (!force && executedSearchKeys[type] == key) return

        // 先清空当前类型的旧结果：此前失败时旧平台/旧类型的结果仍留在列表里，
        // 被静默当作本次搜索结果展示（用户切"酷狗"失败后看到的还是"全部平台"的曲目）
        when (type) {
            SearchType.SONG -> _songResults.value = emptyList()
            SearchType.ARTIST -> _artistResults.value = emptyList()
            SearchType.PLAYLIST -> _playlistResults.value = emptyList()
            SearchType.ALBUM -> _albumResults.value = emptyList()
            SearchType.LYRIC -> _lyricResults.value = emptyList()
        }

        isSearching.value = true
        searchError.value = null
        try {
            when (type) {
                SearchType.SONG -> repository.search(kw, 30, platform).onSuccess {
                    _songResults.value = it
                    executedSearchKeys[type] = key
                }.onFailure { e -> searchError.value = e.message ?: "搜索失败" }

                SearchType.ARTIST -> repository.searchArtists(kw, 12).onSuccess {
                    _artistResults.value = it
                    executedSearchKeys[type] = key
                }.onFailure { e -> searchError.value = e.message ?: "歌手搜索失败" }

                SearchType.PLAYLIST -> repository.searchPlaylists(kw, 20).onSuccess {
                    _playlistResults.value = it
                    executedSearchKeys[type] = key
                }.onFailure { e -> searchError.value = e.message ?: "歌单搜索失败" }

                SearchType.ALBUM -> repository.searchAlbums(kw, 20).onSuccess {
                    _albumResults.value = it
                    executedSearchKeys[type] = key
                }.onFailure { e -> searchError.value = e.message ?: "专辑搜索失败" }

                SearchType.LYRIC -> repository.searchByLyric(kw, 30).onSuccess {
                    _lyricResults.value = it
                    executedSearchKeys[type] = key
                }.onFailure { e -> searchError.value = e.message ?: "歌词搜索失败" }
            }
        } finally {
            isSearching.value = false
        }
    }

    // ==================== 歌手页 ====================

    fun openArtist(artist: ArtistItem) {
        openArtist(
            ArtistRef(
                platform = artist.platform ?: "netease",
                id = artist.id,
                name = artist.name,
            )
        )
    }

    fun openArtist(ref: ArtistRef) {
        showArtist.value = ref
        val req = ++artistRequestId
        viewModelScope.launch {
            isLoadingArtist.value = true
            _artistDetail.value = null
            artistError.value = null
            repository.getArtist(ref.platform, ref.id).onSuccess { detail ->
                // 请求在途期间用户已打开别的歌手：丢弃过期响应
                if (req != artistRequestId) return@launch
                _artistDetail.value = detail
            }.onFailure { e ->
                if (req != artistRequestId) return@launch
                // 失败要可见：否则页面显示"暂无相关歌曲"，把网络错误伪装成歌手无作品
                artistError.value = e.message ?: "获取歌手失败"
            }
            if (req == artistRequestId) isLoadingArtist.value = false
        }
    }

    fun closeArtist() {
        showArtist.value = null
        _artistDetail.value = null
        artistError.value = null
    }

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

    fun openHistory() { showHistory.value = true }
    fun closeHistory() { showHistory.value = false }

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

    // ==================== 内容加载（首页 / 分类页 / 排行榜页） ====================

    fun loadHotPlaylists(force: Boolean = false) {
        if (!force && _hotPlaylists.value.isNotEmpty()) return
        // 在途防重：此前首个请求在途时值为空，快速切 Tab 会并发打出多个相同请求
        if (!force && isLoadingHot.value) return
        viewModelScope.launch {
            isLoadingHot.value = true
            repository.getHotPlaylists(20).onSuccess { list ->
                _hotPlaylists.value = list
                hotPlaylistsError.value = null
            }.onFailure { e ->
                // 失败要可见：此前静默，"热门歌单"区块直接消失且无法重试
                hotPlaylistsError.value = e.message ?: "获取热门歌单失败"
            }
            isLoadingHot.value = false
        }
    }

    /**
     * 加载首页内容。四个请求并行发出（原先串行 await，延迟是四段网络往返之和）；
     * 已成功加载过则跳过（切 Tab 重入不再全量重拉），需要强制刷新用 [refreshContent]。
     */
    fun loadContent(force: Boolean = false) {
        if (isLoadingContent.value) return
        if (!force && contentLoaded) return
        viewModelScope.launch {
            isLoadingContent.value = true
            contentError.value = null
            var failures = 0
            var failureMsg = ""
            val dailyD = async { repository.getDaily(8) }
            val newsD = async { repository.getNewSongs(20) }
            val ranksD = async { repository.getRanks() }
            val catsD = async { repository.getCategories() }
            dailyD.await().onSuccess { _dailyPlaylists.value = it.playlists }
                .onFailure { failures++; failureMsg = "每日推荐" }
            newsD.await().onSuccess { _newSongs.value = it.songs }
                .onFailure { failures++; failureMsg = "新歌速递" }
            catsD.await().onSuccess { _homeCategories.value = it.categories }
                .onFailure { failures++; failureMsg = "分类歌单" }
            ranksD.await().onSuccess {
                _ranks.value = it.ranks
                if (it.ranks.isNotEmpty() && _hotSongs.value.isEmpty()) {
                    val hotRank = it.ranks.firstOrNull { r -> r.kind == "hot" }
                        ?: it.ranks.firstOrNull { r -> r.name.contains("热歌") }
                        ?: it.ranks.first()
                    // 独立协程：此前是 coroutineScope 的子协程，会把 isLoadingContent=false
                    // 拖到热歌请求结束，拉长整页 loading 窗口
                    viewModelScope.launch {
                        repository.getRankSongs(hotRank.id).onSuccess { songs ->
                            _hotSongs.value = songs
                        }
                    }
                }
            }.onFailure { failures++; failureMsg = "排行榜" }
            if (failures == 4) {
                contentError.value = "内容加载失败，请检查网络"
            } else if (failures > 0) {
                // 部分失败也要可见：此前只有全挂才报错，单个区块（如排行榜）失败后
                // 永久消失且无提示（contentLoaded=true 还拦截了重拉）
                contentError.value = "部分内容加载失败（$failureMsg），可点重试"
            }
            contentLoaded = true
            isLoadingContent.value = false
        }
    }

    /** 用户点"重试"时强制重新拉取内容 */
    fun refreshContent() = loadContent(force = true)

    // ==================== 歌单广场 ====================

    fun setSheetCat(cat: String) {
        if (sheetCat.value == cat) return
        sheetCat.value = cat
        loadSheetPlaylists(force = true)
    }

    fun setSheetOrder(order: String) {
        if (sheetOrder.value == order) return
        sheetOrder.value = order
        loadSheetPlaylists(force = true)
    }

    fun loadSheetPlaylists(force: Boolean = false) {
        if (!force && _sheetPlaylists.value.isNotEmpty()) return
        if (!force && isLoadingSheets.value) return
        val req = ++sheetRequestId
        viewModelScope.launch {
            isLoadingSheets.value = true
            sheetError.value = null
            repository.sheetPlaylists(sheetCat.value, sheetOrder.value)
                .onSuccess { list -> if (req == sheetRequestId) _sheetPlaylists.value = list }
                .onFailure { e -> if (req == sheetRequestId) sheetError.value = e.message ?: "获取歌单失败" }
            if (req == sheetRequestId) isLoadingSheets.value = false
        }
    }

    fun openSheet() {
        showSheet.value = true
        loadSheetPlaylists()
    }

    fun closeSheet() { showSheet.value = false }

    // ==================== FM 电台（会话级推荐逻辑） ====================

    /** 进入 FM（选风格后开拉种子池；推荐新歌 or 标签精品歌单） */
    fun fmStart( style : String ) {
        _fmStyle.value = style
        fmDriftArtists.clear()
        fmQueuedIds.clear()
        _fmQueue.value = emptyList()
        _fmIndex.value = 0
        _fmTreasure.value = emptyList()
        _fmError.value = null
        _fmActive.value = true
        _fmLoading.value = true
        viewModelScope.launch {
            repository.fmSeedSongs(style,  120).onSuccess { songs ->
                fmQueuedIds.addAll(songs.map { it.id })
                _fmQueue.value = songs
                _fmLoading.value = false
            }.onFailure { e ->
                _fmError.value = e.message ?: "加载失败"
                _fmLoading.value = false
            }
        }
    }

    /** FM 切歌（拖动/自动续播同步索引；近队尾触发补池） */
    fun fmSelect( index : Int ) {
        val q = _fmQueue.value
        if (index < 0 || index >= q.size) return
        _fmIndex.value = index
        maybeFmFill( index )
    }

    /** FM 点赞/取消：点赞 → 收藏（我喜欢的音乐）+ 本次宝藏歌单；取消 → 一并移除 */
    fun fmToggleLike( song : Song ) {
        toggleFavorite( song )
        val trea = _fmTreasure.value.toMutableList()
        if (_favoriteKeys.value.contains(song.id)) {
            if (trea.none { it.id == song.id }) {
                trea.add( song )
                _fmTreasure.value = trea
                fmOnSignal( song )
            }
        } else {
            _fmTreasure.value = trea.filterNot { it.id == song.id }
        }
    }

    /** FM 漂移信号：点赞/听完某曲 → 收集其歌手 → 补池优先拉该歌手（无需登录，本次会话越刷越准） */
    fun fmOnSignal( song : Song ) {
        if (!_fmActive.value) return
        val artistId = song.artistId ?: return
        val artistName = song.artist.substringBefore("/").trim()
        if (artistId.isBlank() || artistName.isBlank()) return
        if (fmDriftArtists.none { it.first == artistId }) {
            fmDriftArtists.addLast( artistId to artistName )
        }
        maybeFmFill( _fmIndex.value )
    }

    /** 接近队尾或收到信号时补充：优先漂移歌手，否则同风格再补池 */
    private fun maybeFmFill( index : Int ) {
        val q = _fmQueue.value
        if (q.size - index >= 5) return
        if (_fmLoading.value) return
        val style = _fmStyle.value ?: return
        _fmLoading.value = true
        viewModelScope.launch {
            val added = mutableListOf<Song>()
            val drift = fmDriftArtists.removeFirstOrNull()
            if ( drift != null ) {
                repository.artistTopSongs(drift.first,  50).onSuccess { songs ->
                    added += songs.filter { it.id.isNotBlank() && fmQueuedIds.add( it.id ) }
                }
                repository.searchSongs( drift.second ,  20 ).onSuccess { songs ->
                    added += songs.filter { it.id.isNotBlank() && fmQueuedIds.add( it.id ) }
                }
            }
            if (added.isEmpty()) {
                repository.fmSeedSongs(style,  80).onSuccess { songs ->
                    added += songs.filter { it.id.isNotBlank() && fmQueuedIds.add( it.id ) }
                }
            }
            if (added.isNotEmpty()) {
                val curr = _fmQueue.value
                _fmQueue.value = curr + added
            }
            _fmLoading.value = false
        }
    }

    /** FM 暂停漂移（切走 Tab 时；队列与宝藏歌单保留，本次 Session 仍可导出） */
    fun fmStopDrift() { _fmActive.value = false }

    // ==================== 排行榜页 ====================

    fun openRanks() { showRanks.value = true }
    fun closeRanks() { showRanks.value = false }

    // ==================== 歌单 ====================

    fun openPlaylistDetail(item: PlaylistItem) {
        showPlaylistDetail.value = item
        _playlistSongs.value = emptyList()
        playlistError.value = null
        val req = ++playlistRequestId
        viewModelScope.launch {
            isLoadingPlaylist.value = true
            repository.getPlaylist(item.id).onSuccess { list ->
                if (req == playlistRequestId) _playlistSongs.value = list
            }.onFailure { e ->
                // 失败要可见：此前只写 onSuccess，失败后列表空 + 无 loading，
                // Overlay 渲染 0 项 LazyColumn 白页，无文案无重试
                if (req == playlistRequestId) playlistError.value = e.message ?: "歌单加载失败"
            }
            if (req == playlistRequestId) isLoadingPlaylist.value = false
        }
    }

    fun openRankDetail(item: PlaylistItem) {
        showPlaylistDetail.value = item
        _playlistSongs.value = emptyList()
        playlistError.value = null
        val req = ++playlistRequestId
        viewModelScope.launch {
            isLoadingPlaylist.value = true
            repository.getRankSongs(item.id).onSuccess { list ->
                if (req == playlistRequestId) _playlistSongs.value = list
            }.onFailure { e ->
                if (req == playlistRequestId) playlistError.value = e.message ?: "榜单加载失败"
            }
            if (req == playlistRequestId) isLoadingPlaylist.value = false
        }
    }

    fun importPlaylist(id: String) {
        val req = ++playlistRequestId
        viewModelScope.launch {
            isLoadingPlaylist.value = true
            repository.getPlaylist(id).onSuccess { list ->
                if (req != playlistRequestId) return@launch
                _playlistSongs.value = list
                showPlaylistDetail.value = PlaylistItem(id = id, name = "导入的歌单")
            }.onFailure { e ->
                // 此前失败静默：弹窗关闭后"什么都没发生"
                if (req == playlistRequestId) {
                    _appToast.value = "导入歌单失败：${e.message ?: "网络异常"}"
                }
            }
            if (req == playlistRequestId) isLoadingPlaylist.value = false
        }
    }

    // ==================== 我的歌单 (本地优先自治) ====================

    fun createPlaylist(name: String, initialSongs: List<Song> = emptyList()) {
        val n = name.trim()
        if (n.isEmpty()) return
        val p = MyPlaylist(id = PlaylistStore.newId(), name = n, songs = initialSongs)
        _myPlaylists.value = listOf(p) + _myPlaylists.value
        saveMyPlaylists()
        showCreatePlaylistDialog.value = false
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
        playlistError.value = null
        _playlistSongs.value = p.songs
        showPlaylistDetail.value = PlaylistItem(
            id = p.id,
            name = p.name,
            coverUrl = p.coverUrl,
            trackCount = p.songs.size.toLong(),
            platform = "local",
        )
    }

    fun addCurrentToPlaylist(song: Song) {
        showCreatePlaylistDialog.value = true
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
                        lastSyncedAt.value = now
                        lastSyncStatus.value = "同步成功"
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

    val activeScriptInfo: StateFlow<ScriptMetadata?> = UserApiEngine.activeMetadata
    val isEngineReady: StateFlow<Boolean> = UserApiEngine.isEngineReady
    val engineError: StateFlow<String?> = UserApiEngine.engineError

    val syncServerUrl = MutableStateFlow(SyncServerStore.getServerUrl())
    val syncToken = MutableStateFlow(SyncServerStore.getSyncToken())
    val isSyncConfigured = MutableStateFlow(SyncServerStore.isConfigured())
    val lastSyncedAt = MutableStateFlow(SyncServerStore.getLastSyncedAt())
    val lastSyncStatus = MutableStateFlow(SyncServerStore.getLastSyncStatus())
    val isSyncing = MutableStateFlow(false)
    val isTestingSync = MutableStateFlow(false)

    /** 云端下发的全部音源脚本目录（本地可切换，无需回服务端修改激活项） */
    val scriptCatalog = MutableStateFlow<List<ScriptCatalogEntry>>(ScriptCatalogStore.list())

    fun saveSyncConfig(serverUrl: String, token: String) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanToken = token.trim()
        SyncServerStore.save(cleanUrl, cleanToken)
        syncServerUrl.value = cleanUrl
        syncToken.value = cleanToken
        isSyncConfigured.value = SyncServerStore.isConfigured()
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
            isTestingSync.value = true
            repository.testConnection().onSuccess {
                val msg = "连接正常！服务器同步功能就绪"
                _appToast.value = msg
                onResult?.invoke(true, msg)
            }.onFailure { e ->
                val msg = "连接失败：${e.message ?: "网络超时"}"
                _appToast.value = msg
                onResult?.invoke(false, msg)
            }
            isTestingSync.value = false
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
            isSyncing.value = true
            syncMutex.withLock {
                var takenEvents: List<PlayEventDto> = emptyList()
                try {
                    val since = SyncServerStore.getLastSyncVersion()
                    val eventSince = SyncServerStore.getLastEventSince()
                    val pullResult = repository.syncPull(since = since, eventSince = eventSince)
                    if (pullResult.isFailure) {
                        val err = pullResult.exceptionOrNull()?.message ?: "拉取失败"
                        SyncServerStore.updateSyncStatus("同步失败: $err")
                        lastSyncStatus.value = "同步失败: $err"
                        _appToast.value = "云同步失败：$err"
                        isSyncing.value = false
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
                    lastSyncedAt.value = now
                    lastSyncStatus.value = "同步成功"
                    _appToast.value = "云端双向同步成功！数据已更新"
                } catch (e: Exception) {
                    if (takenEvents.isNotEmpty()) {
                        StatsTracker.reenqueueEvents(takenEvents)
                    }
                    val err = e.message ?: "未知错误"
                    SyncServerStore.updateSyncStatus("同步失败: $err")
                    lastSyncStatus.value = "同步失败: $err"
                    _appToast.value = "同步异常：$err"
                } finally {
                    isSyncing.value = false
                }
            }
        }
    }

    /**
     * 直接导入 JS 脚本源码
     */
    fun importScriptFromText(content: String, name: String? = null) {
        if (content.isBlank()) {
            _appToast.value = "脚本代码不能为空"
            return
        }
        viewModelScope.launch {
            val meta = UserApiStore.saveScript(content, customName = name)
            val res = UserApiEngine.loadScript(content, meta)
            if (res.isSuccess) {
                _appToast.value = "已成功导入并激活音源脚本: [${meta.name}]"
            } else {
                val err = res.exceptionOrNull()?.message ?: "执行异常"
                _appToast.value = "脚本已保存，但沙箱初始化异常: $err"
            }
        }
    }

    /**
     * 从 URL 抓取并导入音源脚本
     */
    fun importScriptFromUrl(url: String, onFinished: (Boolean, String) -> Unit) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            onFinished(false, "URL 格式不正确")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val req = okhttp3.Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onFinished(false, "HTTP ${resp.code}: 下载失败")
                    }
                    return@launch
                }
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) {
                    withContext(Dispatchers.Main) {
                        onFinished(false, "下载的脚本内容为空")
                    }
                    return@launch
                }
                val meta = UserApiStore.saveScript(text)
                val res = UserApiEngine.loadScript(text, meta)
                withContext(Dispatchers.Main) {
                    if (res.isSuccess) {
                        _appToast.value = "已成功导入并激活音源脚本: [${meta.name}]"
                        onFinished(true, "导入成功")
                    } else {
                        val err = res.exceptionOrNull()?.message ?: "执行异常"
                        _appToast.value = "脚本已下载，但沙箱执行异常: $err"
                        onFinished(false, "脚本执行异常: $err")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onFinished(false, "下载失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 重新尝试加载当前已保存在本地的音源脚本
     */
    fun retryLoadScript() {
        val script = UserApiStore.getScriptContent()
        val meta = UserApiStore.getMetadata()
        if (script.isNullOrBlank() || meta == null) {
            _appToast.value = "未找到已保存的脚本"
            return
        }
        viewModelScope.launch {
            _appToast.value = "正在重新在沙箱中加载脚本…"
            val res = UserApiEngine.loadScript(script, meta)
            if (res.isSuccess) {
                _appToast.value = "音源脚本已成功激活: [${meta.name}]"
            } else {
                val err = res.exceptionOrNull()?.message ?: "执行异常"
                _appToast.value = "加载依然异常: $err"
            }
        }
    }

    fun unloadScript() {
        UserApiEngine.unloadScript()
        _appToast.value = "已卸载自定义音源脚本"
    }

    /**
     * 从已同步的脚本目录中本地切换音源（不回服务端修改激活项）。
     */
    fun switchScript(id: String) {
        val entry = scriptCatalog.value.firstOrNull { it.id == id }
        if (entry == null) {
            _appToast.value = "未找到该音源脚本，请先执行一次云同步"
            return
        }
        val content = ScriptCatalogStore.getContent(id)
        if (content.isNullOrBlank()) {
            _appToast.value = "脚本源码缺失，请重新同步"
            return
        }
        viewModelScope.launch {
            val meta = UserApiStore.saveScript(
                content = content,
                customName = entry.name,
                customVersion = entry.version,
                customId = entry.id
            )
            val res = UserApiEngine.loadScript(content, meta)
            if (res.isSuccess) {
                _appToast.value = "已本地切换到音源: [${entry.name}]"
            } else {
                _appToast.value = "切换失败: ${res.exceptionOrNull()?.message ?: "沙箱执行异常"}"
            }
        }
    }

    // ==================== 用户体系 (Auth) ====================

    fun showLoginSheet() { showLogin.value = true }
    fun clearAuthError() { authError.value = null }
    fun dismissLogin() { showLogin.value = false; authError.value = null }

    fun login(u: String, p: String) {
        if (u.isBlank() || p.isBlank()) {
            authError.value = "用户名与密码不能为空"
            return
        }
        viewModelScope.launch {
            authBusy.value = true
            authError.value = null
            repository.login(u, p).onSuccess { data ->
                UserStore.save(data.token, data.user)
                _user.value = data.user
                authBusy.value = false
                showLogin.value = false
                _appToast.value = "登录成功"
                // 登录成功后触发一次增量同步
                performCloudSync()
            }.onFailure { e ->
                authBusy.value = false
                authError.value = e.message ?: "登录失败"
            }
        }
    }

    fun register(u: String, p: String, n: String) {
        if (u.isBlank() || p.isBlank()) {
            authError.value = "用户名与密码不能为空"
            return
        }
        viewModelScope.launch {
            authBusy.value = true
            authError.value = null
            repository.register(u, p, n).onSuccess { data ->
                UserStore.save(data.token, data.user)
                _user.value = data.user
                authBusy.value = false
                showLogin.value = false
                _appToast.value = "注册成功并已登录"
                performCloudSync()
            }.onFailure { e ->
                authBusy.value = false
                authError.value = e.message ?: "注册失败"
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try { repository.logout() } catch (_: Exception) {}
            UserStore.logout()
            SyncServerStore.clear()
            _user.value = null
            syncServerUrl.value = ""
            syncToken.value = ""
            isSyncConfigured.value = false
            profileNickname.value = UserStore.getNickname()
            profileUserId.value = UserStore.getUserId()
            _appToast.value = "已退出登录并清空私有服务器配置"
        }
    }

    fun updateNickname(nickname: String) {
        if (nickname.isBlank()) return
        viewModelScope.launch {
            repository.updateProfile(nickname = nickname).onSuccess { updated ->
                _user.value = updated
                UserStore.updateUser(updated)
                profileNickname.value = updated.nickname.ifBlank { updated.username }
                _appToast.value = "昵称已更新"
            }.onFailure { e ->
                _appToast.value = "更新失败: ${e.message ?: "网络异常"}"
            }
        }
    }

    // ===== 个人中心个性化管理 =====

    fun setProfileAvatar(path: String?) {
        UserStore.setAvatarPath(path)
        profileAvatar.value = path
    }

    fun setProfileBackground(path: String?) {
        UserStore.setBackgroundPath(path)
        profileBackground.value = path
    }

    fun toggleProfileBgBlur() {
        val next = !isProfileBgBlur.value
        UserStore.setBackgroundBlur(next)
        isProfileBgBlur.value = next
    }

    fun restoreProfileDefaults(context: android.content.Context) {
        UserStore.restoreProfileDefaults(context)
        profileAvatar.value = null
        profileBackground.value = null
        isProfileBgBlur.value = false
        profileNickname.value = UserStore.getNickname()
        profileBio.value = ""
        profileGender.value = "保密"
        profileBirthday.value = ""
        profileUserId.value = UserStore.getUserId()
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
            profileNickname.value = nickname
        }
        UserStore.setBio(bio)
        profileBio.value = bio
        UserStore.setGender(gender)
        profileGender.value = gender
        UserStore.setBirthday(birthday)
        profileBirthday.value = birthday
        if (avatarPath != null) {
            UserStore.setAvatarPath(avatarPath)
            profileAvatar.value = avatarPath
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
        themeMode.value = mode
        SettingsStore.setThemeMode(mode)
        com.aesthetic.music.ui.theme.applyThemeMode(mode, systemDark)
    }

    fun setQuality(level: QualityLevel) {
        quality.value = level
        SettingsStore.setQuality(level)
    }

    // ==================== 导航 ====================

    fun switchTab(tab: AppTab) {
        currentTab.value = tab
        // 切 Tab 时关闭二级页面（搜索/歌单详情/歌手页/收藏页/最近播放等覆盖层），否则会盖住新 Tab 内容
        showPlaylistDetail.value = null
        showArtist.value = null
        _artistDetail.value = null
        showFavorites.value = false
        showCacheList.value = false
        showMyPlaylists.value = false
        showHistory.value = false
        showSearch.value = false
        showAlbumDetail.value = null
        showRanks.value = false
        showSheet.value = false
        _fmActive.value = (tab == AppTab.SHEET)
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
        private const val HISTORY_LIMIT = 100
        /** 收藏上限：SharedPreferences 存整组 JSON，无上限会随使用无限膨胀 */
        private const val MAX_FAVORITES = 1000
        /** 单个歌单曲目数上限 */
        private const val MAX_PLAYLIST_SONGS = 1000
    }
}
