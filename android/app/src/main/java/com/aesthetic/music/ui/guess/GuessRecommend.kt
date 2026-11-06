package com.aesthetic.music.ui.guess

import com.aesthetic.music.data.model.Song
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

object GuessRecommend {

    const val FAVORITE_WEIGHT = 3.0

    const val RECENT_WEIGHT = 1.0

    const val FAVORITE_HALF_LIFE_DAYS = 30.0

    const val RECENT_HALF_LIFE_DAYS = 7.0

    const val SEEDS_PER_SOURCE = 20

    const val MAX_SEEDS = 40

    const val MAX_RESULT = 30

    const val MAX_PER_TITLE = 2

    const val MIN_RESULT = 10

    private const val DAY_MS = 86_400_000.0

    data class Seed(
        val platform: String,
        val songId: String,
        val weight: Double,
        val title: String = "",
        val artist: String = "",
    ) {
        val key: String get() = "$platform:$songId"
    }

    data class Result(
        val songs: List<Song>,
        val seedCount: Int,
        val fallbackLevel: Int,
        val failed: Int,
    )

    fun buildSeeds(
        favorites: List<Song>,
        recents: List<Song>,
        lastPlayedAt: Map<String, Long>,
        nowMs: Long,
    ): List<Seed> {
        val best = LinkedHashMap<String, Seed>()

        fun add(song: Song, baseWeight: Double, halfLifeDays: Double) {
            val platform = song.platform.trim().lowercase(Locale.ROOT)
            val songId = song.id.trim()
            if (platform.isEmpty() || songId.isEmpty()) return
            if (platform == "netease" && songId.any { !it.isDigit() }) return

            val key = "$platform:$songId"
            var weight = baseWeight
            val last = lastPlayedAt[key] ?: 0L
            if (last > 0L && nowMs > last) {
                val days = (nowMs - last) / DAY_MS
                if (days > 0.0) {
                    weight *= 0.9.pow(days / halfLifeDays)
                }
            }
            val incoming = Seed(platform, songId, weight, song.title, song.artist)
            val existing = best[key]
            if (existing == null || existing.weight < weight) best[key] = incoming
        }

        favorites.take(SEEDS_PER_SOURCE).forEach { add(it, FAVORITE_WEIGHT, FAVORITE_HALF_LIFE_DAYS) }
        recents.take(SEEDS_PER_SOURCE).forEach { add(it, RECENT_WEIGHT, RECENT_HALF_LIFE_DAYS) }

        return best.values
            .sortedByDescending { it.weight }
            .take(MAX_SEEDS)
    }

    fun pick(
        seeds: List<Seed>,
        candidates: List<List<Song>>,
        exclude: Set<String>,
        maxPerTitle: Int = MAX_PER_TITLE,
        shuffle: Boolean = false,
        limit: Int = MAX_RESULT,
    ): List<Song> {
        if (seeds.isEmpty() || candidates.isEmpty()) return emptyList()

        val maxRounds = max(1, seeds.maxOf { it.weight }.roundToInt())
        val roundsPerSeed = seeds.map { max(1, it.weight.roundToInt()) }

        val picked = ArrayList<Song>(limit)
        val pickedKeys = HashSet<String>()
        val titleCount = HashMap<String, Int>()
        val cursors = IntArray(seeds.size)

        var round = 0
        while (round < maxRounds && picked.size < limit) {
            var addedThisRound = false
            for (i in seeds.indices) {
                if (picked.size >= limit) break
                if (round >= roundsPerSeed[i]) continue

                val list = candidates.getOrElse(i) { emptyList() }
                var cursor = cursors[i]
                while (cursor < list.size) {
                    val song = list[cursor]
                    cursor++
                    val platform = song.platform.trim().lowercase(Locale.ROOT)
                    val id = song.id.trim()
                    if (platform.isEmpty() || id.isEmpty()) continue
                    val key = "$platform:$id"
                    if (key in exclude || key in pickedKeys) continue

                    val normTitle = normalizeTitle(song.title)
                    val used = titleCount[normTitle] ?: 0
                    if (normTitle.isNotEmpty() && used >= maxPerTitle) continue

                    pickedKeys += key
                    if (normTitle.isNotEmpty()) titleCount[normTitle] = used + 1
                    picked += song
                    addedThisRound = true
                    break
                }
                cursors[i] = cursor
            }
            if (!addedThisRound) break
            round++
        }

        return if (shuffle && picked.size > 2) picked.shuffled() else picked
    }

    fun pickWithFallback(
        seeds: List<Seed>,
        candidates: List<List<Song>>,
        exclude: Set<String>,
        favoritesOnlyExclude: Set<String>,
        shuffle: Boolean,
    ): Pair<List<Song>, Int> {
        var songs = pick(seeds, candidates, exclude, MAX_PER_TITLE, shuffle)
        var level = 0
        if (songs.size < MIN_RESULT) {
            val wider = pick(seeds, candidates, exclude, Int.MAX_VALUE, shuffle)
            if (wider.size > songs.size) {
                songs = wider
                level = 1
            }
        }
        if (songs.size < MIN_RESULT) {
            val loose = pick(seeds, candidates, favoritesOnlyExclude, Int.MAX_VALUE, shuffle)
            if (loose.size > songs.size) {
                songs = loose
                level = 2
            }
        }
        return songs to level
    }

    fun normalizeTitle(raw: String): String =
        raw.lowercase(Locale.ROOT).replace(TITLE_NOISE_REGEX, "")

    private val TITLE_NOISE_REGEX = Regex("[\\s\\-_·．.、&,，/]+")

    fun pickFromPool(
        pool: List<Song>,
        seeds: List<Seed>,
        exclude: Set<String>,
        limit: Int = MAX_RESULT,
        maxPerTitle: Int = MAX_PER_TITLE,
    ): List<Song> {
        if (pool.isEmpty()) return emptyList()
        val seedArtists = seeds
            .flatMap { artistTokens(it.artist) }
            .toHashSet()

        val scored = pool.map { song ->
            val hit = artistTokens(song.artist).any { it in seedArtists }
            song to if (hit) 2 else 0
        }.sortedByDescending { it.second }

        val picked = ArrayList<Song>(limit)
        val seenKeys = HashSet<String>()
        val titleCount = HashMap<String, Int>()
        for ((song, _) in scored) {
            if (picked.size >= limit) break
            val key = songKey(song)
            if (key == ":" || key in exclude || !seenKeys.add(key)) continue
            val normTitle = normalizeTitle(song.title)
            val used = titleCount[normTitle] ?: 0
            if (normTitle.isNotEmpty() && used >= maxPerTitle) continue
            if (normTitle.isNotEmpty()) titleCount[normTitle] = used + 1
            picked += song
        }
        return picked
    }

    private fun artistTokens(raw: String): List<String> =
        raw.split('/', '&', ',', '，', '、', ';')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }

    fun songKey(song: Song): String =
        song.platform.trim().lowercase(Locale.ROOT) + ":" + song.id.trim()
}
