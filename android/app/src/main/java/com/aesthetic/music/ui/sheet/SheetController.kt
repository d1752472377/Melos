package com.aesthetic.music.ui.sheet

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.PlaylistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 歌单广场控制器：标签 + 最新/最热 + 方块歌单。
 *
 * 从 `MainViewModel` 抽出：歌单广场只依赖 [MusicRepository]，与其它域无交集。
 * 快速切标签/顺序时用请求令牌丢弃旧响应。`MainViewModel` 保留同名只读委托。
 */
class SheetController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
) {

    /** 歌单广场标签（仓库静态配置，非 StateFlow） */
    val tags: List<String> = repository.sheetTags()

    private val _cat = MutableStateFlow("全部")
    val cat = _cat.asStateFlow()
    private val _order = MutableStateFlow("hot") // "hot"=最热 / "new"=最新
    val order = _order.asStateFlow()
    private val _playlists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlists: StateFlow<List<PlaylistItem>> = _playlists.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** 请求令牌：快速切标签或顺序时丢弃旧响应 */
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
                .onFailure { e -> if (req == requestId) _error.value = e.message ?: "获取歌单失败" }
            if (req == requestId) _isLoading.value = false
        }
    }

    /**
     * 进入歌单广场时调用：没数据就拉一次，已有数据不重复联网。
     *
     * 这个方法是「进页面显示'该分类下暂无歌单'」那个 bug 的正面修复：
     * 之前只有切标签（[setCat]）和点重试才会联网，直接进页面时列表一直是空的，
     * 于是"没加载过"被渲染成了"这个分类没有歌单"——两者是完全不同的事实。
     */
    fun ensureLoaded() = load(force = false)
}
