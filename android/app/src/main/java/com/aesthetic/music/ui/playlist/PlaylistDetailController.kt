package com.aesthetic.music.ui.playlist

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 歌单/榜单详情控制器（网络歌单详情 + 本地歌单展示）。
 *
 * 从 `MainViewModel` 抽出：只依赖 [MusicRepository]，用请求令牌防止快速连开两个歌单时旧响应串台。
 * 加载失败写入 [error] 由详情页内联展示；"导入外部歌单"已改由 [com.aesthetic.music.ui.library.PlaylistImportController]
 * 落成真正的本地歌单，不再借道这里的临时详情页。
 * `MainViewModel` 保留同名只读委托。
 */
class PlaylistDetailController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    private val _showDetail = MutableStateFlow<PlaylistItem?>(null)
    val showDetail: StateFlow<PlaylistItem?> = _showDetail.asStateFlow()
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    /** 歌单/榜单详情加载失败信息：此前失败静默成空白列表页，无文案无重试 */
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** 请求令牌：快速连开两个歌单时丢弃慢的旧响应，防止串台 */
    @Volatile
    private var requestId = 0L

    fun open(item: PlaylistItem) {
        _showDetail.value = item
        _songs.value = emptyList()
        _error.value = null
        val req = ++requestId
        scope.launch {
            _isLoading.value = true
            // 按条目自身平台取曲目：收藏/导入进来的歌单可能来自酷狗、酷我、QQ，
            // 以前一律走网易云接口，这些歌单打开后能对上 ID 就串歌、对不上就空白
            repository.getPlaylistDetail(item.platform, item.id).onSuccess { data ->
                if (req != requestId) return@onSuccess
                _songs.value = data.songs
                // 详情接口带回的名字/封面/曲目数更准，顺手刷新列表项展示信息
                data.info?.let { info ->
                    _showDetail.value = item.copy(
                        name = info.name.ifBlank { item.name },
                        coverUrl = info.coverUrl ?: item.coverUrl,
                        playCount = info.playCount ?: item.playCount,
                        trackCount = info.trackCount ?: item.trackCount,
                    )
                }
            }.onFailure { e ->
                // 失败要可见：此前只写 onSuccess，失败后列表空 + 无 loading，
                // Overlay 渲染 0 项 LazyColumn 白页，无文案无重试
                if (req == requestId) _error.value = e.message ?: "歌单加载失败"
            }
            if (req == requestId) _isLoading.value = false
        }
    }

    fun openRank(item: PlaylistItem) {
        _showDetail.value = item
        _songs.value = emptyList()
        _error.value = null
        val req = ++requestId
        scope.launch {
            _isLoading.value = true
            repository.getRankSongs(item.id).onSuccess { list ->
                if (req == requestId) _songs.value = list
            }.onFailure { e ->
                if (req == requestId) _error.value = e.message ?: "榜单加载失败"
            }
            if (req == requestId) _isLoading.value = false
        }
    }

    /** 直接展示本地数据（我的歌单），不发起网络请求；bump 令牌使在途请求作废 */
    fun showLocal(item: PlaylistItem, songs: List<Song>) {
        ++requestId
        _error.value = null
        _songs.value = songs
        _showDetail.value = item
    }

    fun close() {
        _showDetail.value = null
    }
}
