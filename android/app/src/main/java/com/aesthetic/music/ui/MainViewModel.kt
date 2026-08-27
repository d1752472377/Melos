package com.aesthetic.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistDetailData
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.api.UserStatsDto
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.FavoriteStore
import com.aesthetic.music.data.settings.HistoryStore
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.data.settings.PlaylistStore
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.settings.RecentSearchStore
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.data.settings.UserStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Mutex
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab { HOME, DISCOVER, PLAYLIST, PROFILE }

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

    // （全局搜索相关状态见下方「==================== 全局搜索 ====================」区块）

    // 发现页：热门歌单 + 内容体系
    private val _hotPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val hotPlaylists: StateFlow<List<PlaylistItem>> = _hotPlaylists.asStateFlow()
    val isLoadingHot = MutableStateFlow(false)

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

    // 歌单详情
    private val _playlistSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlistSongs: StateFlow<List<Song>> = _playlistSongs.asStateFlow()
    val isLoadingPlaylist = MutableStateFlow(false)
    /** 歌单详情请求令牌：快速连开两个歌单时丢弃慢的旧响应，防止串台 */
    @Volatile
    private var playlistRequestId = 0L
    /** 专辑详情请求令牌（openAlbum 用，防快速切换专辑时旧响应覆盖新数据） */
    @Volatile
    private var albumRequestId = 0L
    /** 歌手详情请求令牌（openArtist 用，同上） */
    @Volatile
    private var artistRequestId = 0L

    /**
     * 云同步串行互斥锁 + 待发标记：
     * 收藏/历史是后端"整表替换"语义，多个并发 PUT 乱序到达会让云端被旧快照覆盖。
     * 所有推送排进同一把锁按序执行；待发标记保证激增的推送合并为"只发最新一次"。
     */
    private val syncMutex = Mutex()
    private var favPushPending = false
    private var histPushPending = false

    // 用户体系
    private val _user = MutableStateFlow(UserStore.user())
    val user: StateFlow<UserInfo?> = _user.asStateFlow()
    val authBusy = MutableStateFlow(false)
    val authError = MutableStateFlow<String?>(null)

    // 我的歌单（本地持久化，登录后同步云端）
    private val _myPlaylists = MutableStateFlow<List<MyPlaylist>>(emptyList())
    val myPlaylists: StateFlow<List<MyPlaylist>> = _myPlaylists.asStateFlow()

    // 统计（本地真实累计 + 云端聚合）
    private val _localStats = MutableStateFlow(StatsStore.load())
    val localStats: StateFlow<LocalStats> = _localStats.asStateFlow()
    private val _cloudStats = MutableStateFlow<UserStatsDto?>(null)
    val cloudStats: StateFlow<UserStatsDto?> = _cloudStats.asStateFlow()
    /** 云端统计是否仍在拉取：Profile 徽标据此区分"云端实时 / 同步中 / 本机" */
    val isLoadingCloudStats = MutableStateFlow(false)

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
            val p = PlaylistStore.load()
            withContext(Dispatchers.Main) {
                _history.value = mergeSongs(_history.value, h).take(HISTORY_LIMIT)
                val fav = mergeSongs(_favoriteSongs.value, f).take(MAX_FAVORITES)
                _favoriteSongs.value = fav
                _favoriteKeys.value = fav.map { it.platform + ":" + it.id }.toSet()
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
        pushHistoryToCloud()
    }

    /** 最近播放页：移除单条记录（本地 + 云端全量覆盖同步） */
    fun removeFromHistory(song: Song) {
        val next = _history.value.filterNot { it.id == song.id && it.platform == song.platform }
        _history.value = next
        HistoryStore.save(next)
        pushHistoryToCloud()
    }

    /** 最近播放页：清空全部记录 */
    fun clearHistory() {
        _history.value = emptyList()
        HistoryStore.save(emptyList())
        pushHistoryToCloud()
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
        pushFavoritesToCloud()
    }

    fun isFavorite(song: Song): Boolean =
        (song.platform + ":" + song.id) in _favoriteKeys.value

    // ==================== 发现页 / 内容 ====================

    fun loadHotPlaylists() {
        if (_hotPlaylists.value.isNotEmpty()) return
        viewModelScope.launch {
            isLoadingHot.value = true
            repository.getHotPlaylists(20).onSuccess { list ->
                _hotPlaylists.value = list
            }
            isLoadingHot.value = false
        }
    }

    /**
     * 加载首页/发现页内容。四个请求并行发出（原先串行 await，延迟是四段网络往返之和）；
     * 已成功加载过则跳过（切 Tab 重入不再全量重拉），需要强制刷新用 [refreshContent]。
     */
    fun loadContent(force: Boolean = false) {
        if (isLoadingContent.value) return
        if (!force && contentLoaded) return
        viewModelScope.launch {
            isLoadingContent.value = true
            contentError.value = null
            var failures = 0
            coroutineScope {
                val dailyD = async { repository.getDaily(8) }
                val newsD = async { repository.getNewSongs(20) }
                val ranksD = async { repository.getRanks() }
                val catsD = async { repository.getCategories() }
                dailyD.await().onSuccess { _dailyPlaylists.value = it.playlists }.onFailure { failures++ }
                newsD.await().onSuccess { _newSongs.value = it.songs }.onFailure { failures++ }
                catsD.await().onSuccess { _homeCategories.value = it.categories }.onFailure { failures++ }
                ranksD.await().onSuccess {
                    _ranks.value = it.ranks
                    if (it.ranks.isNotEmpty() && _hotSongs.value.isEmpty()) {
                        val hotRank = it.ranks.firstOrNull { r -> r.kind == "hot" }
                            ?: it.ranks.firstOrNull { r -> r.name.contains("热歌") }
                            ?: it.ranks.first()
                        launch {
                            repository.getRankSongs(hotRank.id).onSuccess { songs ->
                                _hotSongs.value = songs
                            }
                        }
                    }
                }.onFailure { failures++ }
            }
            if (failures == 4) {
                contentError.value = "内容加载失败，请检查网络"
            } else {
                contentLoaded = true
            }
            isLoadingContent.value = false
        }
    }

    /** 用户点"重试"时强制重新拉取内容 */
    fun refreshContent() = loadContent(force = true)

    // ==================== 歌单 ====================

    fun openPlaylistDetail(item: PlaylistItem) {
        showPlaylistDetail.value = item
        val req = ++playlistRequestId
        viewModelScope.launch {
            isLoadingPlaylist.value = true
            _playlistSongs.value = emptyList()
            repository.getPlaylist(item.id).onSuccess { list ->
                if (req == playlistRequestId) _playlistSongs.value = list
            }
            if (req == playlistRequestId) isLoadingPlaylist.value = false
        }
    }

    fun openRankDetail(item: PlaylistItem) {
        showPlaylistDetail.value = item
        val req = ++playlistRequestId
        viewModelScope.launch {
            isLoadingPlaylist.value = true
            _playlistSongs.value = emptyList()
            repository.getRankSongs(item.id).onSuccess { list ->
                if (req == playlistRequestId) _playlistSongs.value = list
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
            }
            if (req == playlistRequestId) isLoadingPlaylist.value = false
        }
    }

    // ==================== 我的歌单 ====================

    fun createPlaylist(name: String, initialSongs: List<Song> = emptyList()) {
        val n = name.trim()
        if (n.isEmpty()) return
        val p = MyPlaylist(id = PlaylistStore.newId(), name = n, songs = initialSongs)
        _myPlaylists.value = listOf(p) + _myPlaylists.value
        saveMyPlaylists()
        pushPlaylistToCloud(p)
        showCreatePlaylistDialog.value = false
    }

    fun renamePlaylist(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        _myPlaylists.value = _myPlaylists.value.map {
            if (it.id == id) it.copy(name = n) else it
        }
        saveMyPlaylists()
        viewModelScope.launch {
            syncMutex.withLock {
                val p = _myPlaylists.value.firstOrNull { it.id == id } ?: return@withLock
                if (p.cloudId.isNotBlank()) repository.renameCloudPlaylist(p.cloudId, n)
            }
        }
    }

    fun deletePlaylist(id: String) {
        val target = _myPlaylists.value.firstOrNull { it.id == id } ?: return
        _myPlaylists.value = _myPlaylists.value.filterNot { it.id == id }
        saveMyPlaylists()
        viewModelScope.launch {
            syncMutex.withLock {
                if (target.cloudId.isNotBlank()) repository.deleteCloudPlaylist(target.cloudId)
            }
        }
    }

    fun addSongsToPlaylist(id: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        val target = _myPlaylists.value.firstOrNull { it.id == id } ?: return
        val merged = (target.songs + songs).distinctBy { it.platform + ":" + it.id }.take(MAX_PLAYLIST_SONGS)
        _myPlaylists.value = _myPlaylists.value.map {
            if (it.id == id) it.copy(songs = merged) else it
        }
        saveMyPlaylists()
        pushPlaylistSongsToCloud(id) // 锁内读最新快照发送，无需透传本次增量的歌曲列表
    }

    fun openMyPlaylist(id: String) {
        val p = _myPlaylists.value.firstOrNull { it.id == id } ?: return
        _playlistSongs.value = p.songs
        showPlaylistDetail.value = PlaylistItem(
            id = p.id,
            name = p.name,
            coverUrl = p.coverUrl,
            platform = "local",
        )
    }

    fun addCurrentToPlaylist(song: Song) {
        showCreatePlaylistDialog.value = true
        // 弹窗创建时可附带当前歌曲；通过一个待定队列实现
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

    private fun pushPlaylistToCloud(p: MyPlaylist) {
        viewModelScope.launch {
            syncMutex.withLock {
                if (!UserStore.isLoggedIn()) return@withLock
                val created = repository.createCloudPlaylist(p.name, p.coverUrl).getOrNull() ?: return@withLock
                _myPlaylists.value = _myPlaylists.value.map {
                    if (it.id == p.id) it.copy(cloudId = created.id) else it
                }
                saveMyPlaylists()
                if (p.songs.isNotEmpty()) {
                    repository.pushPlaylistSongs(created.id, p.songs)
                }
            }
        }
    }

    private fun pushPlaylistSongsToCloud(id: String) {
        viewModelScope.launch {
            syncMutex.withLock {
                if (!UserStore.isLoggedIn()) return@withLock
                val p = _myPlaylists.value.firstOrNull { it.id == id } ?: return@withLock
                if (p.cloudId.isBlank()) {
                    // 尚未建过云端歌单：补建并同步（锁内复用逻辑，直接内联）
                    val created = repository.createCloudPlaylist(p.name, p.coverUrl).getOrNull() ?: return@withLock
                    val updated = p.copy(cloudId = created.id)
                    _myPlaylists.value = _myPlaylists.value.map { if (it.id == id) updated else it }
                    saveMyPlaylists()
                    repository.pushPlaylistSongs(created.id, updated.songs)
                } else {
                    // 发送锁内读到的最新快照，避免旧列表覆盖新变更
                    val latest = _myPlaylists.value.firstOrNull { it.id == id }
                    if (latest != null && latest.cloudId == p.cloudId) {
                        repository.pushPlaylistSongs(p.cloudId, latest.songs)
                    }
                }
            }
        }
    }

    // ==================== 用户 / 云同步 ====================

    fun showLoginSheet() {
        authError.value = null
        showLogin.value = true
    }

    /** 仅清除错误提示（登录/注册表单切换模式时用），不关闭弹层 */
    fun clearAuthError() {
        authError.value = null
    }

    fun dismissLogin() {
        showLogin.value = false
        authError.value = null
    }

    fun login(username: String, password: String) {
        val u = username.trim()
        if (u.isEmpty() || password.isEmpty()) {
            authError.value = "请输入用户名和密码"
            return
        }
        viewModelScope.launch {
            authBusy.value = true
            authError.value = null
            repository.login(u, password).onSuccess { auth ->
                UserStore.save(auth.token, auth.user)
                _user.value = auth.user
                showLogin.value = false
                syncCloudDown()
                refreshCloudStats()
            }.onFailure { e ->
                authError.value = e.message ?: "登录失败"
            }
            authBusy.value = false
        }
    }

    fun register(username: String, password: String, nickname: String) {
        val u = username.trim()
        if (u.isEmpty() || password.length < 6) {
            authError.value = "用户名不能为空，密码至少 6 位"
            return
        }
        viewModelScope.launch {
            authBusy.value = true
            authError.value = null
            repository.register(u, password, nickname.trim().ifEmpty { u }).onSuccess { auth ->
                UserStore.save(auth.token, auth.user)
                _user.value = auth.user
                showLogin.value = false
                pushFavoritesToCloud()
                pushHistoryToCloud()
                refreshCloudStats()
            }.onFailure { e ->
                authError.value = e.message ?: "注册失败"
            }
            authBusy.value = false
        }
    }

    fun logout() {
        UserStore.logout()
        _user.value = null
        _cloudStats.value = null
        authError.value = null
    }

    fun updateNickname(nickname: String) {
        val n = nickname.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            repository.updateProfile(n).onSuccess { info ->
                _user.value = info
                UserStore.updateUser(info)
            }.onFailure { e ->
                authError.value = e.message ?: "修改失败"
            }
        }
    }

    /** 登录后：本地数据与云端合并（本地优先），并同步回云端（整段串行执行） */
    private fun syncCloudDown() {
        viewModelScope.launch {
            syncMutex.withLock {
                if (!UserStore.isLoggedIn()) return@withLock
                // 收藏合并
                val cloudFav = repository.cloudFavorites().getOrElse { emptyList() }
                val mergedFav = mergeSongs(_favoriteSongs.value, cloudFav).take(MAX_FAVORITES)
                _favoriteSongs.value = mergedFav
                _favoriteKeys.value = mergedFav.map { it.platform + ":" + it.id }.toSet()
                FavoriteStore.save(mergedFav)
                // 直接推送合并结果，并复位待发标记（队列里的旧推送任务拿到锁后会发现无待发而跳过）
                favPushPending = false
                repository.pushFavorites(mergedFav)

                // 历史合并
                val cloudHis = repository.cloudHistory().getOrElse { emptyList() }
                val mergedHis = mergeSongs(_history.value, cloudHis).take(HISTORY_LIMIT)
                _history.value = mergedHis
                HistoryStore.save(mergedHis)
                histPushPending = false
                repository.pushHistory(mergedHis)

                // 歌单合并
                val cloudPls = repository.cloudPlaylists().getOrElse { emptyList() }
                for (cp in cloudPls) {
                    val exist = _myPlaylists.value.firstOrNull { it.id == cp.id || it.cloudId == cp.id }
                    if (exist == null) {
                        val songs = repository.cloudPlaylistSongs(cp.id).getOrElse { emptyList() }
                        _myPlaylists.value = _myPlaylists.value + MyPlaylist(
                            id = cp.id, name = cp.name, songs = songs, cloudId = cp.id,
                        )
                    }
                }
                saveMyPlaylists()
                // 本地歌单推送到云端
                for (p in _myPlaylists.value.toList()) {
                    if (p.cloudId.isBlank()) {
                        val created = repository.createCloudPlaylist(p.name, p.coverUrl).getOrNull() ?: continue
                        val updated = p.copy(cloudId = created.id)
                        _myPlaylists.value = _myPlaylists.value.map { if (it.id == p.id) updated else it }
                        saveMyPlaylists()
                        if (p.songs.isNotEmpty()) repository.pushPlaylistSongs(created.id, p.songs)
                    } else {
                        repository.pushPlaylistSongs(p.cloudId, p.songs)
                    }
                }
            }
        }
    }

    private fun mergeSongs(local: List<Song>, cloud: List<Song>): List<Song> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Song>()
        for (s in (local + cloud)) {
            val key = s.platform + ":" + s.id
            if (seen.add(key)) out.add(s)
        }
        return out
    }

    private fun pushFavoritesToCloud() {
        if (!UserStore.isLoggedIn()) return
        favPushPending = true
        viewModelScope.launch {
            syncMutex.withLock {
                // 队列中可能积压多个推送：只发最新一次（合并），且发送锁内读到的最新快照
                if (!favPushPending) return@withLock
                favPushPending = false
                repository.pushFavorites(_favoriteSongs.value)
            }
        }
    }

    private fun pushHistoryToCloud() {
        if (!UserStore.isLoggedIn()) return
        histPushPending = true
        viewModelScope.launch {
            syncMutex.withLock {
                if (!histPushPending) return@withLock
                histPushPending = false
                repository.pushHistory(_history.value)
            }
        }
    }

    // ==================== 统计 ====================

    fun refreshStats() {
        _localStats.value = StatsStore.load()
        if (UserStore.isLoggedIn()) refreshCloudStats()
    }

    private fun refreshCloudStats() {
        viewModelScope.launch {
            isLoadingCloudStats.value = true
            repository.userStats().onSuccess { _cloudStats.value = it }
            isLoadingCloudStats.value = false
        }
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
        if (tab == AppTab.DISCOVER) {
            loadHotPlaylists()
            loadContent()
        }
        if (tab == AppTab.PROFILE) refreshStats()
    }

    companion object {
        private const val HISTORY_LIMIT = 100
        /** 收藏上限：SharedPreferences 存整组 JSON，无上限会随使用无限膨胀 */
        private const val MAX_FAVORITES = 1000
        /** 单个歌单曲目数上限 */
        private const val MAX_PLAYLIST_SONGS = 1000
    }
}
