package com.aesthetic.music.ui.guess

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.GuessHistoryStore
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.service.LogReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 「猜你喜欢」页的状态与编排（对应 GZ `defpackage/x43.java` 的 GuessViewModel +
 * `y33.java` 的 GuessYouLikeRepo 的合体）。
 *
 * 分工：
 *  - 算法本身（种子权重、轮转挑选、降级）在 [GuessRecommend]，纯函数、可单测；
 *  - 这里只负责「取数据 → 调算法 → 落状态」，以及网络并发控制与结果记忆。
 *
 * ## 为什么种子要现场取、不缓存
 * 收藏/最近播放随时会变（用户在这页听两首、回首页收藏一首），而 GZ 每次 `refresh()`
 * 都重新读一遍仓库（`y33.java:306/326` 的 `wi0.I(...)` 取 flow 首值）。
 * 这里同样每次重新取，代价只是一次内存读取。
 *
 * ## 网络量
 * 每个种子一次「相似歌曲」请求（`discovery/simiSong`），GZ 的种子上限是 40。
 * 40 个并发请求对手机弱网不友好，因此：
 *  - 只取权重最高的 [MAX_FETCH_SEEDS] 个种子去扩展（其余仅计入「线索数」）；
 *  - 并发用 [FETCH_CONCURRENCY] 个信号量卡住，避免瞬时 20+ 个连接把 App 的
 *    OkHttp 连接池打满（那会让「播放」这类关键请求排队）。
 */
