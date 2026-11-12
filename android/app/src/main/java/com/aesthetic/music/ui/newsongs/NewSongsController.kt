package com.aesthetic.music.ui.newsongs

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.platform.NewSongRegions
import com.aesthetic.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「新歌首发」页的数据：按地区取网易云新歌，每个地区只请求一次。
 */
class NewSongsController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    val regions: List<NewSongRegions.Region> = NewSongRegions.all

    private val _region = MutableStateFlow(regions.first())
    val region: StateFlow<NewSongRegions.Region> = _region.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val cache = mutableMapOf<String, List<Song>>()
    private var job: Job? = null

    fun ensureLoaded() {
        if (_songs.value.isEmpty() && !_loading.value) load(_region.value, force = false)
    }

    fun select(region: NewSongRegions.Region) {
        if (region.id == _region.value.id) return
        _region.value = region
        cache[region.id]?.takeIf { it.isNotEmpty() }?.let {
            _songs.value = it
            _error.value = null
            return
        }
        load(region, force = false)
    }

    fun refresh() = load(_region.value, force = true)

    private fun load(region: NewSongRegions.Region, force: Boolean) {
        if (!force) {
            cache[region.id]?.takeIf { it.isNotEmpty() }?.let {
                _songs.value = it
                _error.value = null
                return
            }
        }
        job?.cancel()
        _loading.value = true
        _error.value = null
        job = scope.launch {
            repository.getNewSongsByArea(region.areaId, 40)
                .onSuccess { list ->
                    cache[region.id] = list
                    _songs.value = list
                    if (list.isEmpty()) _error.value = "这个地区暂时没有新歌"
                }
                .onFailure { e -> _error.value = e.message ?: "加载失败" }
            _loading.value = false
        }
    }
}
