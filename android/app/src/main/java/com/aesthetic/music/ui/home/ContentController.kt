package com.aesthetic.music.ui.home

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.platform.NewSongRegions
import com.aesthetic.music.data.api.platform.RankCatalog
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.service.LogReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegionNewSongs(
    val region: NewSongRegions.Region,
    val songs: List<Song>,
) {
    val countLabel: String get() = "${songs.size} 首新歌"
}

class ContentController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    private val _hotPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val hotPlaylists: StateFlow<List<PlaylistItem>> = _hotPlaylists.asStateFlow()
    private val _isLoadingHot = MutableStateFlow(false)
    val isLoadingHot = _isLoadingHot.asStateFlow()

    private val _hotPlaylistsError = MutableStateFlow<String?>(null)
    val hotPlaylistsError = _hotPlaylistsError.asStateFlow()

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
    private val _isLoadingContent = MutableStateFlow(false)
    val isLoadingContent = _isLoadingContent.asStateFlow()
    private val _contentError = MutableStateFlow<String?>(null)
    val contentError = _contentError.asStateFlow()

    private val _recommendPlaylistSongs = MutableStateFlow<List<Song>>(emptyList())
    val recommendPlaylistSongs: StateFlow<List<Song>> = _recommendPlaylistSongs.asStateFlow()
    private val _isLoadingRecommendSongs = MutableStateFlow(false)
    val isLoadingRecommendSongs = _isLoadingRecommendSongs.asStateFlow()

    private val _newSongRegions = MutableStateFlow<List<RegionNewSongs>>(emptyList())
    val newSongRegions: StateFlow<List<RegionNewSongs>> = _newSongRegions.asStateFlow()

    @Volatile
    private var recommendReqId = 0L

    @Volatile
    private var contentLoaded = false

    fun loadRecommendPlaylistSongs(playlist: PlaylistItem) {
        if (playlist.id.isBlank()) return
        val req = ++recommendReqId
        scope.launch {
            _isLoadingRecommendSongs.value = true
            repository.getPlaylistDetail(playlist.platform, playlist.id).onSuccess { data ->
                if (req == recommendReqId) {
                    _recommendPlaylistSongs.value = data.songs
                }
            }.onFailure {
                if (req == recommendReqId) {
                    _recommendPlaylistSongs.value = emptyList()
                }
            }
            if (req == recommendReqId) {
                _isLoadingRecommendSongs.value = false
            }
        }
    }

    fun loadHotPlaylists(force: Boolean = false) {
        if (!force && _hotPlaylists.value.isNotEmpty()) return
        if (!force && isLoadingHot.value) return
        scope.launch {
            _isLoadingHot.value = true
            repository.getHotPlaylists(20).onSuccess { list ->
                _hotPlaylists.value = list
                _hotPlaylistsError.value = null
                if (_recommendPlaylistSongs.value.isEmpty() && _dailyPlaylists.value.isEmpty()) {
                    list.firstOrNull()?.let { loadRecommendPlaylistSongs(it) }
                }
            }.onFailure { e ->
                LogReporter.w("Home", "热门歌单加载失败", e)
                _hotPlaylistsError.value = e.message ?: "获取热门歌单失败"
            }
            _isLoadingHot.value = false
        }
    }

    fun loadContent(force: Boolean = false) {
        if (isLoadingContent.value) return
        if (!force && contentLoaded) return
        scope.launch {
            _isLoadingContent.value = true
            _contentError.value = null
            var failures = 0
            var failureMsg = ""
            val dailyD = async { repository.getDaily(8) }
            val newsD = async { repository.getNewSongs(20) }
            val ranksD = async { repository.getRanks() }
            val catsD = async { repository.getCategories() }
            loadRegionalNewSongs()
            dailyD.await().onSuccess {
                _dailyPlaylists.value = it.playlists
                val target = it.playlists.getOrNull(1) ?: it.playlists.firstOrNull()
                if (target != null) {
                    loadRecommendPlaylistSongs(target)
                }
            }.onFailure { failures++; failureMsg = "每日推荐" }
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
                    scope.launch {
                        repository.getRankSongs(hotRank.platform ?: RankCatalog.DEFAULT, hotRank.id)
                            .onSuccess { songs ->
                                _hotSongs.value = songs
                            }
                    }
                }
            }.onFailure { failures++; failureMsg = "排行榜" }
            if (failures == 4) {
                LogReporter.w("Home", "首页内容全部加载失败")
                _contentError.value = "内容加载失败，请检查网络"
            } else if (failures > 0) {
                LogReporter.w("Home", "首页部分内容加载失败 count=$failures sections=$failureMsg")
                _contentError.value = "部分内容加载失败（$failureMsg），可点重试"
            }
            contentLoaded = true
            _isLoadingContent.value = false
        }
    }

    fun refreshContent() = loadContent(force = true)

    private fun loadRegionalNewSongs() {
        scope.launch {
            val regions = NewSongRegions.pick(
                count = NewSongRegions.HOME_CARD_COUNT,
                dayKey = NewSongRegions.dayKeyOf(System.currentTimeMillis()),
            )
            val groups = regions.map { region ->
                async {
                    repository.getNewSongsByArea(region.areaId, limit = 20)
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { RegionNewSongs(region = region, songs = it) }
                }
            }.mapNotNull { it.await() }
            if (groups.isNotEmpty()) {
                val byId = groups.associateBy { it.region.id }
                _newSongRegions.value = regions.mapNotNull { byId[it.id] }
            }
        }
    }
}
