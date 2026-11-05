package com.aesthetic.music.ui.album

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 专辑详情控制器（搜索页专辑 Tab 点击进入的覆盖层）。
 *
 * 从 `MainViewModel` 抽出：只依赖 [MusicRepository]，用请求令牌防止快速切换专辑时旧响应覆盖。
 * 由对应 Destination 直接依赖本控制器。
 */
class AlbumController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    private val _showAlbumDetail = MutableStateFlow<AlbumItem?>(null)
    val showAlbumDetail: StateFlow<AlbumItem?> = _showAlbumDetail.asStateFlow()
    private val _albumDetailSongs = MutableStateFlow<List<Song>>(emptyList())
    val albumDetailSongs: StateFlow<List<Song>> = _albumDetailSongs.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** 请求令牌（防快速切换专辑时旧响应覆盖新数据） */
    @Volatile
    private var requestId = 0L

    fun open(album: AlbumItem) {
        _showAlbumDetail.value = album
        _albumDetailSongs.value = emptyList()
        _error.value = null
        _isLoading.value = true
        val req = ++requestId
        scope.launch {
            repository.getAlbum(album.id).onSuccess { detail ->
                // 请求在途期间用户已打开别的专辑：丢弃过期响应，防止串台
                if (req != requestId) return@launch
                _albumDetailSongs.value = detail.songs
                // 详情返回的 info 更完整（封面/曲目数），同 id 时替换占位数据
                if (detail.info != null && _showAlbumDetail.value?.id == album.id) {
                    _showAlbumDetail.value = detail.info
                }
            }.onFailure { e ->
                if (req != requestId) return@launch
                _error.value = e.message ?: "获取专辑失败"
            }
            if (req == requestId) _isLoading.value = false
        }
    }

    fun close() {
        _showAlbumDetail.value = null
        _albumDetailSongs.value = emptyList()
        _error.value = null
    }
}
