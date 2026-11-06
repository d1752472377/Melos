package com.aesthetic.music.ui.artist

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.ArtistDetailData
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.service.LogReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 歌手页定位信息（平台内标识） */
data class ArtistRef(
    val platform: String,
    val id: String,
    val name: String,
    val avatar: String? = null,
    val alias: String? = null,
)

/**
 * 歌手页控制器：定位信息 + 详情拉取 + 全量歌曲拉取。
 *
 * 从 `MainViewModel` 抽出：只依赖 [MusicRepository]，用请求令牌防止快速切换歌手时旧响应覆盖。
 * 由对应 Destination 直接依赖本控制器。
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

    // 全部演唱歌曲列表及状态
    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()
    private val _isLoadingAllSongs = MutableStateFlow(false)
    val isLoadingAllSongs: StateFlow<Boolean> = _isLoadingAllSongs.asStateFlow()
    private val _allSongsError = MutableStateFlow<String?>(null)
    val allSongsError: StateFlow<String?> = _allSongsError.asStateFlow()

    /** 请求令牌（快速切换歌手时丢弃旧响应） */
    @Volatile
    private var requestId = 0L

    fun open(artist: ArtistItem) {
        open(
            ArtistRef(
                platform = artist.platform ?: "netease",
                id = artist.id,
                name = artist.name,
                avatar = artist.avatar,
                alias = artist.alias,
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
            _allSongs.value = emptyList()
            _allSongsError.value = null

            repository.getArtist(ref.platform, ref.id).onSuccess { detail ->
                // 请求在途期间用户已打开别的歌手：丢弃过期响应
                if (req != requestId) return@launch
                val finalInfo = detail.info?.copy(
                    avatar = detail.info.avatar ?: ref.avatar,
                    alias = detail.info.alias ?: ref.alias,
                ) ?: ArtistItem(
                    id = ref.id,
                    name = ref.name,
                    avatar = ref.avatar,
                    alias = ref.alias,
                    platform = ref.platform,
                )
                val finalDetail = detail.copy(info = finalInfo)
                _artistDetail.value = finalDetail
                // 先用前 50 首作为全部演唱的初始预填，保证跳转"全部演唱"绝不出现白屏
                if (_allSongs.value.isEmpty() && finalDetail.songs.isNotEmpty()) {
                    _allSongs.value = finalDetail.songs
                }
            }.onFailure { e ->
                if (req != requestId) return@launch
                LogReporter.w("Artist", "歌手详情加载失败 platform=${ref.platform} id=${ref.id}", e)
                // 失败要可见：否则页面显示"暂无相关歌曲"，把网络错误伪装成歌手无作品
                _error.value = e.message ?: "获取歌手失败"
            }
            if (req == requestId) _isLoading.value = false

            // 后台静默启动全量歌曲拉取
            loadAllSongsInternal(ref, req)
        }
    }

    fun loadAllSongs() {
        val ref = _showArtist.value ?: return
        val req = requestId
        scope.launch {
            loadAllSongsInternal(ref, req)
        }
    }

    private suspend fun loadAllSongsInternal(ref: ArtistRef, req: Long) {
        _isLoadingAllSongs.value = true
        _allSongsError.value = null
        repository.getArtistAllSongs(ref.platform, ref.id).onSuccess { fullList ->
            if (req != requestId) return@onSuccess
            if (fullList.isNotEmpty()) {
                _allSongs.value = fullList
            }
        }.onFailure { e ->
            if (req != requestId) return@onFailure
            LogReporter.w("Artist", "全部歌曲加载失败 platform=${ref.platform} id=${ref.id}", e)
            // 如果已有种子单曲（如详情页前50首），不覆写页面为纯错误态，仅记录供重试
            _allSongsError.value = e.message ?: "拉取全部曲目失败"
        }
        if (req == requestId) _isLoadingAllSongs.value = false
    }

    fun close() {
        _showArtist.value = null
        _artistDetail.value = null
        _error.value = null
        _allSongs.value = emptyList()
        _isLoadingAllSongs.value = false
        _allSongsError.value = null
    }
}
