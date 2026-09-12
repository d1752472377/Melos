package com.aesthetic.music.ui.rank

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.platform.RankCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 排行榜页控制器：平台切换 + 各平台榜单列表。
 *
 * 为什么单独一个控制器而不是塞回 `ContentController`：首页的「排行榜」区块只认网易云热歌榜
 * （进首页就要用），而这里是"用户主动挑平台"的浏览行为，生命周期与缓存策略都不同——
 * 按平台各存一份缓存、切回来立即出结果；如果混进首页那套，会把首页的加载失败状态
 * 与榜单页的失败状态搅在一起。
 */
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

    /**
     * 按平台缓存榜单列表。
     *
     * 榜单是"天级"更新的数据，来回切 tag 时重新联网纯属浪费，还会让界面闪 loading；
     * 但**失败不写缓存**，这样"重试"永远是真的重试，不会拿空结果当缓存命中。
     */
    private val cache = mutableMapOf<String, List<PlaylistItem>>()

    private var job: Job? = null

    /** 在途请求作废令牌：快速连点 tag 时，先发的慢请求不允许覆盖后发的快请求 */
    private var requestId = 0

    /**
     * 切换平台。命中缓存直接出结果；[force] 用于下拉/重试这类显式刷新。
     */
    fun select(platform: String, force: Boolean = false) {
        val p = RankCatalog.normalize(platform)
        val changed = _platform.value != p
        _platform.value = p
        if (!changed && !force && _ranks.value.isNotEmpty()) return
        val cached = cache[p]
        if (!force && cached != null) {
            _error.value = null
            _ranks.value = cached
            return
        }
        load(p, force = force)
    }

    /** 进入榜单页：没数据就拉，已有数据不重复联网（重试走 [retry]） */
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
                // 排序/去重在网络层做（PlatformMusicService 已排序，这里再兜一次去重）：
                // 重复的 平台:id 会让榜单列表的 LazyColumn key 冲突并直接崩页
                val cleaned = RankCatalog.dedupe(data.ranks)
                cache[platform] = cleaned
                _ranks.value = cleaned
                if (cleaned.isEmpty()) _error.value = "该平台暂未返回榜单"
            }.onFailure { e ->
                if (req != requestId) return@onFailure
                // 失败时清空列表：留着上一个平台的榜单配着新 tag 显示，比空页面更容易误判
                cache.remove(platform)
                _ranks.value = emptyList()
                _error.value = e.message?.takeIf { it.isNotBlank() } ?: "榜单加载失败"
            }
            if (req == requestId) _isLoading.value = false
        }
    }
}
