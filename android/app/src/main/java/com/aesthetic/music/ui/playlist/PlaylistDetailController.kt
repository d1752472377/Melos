package com.aesthetic.music.ui.playlist

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.platform.RankCatalog
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.service.LogReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    @Volatile
    private var requestId = 0L

    private var lastAction: (() -> Unit)? = null

    fun open(item: PlaylistItem) {
        lastAction = { open(item) }
        _showDetail.value = item
        _songs.value = emptyList()
        _error.value = null
        val req = ++requestId
        scope.launch {
            _isLoading.value = true
            repository.getPlaylistDetail(item.platform, item.id).onSuccess { data ->
                if (req != requestId) return@onSuccess
                _songs.value = data.songs
                data.info?.let { info ->
                    _showDetail.value = item.copy(
                        name = info.name.ifBlank { item.name },
                        coverUrl = info.coverUrl ?: item.coverUrl,
                        playCount = info.playCount ?: item.playCount,
                        trackCount = info.trackCount ?: item.trackCount,
                    )
                }
            }.onFailure { e ->
                if (req == requestId) {
                    LogReporter.w("PlaylistDetail", "歌单加载失败 platform=${item.platform} id=${item.id}", e)
                    _error.value = e.message ?: "歌单加载失败"
                }
            }
            if (req == requestId) _isLoading.value = false
        }
    }

    fun openRank(item: PlaylistItem) {
        lastAction = { openRank(item) }
        _showDetail.value = item
        _songs.value = emptyList()
        _error.value = null
        val req = ++requestId
        scope.launch {
            _isLoading.value = true
            // 平台必须跟着榜单条目走：QQ/酷狗/酷我的榜单 id 与网易云完全不同，
            repository.getRankSongs(item.platform ?: RankCatalog.DEFAULT, item.id).onSuccess { list ->
                if (req == requestId) _songs.value = list
            }.onFailure { e ->
                if (req == requestId) {
                    LogReporter.w("PlaylistDetail", "榜单歌曲加载失败 platform=${item.platform} id=${item.id}", e)
                    _error.value = e.message ?: "榜单加载失败"
                }
            }
            if (req == requestId) _isLoading.value = false
        }
    }

    fun openAlbum(album: AlbumItem) {
        lastAction = { openAlbum(album) }
        val item = PlaylistItem(
            id = album.id,
            name = album.name,
            coverUrl = album.coverUrl,
            trackCount = album.size ?: 0L,
            platform = album.platform,
        )
        _showDetail.value = item
        _songs.value = emptyList()
        _error.value = null
        val req = ++requestId
        scope.launch {
            _isLoading.value = true
            repository.getAlbum(album.id, album.platform).onSuccess { detail ->
                if (req != requestId) return@onSuccess
                _songs.value = detail.songs
                detail.info?.let { info ->
                    _showDetail.value = item.copy(
                        name = info.name.ifBlank { item.name },
                        coverUrl = info.coverUrl ?: item.coverUrl,
                        trackCount = info.size ?: item.trackCount,
                        platform = info.platform ?: item.platform,
                    )
                }
            }.onFailure { e ->
                if (req == requestId) {
                    LogReporter.w("PlaylistDetail", "专辑加载失败 platform=${item.platform} id=${item.id}", e)
                    _error.value = e.message ?: "获取专辑失败"
                }
            }
            if (req == requestId) _isLoading.value = false
        }
    }

    fun retry() {
        lastAction?.invoke()
    }

    /**
     * 按「专辑名 + 歌手」找专辑。歌曲数据里只有专辑名、没有专辑 id，
     * 所以点专辑时必须先搜一次拿到 id 才能打开详情。
     *
     * 优先取名字完全一致的那张；找不到就回调 [onMissing]，由调用方提示。
     */
    fun openAlbumByName(
        platform: String?,
        albumName: String,
        artistName: String,
        onResolved: () -> Unit,
        onMissing: () -> Unit,
    ) {
        val name = albumName.trim()
        if (name.isEmpty()) {
            onMissing()
            return
        }
        scope.launch {
            val keyword = listOf(name, artistName.trim()).filter { it.isNotBlank() }.joinToString(" ")
            val list = repository.searchAlbums(keyword, limit = 10, platform = platform)
                .getOrNull().orEmpty()
            val candidate = list.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
                ?: list.firstOrNull()
            if (candidate == null) {
                LogReporter.w("PlaylistDetail", "按名字找专辑无结果: $keyword platform=$platform")
                onMissing()
                return@launch
            }
            openAlbum(candidate)
            onResolved()
        }
    }

    fun showLocal(item: PlaylistItem, songs: List<Song>) {
        lastAction = null
        ++requestId
        _error.value = null
        _songs.value = songs
        _showDetail.value = item
    }

    fun showLocalPlaylist(playlist: MyPlaylist) {
        showLocal(
            item = PlaylistItem(
                id = playlist.id,
                name = playlist.name,
                coverUrl = playlist.coverUrl,
                trackCount = playlist.songs.size.toLong(),
                platform = "local",
            ),
            songs = playlist.songs,
        )
    }

    fun close() {
        lastAction = null
        _showDetail.value = null
    }
}
