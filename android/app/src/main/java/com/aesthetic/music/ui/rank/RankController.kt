package com.aesthetic.music.ui.rank

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.service.LogReporter
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.platform.RankCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RankController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    private val _platform = MutableStateFlow(RankCatalog.DEFAULT)
    val platform: StateFlow<String> = _platform.asStateFlow()

    private val _ranks = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val ranks: StateFlow<List<PlaylistItem>> = _ranks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val cache = mutableMapOf<String, List<PlaylistItem>>()

    private val loadedAtByPlatform = mutableMapOf<String, Long>()

    private val _loadedAt = MutableStateFlow(0L)
    val loadedAt: StateFlow<Long> = _loadedAt.asStateFlow()

    private var job: Job? = null

    private var requestId = 0

    fun select(platform: String, force: Boolean = false) {
        val p = RankCatalog.normalize(platform)
        val changed = _platform.value != p
        _platform.value = p
        if (!changed && !force && _ranks.value.isNotEmpty()) return
        val cached = cache[p]
        if (!force && cached != null) {
            _error.value = null
            _ranks.value = cached
            _loadedAt.value = loadedAtByPlatform[p] ?: 0L
            return
        }
        load(p, force = force)
    }

    fun ensureLoaded() {
        if (_ranks.value.isEmpty() && cache[_platform.value] == null) {
            load(_platform.value, force = false)
        }
    }

    fun retry() = load(_platform.value, force = true)

    private fun load(platform: String, force: Boolean) {
        job?.cancel()
        val req = ++requestId
        if (force) cache.remove(platform)
        job = scope.launch {
            _isLoading.value = true
            _error.value = null
            repository.getRanks(platform).onSuccess { data ->
                if (req != requestId) return@onSuccess
                val cleaned = RankCatalog.dedupe(data.ranks)
                cache[platform] = cleaned
                loadedAtByPlatform[platform] = System.currentTimeMillis()
                _loadedAt.value = loadedAtByPlatform[platform] ?: 0L
                _ranks.value = cleaned
                if (cleaned.isEmpty()) _error.value = "该平台暂未返回榜单"
            }.onFailure { e ->
                if (req != requestId) return@onFailure
                LogReporter.w("Rank", "榜单加载失败 platform=$platform", e)
                cache.remove(platform)
                _ranks.value = emptyList()
                _error.value = e.message?.takeIf { it.isNotBlank() } ?: "榜单加载失败"
            }
            if (req == requestId) _isLoading.value = false
        }
    }
}
