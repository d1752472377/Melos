package com.aesthetic.music.ui.home

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.platform.RankCatalog
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页内容控制器：热门歌单 / 每日推荐 / 新歌速递 / 榜单 / 分类歌单。
 *
 * 从 `MainViewModel` 抽出：内容体系自成一域——状态全部来自 [MusicRepository]，
 * 与播放/歌单/同步等域没有交集。`MainViewModel` 保留同名只读委托，视图层无需改动。
 */
class ContentController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    private val _hotPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val hotPlaylists: StateFlow<List<PlaylistItem>> = _hotPlaylists.asStateFlow()
    private val _isLoadingHot = MutableStateFlow(false)
    val isLoadingHot = _isLoadingHot.asStateFlow()

    /** 热门歌单加载失败信息（此前失败静默，区块直接消失） */
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

    /** 为你推荐卡片精选歌单内部的真实曲目列表 */
    private val _recommendPlaylistSongs = MutableStateFlow<List<Song>>(emptyList())
    val recommendPlaylistSongs: StateFlow<List<Song>> = _recommendPlaylistSongs.asStateFlow()
    private val _isLoadingRecommendSongs = MutableStateFlow(false)
    val isLoadingRecommendSongs = _isLoadingRecommendSongs.asStateFlow()

    @Volatile
    private var recommendReqId = 0L

    /** 内容已成功加载过一次：切 Tab 重入不再全量重拉（重试用 [refreshContent]） */
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
        // 在途防重：此前首个请求在途时值为空，快速切 Tab 会并发打出多个相同请求
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
                // 失败要可见：此前静默，"热门歌单"区块直接消失且无法重试
                _hotPlaylistsError.value = e.message ?: "获取热门歌单失败"
            }
            _isLoadingHot.value = false
        }
    }

    /**
     * 加载首页内容。四个请求并行发出（原先串行 await，延迟是四段网络往返之和）；
     * 已成功加载过则跳过（切 Tab 重入不再全量重拉），需要强制刷新用 [refreshContent]。
     */
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
                    // 独立协程：此前是 coroutineScope 的子协程，会把 isLoadingContent=false
                    // 拖到热歌请求结束，拉长整页 loading 窗口
                    scope.launch {
                        repository.getRankSongs(hotRank.platform ?: RankCatalog.DEFAULT, hotRank.id)
                            .onSuccess { songs ->
                                _hotSongs.value = songs
                            }
                    }
                }
            }.onFailure { failures++; failureMsg = "排行榜" }
            if (failures == 4) {
                _contentError.value = "内容加载失败，请检查网络"
            } else if (failures > 0) {
                // 部分失败也要可见：此前只有全挂才报错，单个区块（如排行榜）失败后
                // 永久消失且无提示（contentLoaded=true 还拦截了重拉）
                _contentError.value = "部分内容加载失败（$failureMsg），可点重试"
            }
            contentLoaded = true
            _isLoadingContent.value = false
        }
    }

    /** 用户点"重试"时强制重新拉取内容 */
    fun refreshContent() = loadContent(force = true)
}
