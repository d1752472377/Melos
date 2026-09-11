package com.aesthetic.music.ui.search

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.RecentSearchStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全局搜索控制器：查询/类型/平台 + 五类结果 + 最近搜索 + 热门搜索词。
 *
 * 从 `MainViewModel` 抽出：只依赖 [MusicRepository] 与 [RecentSearchStore]，
 * 与其它域无交集。输入 400ms 防抖、按 keyword|platform 去重、切类型补请求等语义原样保留。
 * `MainViewModel` 保留同名只读委托，`SearchScreen` 无需改动。
 */
class SearchController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _type = MutableStateFlow(SearchType.SONG)
    val type = _type.asStateFlow()

    /** 平台过滤（仅单曲 Tab 生效）：null = 全部平台聚合；否则为音源 id（netease/kugou/qq/kuwo） */
    private val _platform = MutableStateFlow<String?>(null)
    val platform = _platform.asStateFlow()

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

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()

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

    /** 冷启动异步回填：最近搜索（本地持久化）+ 热门搜索词（netease 官方，失败用兜底词） */
    fun hydrate() {
        scope.launch(Dispatchers.IO) {
            val words = RecentSearchStore.load()
            withContext(Dispatchers.Main) { _recentSearches.value = words }
        }
        scope.launch(Dispatchers.IO) {
            val hot = repository.hotSearchWords().getOrNull().orEmpty()
                .ifEmpty { defaultHotWords }
            withContext(Dispatchers.Main) { _hotSearchWords.value = hot }
        }
    }

    /** 输入框内容变化：清空即复位；否则 400ms 防抖后自动搜索当前类型 */
    fun onQueryChanged(q: String) {
        _query.value = q
        searchJob?.cancel()
        val kw = q.trim()
        if (kw.isEmpty()) {
            resetResults()
            _isSearching.value = false
            _searchError.value = null
            return
        }
        searchJob = scope.launch {
            delay(400)
            runSearch(kw, force = false)
        }
    }

    /** 提交搜索（IME 搜索键 / 点最近搜索、热搜词）：立即执行并记入最近搜索 */
    fun submit(word: String? = null) {
        val kw = (word ?: _query.value).trim()
        if (kw.isEmpty()) return
        if (word != null && _query.value != word) {
            _query.value = word
        }
        recordRecent(kw)
        searchJob?.cancel()
        searchJob = scope.launch { runSearch(kw, force = true) }
    }

    /** 切换结果类型：有未执行的组合就补一次请求 */
    fun setType(type: SearchType) {
        if (_type.value == type) return
        _type.value = type
        val kw = _query.value.trim()
        if (kw.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = scope.launch { runSearch(kw, force = false) }
        }
    }

    /** 切换平台（仅单曲生效）：重新搜索 */
    fun setPlatform(platform: String?) {
        if (_platform.value == platform) return
        _platform.value = platform
        val kw = _query.value.trim()
        if (_type.value == SearchType.SONG && kw.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = scope.launch { runSearch(kw, force = false) }
        }
    }

    fun clearRecent() {
        _recentSearches.value = emptyList()
        RecentSearchStore.clear()
    }

    private fun resetResults() {
        _songResults.value = emptyList()
        _lyricResults.value = emptyList()
        _artistResults.value = emptyList()
        _playlistResults.value = emptyList()
        _albumResults.value = emptyList()
        executedSearchKeys.clear()
    }

    private fun recordRecent(word: String) {
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
        val searchType = _type.value
        val plat = if (searchType == SearchType.SONG) _platform.value else null
        val key = "$kw|${plat ?: "*"}"
        if (!force && executedSearchKeys[searchType] == key) return

        // 先清空当前类型的旧结果：此前失败时旧平台/旧类型的结果仍留在列表里，
        // 被静默当作本次搜索结果展示（用户切"酷狗"失败后看到的还是"全部平台"的曲目）
        when (searchType) {
            SearchType.SONG -> _songResults.value = emptyList()
            SearchType.ARTIST -> _artistResults.value = emptyList()
            SearchType.PLAYLIST -> _playlistResults.value = emptyList()
            SearchType.ALBUM -> _albumResults.value = emptyList()
            SearchType.LYRIC -> _lyricResults.value = emptyList()
        }

        _isSearching.value = true
        _searchError.value = null
        try {
            when (searchType) {
                SearchType.SONG -> repository.search(kw, 30, plat).onSuccess {
                    _songResults.value = it
                    executedSearchKeys[searchType] = key
                }.onFailure { e -> _searchError.value = e.message ?: "搜索失败" }

                SearchType.ARTIST -> repository.searchArtists(kw, 12).onSuccess {
                    _artistResults.value = it
                    executedSearchKeys[searchType] = key
                }.onFailure { e -> _searchError.value = e.message ?: "歌手搜索失败" }

                SearchType.PLAYLIST -> repository.searchPlaylists(kw, 20).onSuccess {
                    _playlistResults.value = it
                    executedSearchKeys[searchType] = key
                }.onFailure { e -> _searchError.value = e.message ?: "歌单搜索失败" }

                SearchType.ALBUM -> repository.searchAlbums(kw, 20).onSuccess {
                    _albumResults.value = it
                    executedSearchKeys[searchType] = key
                }.onFailure { e -> _searchError.value = e.message ?: "专辑搜索失败" }

                SearchType.LYRIC -> repository.searchByLyric(kw, 30).onSuccess {
                    _lyricResults.value = it
                    executedSearchKeys[searchType] = key
                }.onFailure { e -> _searchError.value = e.message ?: "歌词搜索失败" }
            }
        } finally {
            _isSearching.value = false
        }
    }
}