class GuessController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    private val favoritesProvider: () -> List<Song>,
    private val recentsProvider: () -> List<Song>,
) {

    private companion object {
        /** 参与「相似歌曲」扩展的种子数上限（GZ 是 40；这里 16 是首屏耗时与覆盖度的折中） */
        const val MAX_FETCH_SEEDS = 16

        /** 每个种子取多少首相似歌（GZ 未给上限，实测 20 首足够凑满 30 首结果） */
        const val SIMILAR_LIMIT = 20

        /** 同时最多几个相似歌曲请求 */
        const val FETCH_CONCURRENCY = 4

        /** 结果少于这个数就启用「通用新歌池」兜底（GZ 的降级阈值是 10，这里取 6 更保守） */
        const val POOL_FALLBACK_THRESHOLD = 6

        /** 兜底池一次取多少首 */
        const val POOL_SIZE = 60

        /** 结果为空时给用户看的文案 */
        const val EMPTY_MESSAGE = "推荐获取失败，请稍后再试"
    }

    /** 页面状态：Loading / Empty / Error / Ready 四态合一，避免 UI 侧再拼一堆布尔量 */
    sealed interface State {
        /** 首次进入、还没有任何结果 */
        data object Loading : State

        /** 种子里连一首可扩展的都没有（新用户：没收藏也没听过） */
        data class Empty(
            val title: String = "还没有足够的听歌线索",
            val subtitle: String = "收藏几首歌、多听几首，这里就会亮起来",
        ) : State

        data class Error(val message: String) : State

        data class Ready(
            val songs: List<Song>,
            /** 参与本次推荐的线索条数（= 种子数），Hero 上「来自收藏与最近播放的 N 条线索」 */
            val seedCount: Int,
            /** 0/1/2 降级级别，仅用于调试展示 */
            val fallbackLevel: Int,
            /** 相似歌曲取空的种子数，仅用于调试展示 */
            val failed: Int,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 「换一批」进行中：刷新按钮据此转圈并禁用重复点击 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    @Volatile
    private var loaded = false

    @Volatile
    private var busy = false

    /**
     * 进入页面时调用（幂等）：已出过结果就不再联网。
     * 需要换一批用 [refresh]。
     */
    fun ensureLoaded() {
        if (loaded || busy) return
        load(shuffle = false)
    }

    /**
     * 「换一批」：强制重算，并带上 [shuffle]（GZ 的 `refresh(boolean)` → `y33.e(z)`）。
     * 已推荐过的歌会被 [GuessHistoryStore] 排除，所以连点几次看到的确实是不同的一批。
     */
    fun refresh() = load(shuffle = true)

    /** 推荐失败后的「重试」：不清记忆，直接重算一次 */
    fun retry() = load(shuffle = false)

    private fun load(shuffle: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            _refreshing.value = true
            if (_state.value !is State.Ready) _state.value = State.Loading
            try {
                val favorites = favoritesProvider()
                val recents = recentsProvider()
                // 时间衰减用的最后播放时间：StatsStore 的明细里带 lastAtMs（Song 本身没有时间戳）
                val lastPlayedAt = withContext(Dispatchers.IO) {
                    runCatching {
                        StatsStore.snapshot().topSongs.associate { it.key to it.lastAtMs }
                    }.getOrDefault(emptyMap())
                }
                val seeds = GuessRecommend.buildSeeds(
                    favorites = favorites,
                    recents = recents,
                    lastPlayedAt = lastPlayedAt,
                    nowMs = System.currentTimeMillis(),
                )
                if (seeds.isEmpty()) {
                    _state.value = State.Empty()
                    loaded = true
                    return@launch
                }

                // 排除集：收藏 + 最近播放 + 上一批已推荐（GZ 的 yc2VarB / mp6Var.b()）
                val exclude = HashSet<String>(favorites.size + recents.size + 64)
                favorites.forEach { exclude += GuessRecommend.songKey(it) }
                recents.forEach { exclude += GuessRecommend.songKey(it) }
                exclude += GuessHistoryStore.load()
                // 降级档用的宽松排除集：只排除收藏（GZ 的 yc2VarB2 = b(favorites, emptyList())）
                val favoritesOnly = favorites.mapTo(HashSet()) { GuessRecommend.songKey(it) }

                val toFetch = seeds.take(MAX_FETCH_SEEDS)
                val candidates = fetchSimilar(toFetch)
                val failed = candidates.count { it.isEmpty() }

                var (songs, level) = GuessRecommend.pickWithFallback(
                    seeds = toFetch,
                    candidates = candidates,
                    exclude = exclude,
                    favoritesOnlyExclude = favoritesOnly,
                    shuffle = shuffle,
                )

                // 相似歌曲一首都没凑够（典型场景：收藏全来自 QQ/酷狗/酷我，
                // 而相似歌曲接口只认网易云数字 id）→ 用通用新歌池兜底，按「同歌手」优先
                if (songs.size < POOL_FALLBACK_THRESHOLD) {
                    val pool = repository.getNewSongs(POOL_SIZE).getOrNull()?.songs.orEmpty()
                    val fromPool = GuessRecommend.pickFromPool(
                        pool = pool,
                        seeds = seeds,
                        exclude = exclude,
                    )
                    if (fromPool.size > songs.size) {
                        songs = fromPool
                        level = 3
                    }
                }

                if (songs.isEmpty()) {
                    _state.value = State.Error(EMPTY_MESSAGE)
                    return@launch
                }
                GuessHistoryStore.record(songs.map { GuessRecommend.songKey(it) })
                _state.value = State.Ready(
                    songs = songs,
                    seedCount = seeds.size,
                    fallbackLevel = level,
                    failed = failed,
                )
                loaded = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogReporter.w("Guess", "猜你喜欢生成失败", e)
                _state.value = State.Error(e.message ?: EMPTY_MESSAGE)
            } finally {
                _refreshing.value = false
                busy = false
            }
        }
    }

    /**
     * 并行拉每个种子的相似歌曲，顺序与 [seeds] 一一对应。
     *
     * 单个种子失败**不拖垮整批**：`runCatching` 兜住后返回空列表，最终由
     * [GuessRecommend.pickWithFallback] 用别的种子凑（GZ 是整批失败直接报错，
     * 手机弱网下那样太脆——10 个种子里挂 2 个不该整页白掉）。
     */
    private suspend fun fetchSimilar(seeds: List<GuessRecommend.Seed>): List<List<Song>> =
        coroutineScope {
            val gate = Semaphore(FETCH_CONCURRENCY)
            seeds.map { seed ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        // 只有网易云数字 id 才有相似歌曲，其它平台直接空（不计请求）
                        if (seed.platform != "netease") {
                            emptyList()
                        } else {
                            repository.getSimilarSongs(seed.songId, SIMILAR_LIMIT)
                                .getOrDefault(emptyList())
                        }
                    }
                }
            }.awaitAll()
        }
}
