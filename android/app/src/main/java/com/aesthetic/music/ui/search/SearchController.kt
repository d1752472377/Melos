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
 * 全局搜索控制器：查询 / 类型 / 平台 + 四类结果 + 最近搜索。
 * 已移除：歌词搜索、热门搜索。
 */
class SearchController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _type = MutableStateFlow(SearchType.SONG)
    val type = _type.asStateFlow()

    /** 平台过滤：null = 全部平台；否则为 netease/qq/kugou/kuwo */
    private val _platform = MutableStateFlow<String?>(null)
    val platform = _platform.asStateFlow()

    private val _songResults = MutableStateFlow<List<Song>>(emptyList())
    val songResults: StateFlow<List<Song>> = _songResults.asStateFlow()

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

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private var debounceJob: Job? = null
    private val searchJobs = mutableMapOf<SearchType, Job>()
    private val executedSearchKeys = mutableMapOf<SearchType, String>()
    private var lastSearchedKeyword: String = ""

    /** 冷启动回填最近搜索 */
    fun hydrate() {
        scope.launch(Dispatchers.IO) {
            val words = RecentSearchStore.load()
            withContext(Dispatchers.Main) { _recentSearches.value = words }
        }
    }

    fun onQueryChanged(q: String) {
        _query.value = q
        debounceJob?.cancel()
        val kw = q.trim()
        if (kw.isEmpty()) {
            cancelAllJobs()
            resetResults()
            lastSearchedKeyword = ""
            _isSearching.value = false
            _searchError.value = null
            return
        }
        if (kw != lastSearchedKeyword) {
            cancelAllJobs()
            resetResults()
            lastSearchedKeyword = kw
        }
        debounceJob = scope.launch {
            delay(400)
            triggerSearch(kw, force = false)
        }
    }

    fun submit(word: String? = null) {
        val kw = (word ?: _query.value).trim()
        if (kw.isEmpty()) return
        if (word != null && _query.value != word) {
            _query.value = word
        }
        recordRecent(kw)
        debounceJob?.cancel()
        if (kw != lastSearchedKeyword) {
            cancelAllJobs()
            resetResults()
            lastSearchedKeyword = kw
        }
        triggerSearch(kw, force = true)
    }

    fun setType(type: SearchType) {
        if (_type.value == type) return
        _type.value = type
        val kw = _query.value.trim()
        if (kw.isNotEmpty()) {
            triggerSearch(kw, force = false)
        }
    }

    /** 切换平台：重搜当前类型，并使其它类型的平台缓存失效 */
    fun setPlatform(platform: String?) {
        if (_platform.value == platform) return
        _platform.value = platform
        executedSearchKeys.clear()
        val kw = _query.value.trim()
        if (kw.isNotEmpty()) {
            triggerSearch(kw, force = true)
        }
    }

    fun clearRecent() {
        _recentSearches.value = emptyList()
        RecentSearchStore.clear()
    }

    private fun cancelAllJobs() {
        debounceJob?.cancel()
        searchJobs.values.forEach { it.cancel() }
        searchJobs.clear()
    }

    private fun resetResults() {
        _songResults.value = emptyList()
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

    private fun triggerSearch(kw: String, force: Boolean) {
        val searchType = _type.value
        val plat = _platform.value
        val key = "$kw|${plat ?: "*"}"

        // 如果已有当前条件的有效缓存且非强制刷新，直接使用已有结果，不提前置空
        if (!force && executedSearchKeys[searchType] == key) return

        searchJobs[searchType]?.cancel()
        searchJobs[searchType] = scope.launch {
            _isSearching.value = true
            _searchError.value = null
            try {
                when (searchType) {
                    SearchType.SONG -> repository.search(kw, 30, plat).onSuccess {
                        _songResults.value = it
                        executedSearchKeys[searchType] = key
                    }.onFailure { e -> _searchError.value = e.message ?: "搜索失败" }

                    SearchType.ARTIST -> repository.searchArtists(kw, 12, plat).onSuccess {
                        _artistResults.value = it
                        executedSearchKeys[searchType] = key
                    }.onFailure { e -> _searchError.value = e.message ?: "歌手搜索失败" }

                    SearchType.PLAYLIST -> repository.searchPlaylists(kw, 20, plat).onSuccess {
                        _playlistResults.value = it
                        executedSearchKeys[searchType] = key
                    }.onFailure { e -> _searchError.value = e.message ?: "歌单搜索失败" }

                    SearchType.ALBUM -> repository.searchAlbums(kw, 20, plat).onSuccess {
                        _albumResults.value = it
                        executedSearchKeys[searchType] = key
                    }.onFailure { e -> _searchError.value = e.message ?: "专辑搜索失败" }
                }
            } finally {
                val anyActive = searchJobs.values.any { it.isActive }
                _isSearching.value = anyActive
            }
        }
    }
}