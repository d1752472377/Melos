package com.aesthetic.music.ui.search

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.RecentSearchStore
import com.aesthetic.music.service.LogReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchType(val label: String) {
    SONG("单曲"), ARTIST("歌手"), PLAYLIST("歌单"), ALBUM("专辑")
}

class SearchController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _type = MutableStateFlow(SearchType.SONG)
    val type = _type.asStateFlow()

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

    fun hydrate() {
        scope.launch(Dispatchers.IO) {
            val words = RecentSearchStore.load()
            withContext(Dispatchers.Main) { _recentSearches.value = words }
        }
    }

    /**
     * 退出搜索页时调用：下次进来是全新的一次搜索。
     *
     * 只清"这一次搜索"的状态；最近搜索是用户资产，保留。
     */
    fun reset() {
        debounceJob?.cancel()
        cancelAllJobs()
        executedSearchKeys.clear()
        lastSearchedKeyword = ""
        _query.value = ""
        _type.value = SearchType.SONG
        _platform.value = null
        _isSearching.value = false
        _searchError.value = null
        resetResults()
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
                    }.onFailure { e ->
                        LogReporter.w("Search", "歌曲搜索失败 platform=${plat ?: "*"}", e)
                        _searchError.value = e.message ?: "搜索失败"
                    }

                    SearchType.ARTIST -> repository.searchArtists(kw, 12, plat).onSuccess {
                        _artistResults.value = it
                        executedSearchKeys[searchType] = key
                    }.onFailure { e ->
                        LogReporter.w("Search", "歌手搜索失败 platform=${plat ?: "*"}", e)
                        _searchError.value = e.message ?: "歌手搜索失败"
                    }

                    SearchType.PLAYLIST -> repository.searchPlaylists(kw, 20, plat).onSuccess {
                        _playlistResults.value = it
                        executedSearchKeys[searchType] = key
                    }.onFailure { e ->
                        LogReporter.w("Search", "歌单搜索失败 platform=${plat ?: "*"}", e)
                        _searchError.value = e.message ?: "歌单搜索失败"
                    }

                    SearchType.ALBUM -> repository.searchAlbums(kw, 20, plat).onSuccess {
                        _albumResults.value = it
                        executedSearchKeys[searchType] = key
                    }.onFailure { e ->
                        LogReporter.w("Search", "专辑搜索失败 platform=${plat ?: "*"}", e)
                        _searchError.value = e.message ?: "专辑搜索失败"
                    }
                }
            } finally {
                val anyActive = searchJobs.values.any { it.isActive }
                _isSearching.value = anyActive
            }
        }
    }
}