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
 * 歌单/榜单详情控制器（含导入歌单与本地歌单展示）。
 *
 * 从 `MainViewModel` 抽出：只依赖 [MusicRepository]，用请求令牌防止快速连开两个歌单时旧响应串台；
 * 导入失败经 [onToast] 提示。`MainViewModel` 保留同名只读委托。
 */
class PlaylistDetailController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    /** 向用户展示一次性提示（对应 ViewModel 的全局 Snackbar 通道） */
    private val onToast: (String) -> Unit,
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
            repository.getPlaylist(item.id).onSuccess { list ->
                if (req == requestId) _songs.value = list
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

    fun import(id: String) {
        val req = ++requestId
        scope.launch {
            _isLoading.value = true
            repository.getPlaylist(id).onSuccess { list ->
                if (req != requestId) return@launch
                _songs.value = list
                _showDetail.value = PlaylistItem(id = id, name = "导入的歌单")
            }.onFailure { e ->
                // 此前失败静默：弹窗关闭后"什么都没发生"
                if (req == requestId) {
                    onToast("导入歌单失败：${e.message ?: "网络异常"}")
                }
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
