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

class GuessController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    private val favoritesProvider: () -> List<Song>,
    private val recentsProvider: () -> List<Song>,
) {

    private companion object {
        const val MAX_FETCH_SEEDS = 16

        const val SIMILAR_LIMIT = 20

        const val FETCH_CONCURRENCY = 4

        const val POOL_FALLBACK_THRESHOLD = 6

        const val POOL_SIZE = 60

        const val EMPTY_MESSAGE = "推荐获取失败，请稍后再试"
    }

    sealed interface State {
        data object Loading : State

        data class Empty(
            val title: String = "还没有足够的听歌线索",
            val subtitle: String = "收藏几首歌、多听几首，这里就会亮起来",
        ) : State

        data class Error(val message: String) : State

        data class Ready(
            val songs: List<Song>,
            val seedCount: Int,
            val fallbackLevel: Int,
            val failed: Int,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    @Volatile
    private var loaded = false

    @Volatile
    private var busy = false

    fun ensureLoaded() {
        if (loaded || busy) return
        load(shuffle = false)
    }

    fun refresh() = load(shuffle = true)

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

                val exclude = HashSet<String>(favorites.size + recents.size + 64)
                favorites.forEach { exclude += GuessRecommend.songKey(it) }
                recents.forEach { exclude += GuessRecommend.songKey(it) }
                exclude += GuessHistoryStore.load()
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

    private suspend fun fetchSimilar(seeds: List<GuessRecommend.Seed>): List<List<Song>> =
        coroutineScope {
            val gate = Semaphore(FETCH_CONCURRENCY)
            seeds.map { seed ->
                async(Dispatchers.IO) {
                    gate.withPermit {
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
