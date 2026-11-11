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

data class ArtistRef(
    val platform: String,
    val id: String,
    val name: String,
    val avatar: String? = null,
    val alias: String? = null,
)

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

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()
    private val _isLoadingAllSongs = MutableStateFlow(false)
    val isLoadingAllSongs: StateFlow<Boolean> = _isLoadingAllSongs.asStateFlow()
    private val _allSongsError = MutableStateFlow<String?>(null)
    val allSongsError: StateFlow<String?> = _allSongsError.asStateFlow()

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
                if (_allSongs.value.isEmpty() && finalDetail.songs.isNotEmpty()) {
                    _allSongs.value = finalDetail.songs
                }
            }.onFailure { e ->
                if (req != requestId) return@launch
                LogReporter.w("Artist", "歌手详情加载失败 platform=${ref.platform} id=${ref.id}", e)
                _error.value = e.message ?: "获取歌手失败"
            }
            if (req == requestId) _isLoading.value = false

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

    /**
     * 只知道歌手名字、不知道平台 id 时用它。
     *
     * 合唱歌曲里除第一位之外的歌手，平台数据里根本没有存 id，只能搜一次。
     * 搜到就用它（同名优先，否则退到第一条），再通过 [onResolved] 让界面跳转；
     * 完全搜不到则回调 [onMissing]，由调用方提示用户。
     */
    fun openByName(
        platform: String?,
        name: String,
        onResolved: () -> Unit,
        onMissing: () -> Unit,
    ) {
        val keyword = name.trim()
        if (keyword.isEmpty()) {
            onMissing()
            return
        }
        scope.launch {
            val result = repository.searchArtists(keyword, limit = 8, platform = platform)
            val list = result.getOrNull().orEmpty()
            val candidate = list.firstOrNull { it.name.trim().equals(keyword, ignoreCase = true) }
                ?: list.firstOrNull()
            if (candidate == null) {
                LogReporter.w("Artist", "按名字找歌手无结果: $keyword platform=$platform")
                onMissing()
                return@launch
            }
            open(
                ArtistRef(
                    platform = candidate.platform ?: platform ?: "netease",
                    id = candidate.id,
                    name = candidate.name,
                    avatar = candidate.avatar,
                    alias = candidate.alias,
                )
            )
            onResolved()
        }
    }
}
