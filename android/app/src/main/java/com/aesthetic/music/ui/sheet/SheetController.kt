package com.aesthetic.music.ui.sheet

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.service.LogReporter
import com.aesthetic.music.data.api.PlaylistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SheetController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    val tags: List<String> = repository.sheetTags()

    private val _cat = MutableStateFlow("全部")
    val cat = _cat.asStateFlow()
    private val _order = MutableStateFlow("hot")
    val order = _order.asStateFlow()
    private val _playlists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlists: StateFlow<List<PlaylistItem>> = _playlists.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    @Volatile
    private var requestId = 0L

    fun setCat(cat: String) {
        if (_cat.value == cat) return
        _cat.value = cat
        load(force = true)
    }

    fun setOrder(order: String) {
        if (_order.value == order) return
        _order.value = order
        load(force = true)
    }

    fun load(force: Boolean = false) {
        if (!force && _playlists.value.isNotEmpty()) return
        if (!force && isLoading.value) return
        val req = ++requestId
        scope.launch {
            _isLoading.value = true
            _error.value = null
            repository.sheetPlaylists(_cat.value, _order.value)
                .onSuccess { list -> if (req == requestId) _playlists.value = list }
                .onFailure { e ->
                    if (req == requestId) {
                        LogReporter.w("Sheet", "歌单广场加载失败", e)
                        _error.value = e.message ?: "获取歌单失败"
                    }
                }
            if (req == requestId) _isLoading.value = false
        }
    }

    fun ensureLoaded() = load(force = false)
}
