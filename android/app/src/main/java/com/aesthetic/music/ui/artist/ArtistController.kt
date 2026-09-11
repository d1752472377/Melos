package com.aesthetic.music.ui.artist

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.ArtistDetailData
import com.aesthetic.music.data.api.ArtistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 歌手页定位信息（平台内标识） */
data class ArtistRef(val platform: String, val id: String, val name: String)

/**
 * 歌手页控制器：定位信息 + 详情拉取。
 *
 * 从 `MainViewModel` 抽出：只依赖 [MusicRepository]，用请求令牌防止快速切换歌手时旧响应覆盖。
 * `MainViewModel` 保留同名只读委托。
 */
class ArtistController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    private val _showArtist = MutableStateFlow<ArtistRef?>(null)
    val showArtist: StateFlow<ArtistRef?> = _showArtist.asStateFlow()
    private val _artistDetail = MutableStateFlow<ArtistDetailData?>(null)
    val artistDetail: StateFlow<ArtistDetailData?> = _artistDetail.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** 请求令牌（快速切换歌手时丢弃旧响应） */
    @Volatile
    private var requestId = 0L

    fun open(artist: ArtistItem) {
        open(
            ArtistRef(
                platform = artist.platform ?: "netease",
                id = artist.id,
                name = artist.name,
            )
        )
    }

    fun open(ref: ArtistRef) {
        _showArtist.value = ref
        val req = ++requestId
        scope.launch {
            _isLoading.value = true
            _artistDetail.value = null
            _error.value = null
            repository.getArtist(ref.platform, ref.id).onSuccess { detail ->
                // 请求在途期间用户已打开别的歌手：丢弃过期响应
                if (req != requestId) return@launch
                _artistDetail.value = detail
            }.onFailure { e ->
                if (req != requestId) return@launch
                // 失败要可见：否则页面显示"暂无相关歌曲"，把网络错误伪装成歌手无作品
                _error.value = e.message ?: "获取歌手失败"
            }
            if (req == requestId) _isLoading.value = false
        }
    }

    fun close() {
        _showArtist.value = null
        _artistDetail.value = null
        _error.value = null
    }
}
