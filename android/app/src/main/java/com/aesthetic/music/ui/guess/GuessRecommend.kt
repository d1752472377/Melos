package com.aesthetic.music.ui.guess

import com.aesthetic.music.data.model.Song
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 「猜你喜欢」的推荐算法（纯逻辑，无 IO、无 Android 依赖，便于单测）。
 *
 * ## 这套逻辑从哪来
 * 不是拍脑袋写的，而是从 GZ Music 2.1.8 的 `GuessYouLikeRepo`
 * （`gz_decompiled/sources/defpackage/y33.java`，函数 `e(boolean)` / `a(...)` / `b(...)` / `c(...)`）
 * 逐个常量抠出来的。GZ 那边完整链路是：
 *
 * 1. **取两类种子**（`y33.java:306/326/667`）
 *    - 收藏 `y33Var.a.a`（收藏仓库的 flow）：权重 **3.0**、衰减半衰期 **30 天**（`y33.java:359`）
 *    - 最近播放 `no6Var.a`：先 `take(50)`，权重 **1.0**、半衰期 **7 天**（`y33.java:378`）
 *    - 两类各 `take(20)`（`z81.L0(list, 20)`）
 * 2. **按时间衰减**（`y33.java:177-182`）
 *    `days = (now - lastPlayedAt) / 86400000`；`weight *= 0.9^(days / halfLifeDays)`。
 *    注意底数是 **0.9** 而不是 0.5：GZ 用的是「每过一个半衰期乘 0.9」，衰减很温和，
 *    老收藏仍然算数，只是新收藏更容易排在前面。
 * 3. **筛掉不可播的种子**（`y33.java:165-172`）：平台要在可播集合里、歌曲 id 非空、
 *    网易云必须是**数字 id**（`kw7.F(...) == null` 即解析不出数字就跳过）。
 * 4. **按权重排序取前 40 个种子**（`y33.java:389/391`）。
 * 5. 用「相似歌曲」把每个种子扩展成一串候选（GZ 走 `d(...)`，本项目走网易云
 *    `discovery/simiSong`，见 `PlatformMusicService.similarSongs`）。
 * 6. **轮转挑选**（`y33.java:36-131` `a(...)`）：
 *    - 轮数 = `max(1, round(最大种子权重))`；第 `r` 轮里，只有「权重四舍五入 ≥ r+1」的种子才会出一首
 *      ——权重高的种子多出歌，权重低的少出歌，这就是「越常听越容易命中」的落点；
 *    - 每个种子内部按相似度顺序往下取，跳过：本批已选过的、排除集里的、同名的（同名最多 [MAX_PER_TITLE] 首）；
 *    - 总量上限 **30**（`y33.java:66` 的 `if (arrayList.size() >= 30) break`）。
 * 7. **保底降级**（`y33.java:618-638`）：结果不足 10 首时，放宽「同名上限」重挑一次；
 *    还不够就只按「收藏」做排除再挑一次，并记录 fallback 级别。
 * 8. **打乱**（`y33.java:127-129`）：`shuffle` 为真且结果 > 2 首时整体 shuffle——
 *    这就是「换一批」的随机性来源。
 *
 * 本项目没有收藏/播放的时间戳字段（`Song` 里没有 `lastPlayedAt`），因此时间衰减用
 * [com.aesthetic.music.data.settings.StatsStore] 的 `SongStat.lastAtMs` 兜底；
 * 拿不到时间戳的种子不做衰减（等价于「刚听过」），宁可保守也不乱罚。
 */
object GuessRecommend {

    /** 收藏种子的基础权重（`y33.java:359`） */
    const val FAVORITE_WEIGHT = 3.0

    /** 最近播放种子的基础权重（`y33.java:378`） */
    const val RECENT_WEIGHT = 1.0

    /** 收藏种子的衰减半衰期（天，`y33.java:359` 最后一个实参 30） */
    const val FAVORITE_HALF_LIFE_DAYS = 30.0

    /** 最近播放种子的衰减半衰期（天，`y33.java:378` 最后一个实参 7） */
    const val RECENT_HALF_LIFE_DAYS = 7.0

    /** 每类种子最多取多少（`y33.java:356/377` 的 `L0(list, 20)`） */
    const val SEEDS_PER_SOURCE = 20

    /** 排序后最多保留多少个种子去扩展（`y33.java:391` 的 `L0(arrayList, 40)`） */
    const val MAX_SEEDS = 40

    /** 一批最多出多少首（`y33.java:66`） */
    const val MAX_RESULT = 30

    /** 同名歌曲在一批里最多出现几次（`y33.java:617` 的 `a(..., 2, ...)`） */
    const val MAX_PER_TITLE = 2

    /** 结果少于这个数就降级重挑（`y33.java:618` 的 `if (size < 10)`） */
    const val MIN_RESULT = 10

    private const val DAY_MS = 86_400_000.0

    /** 一个种子：GZ `u33(platform, playbackId, weight)`（`u33.java:8-10`） */
    data class Seed(
        val platform: String,
        val songId: String,
        val weight: Double,
        /** 用于 UI/日志：种子歌名（GZ 的 Seed 不带标题，这里加上便于排查） */
        val title: String = "",
        /** 种子歌手（保底池按「同歌手」加分时用；GZ 的 Seed 不带，属于本项目的补充字段） */
        val artist: String = "",
    ) {
        val key: String get() = "$platform:$songId"
    }

    /** 一批推荐结果（GZ `t33(songs, seedCount, fallbackLevel, failed)`，`t33.java:9-15`） */
    data class Result(
        val songs: List<Song>,
        /** 参与扩展的种子数（首页那句「来自收藏与最近播放的 N 条线索」） */
        val seedCount: Int,
        /** 0 = 首选策略命中；1 = 放宽同名上限；2 = 只按收藏排除 */
        val fallbackLevel: Int,
        /** 相似歌曲没取到结果的种子数 */
        val failed: Int,
    )

    /**
     * 构造并排序种子。
     *
     * @param favorites     收藏列表（调用方保证「新的在前」，权重相同时顺序即优先级）
     * @param recents       最近播放列表（同上）
     * @param lastPlayedAt  `"platform:songId" -> 最后播放时间戳`，取不到的种子不衰减
     * @param nowMs         当前时间
     */
    fun buildSeeds(
        favorites: List<Song>,
        recents: List<Song>,
        lastPlayedAt: Map<String, Long>,
        nowMs: Long,
    ): List<Seed> {
        // 同一个 key 可能同时出现在收藏与最近播放里：保留更高权重（收藏 3.0 > 播放 1.0），
        // 与 GZ 的 LinkedHashSet 去重口径一致（先到先得 → 先处理收藏）
        val best = LinkedHashMap<String, Seed>()

        fun add(song: Song, baseWeight: Double, halfLifeDays: Double) {
            val platform = song.platform.trim().lowercase(Locale.ROOT)
            val songId = song.id.trim()
            if (platform.isEmpty() || songId.isEmpty()) return
            // 相似歌曲接口只认网易云数字 id，其它平台/非数字 id 的种子扩展必空 → 直接不收
            if (platform == "netease" && songId.any { !it.isDigit() }) return

            val key = "$platform:$songId"
            var weight = baseWeight
            val last = lastPlayedAt[key] ?: 0L
            if (last > 0L && nowMs > last) {
                val days = (nowMs - last) / DAY_MS
                if (days > 0.0) {
                    weight *= 0.9.pow(days / halfLifeDays) // y33.java:180
                }
            }
            val incoming = Seed(platform, songId, weight, song.title, song.artist)
            val existing = best[key]
            if (existing == null || existing.weight < weight) best[key] = incoming
        }

        favorites.take(SEEDS_PER_SOURCE).forEach { add(it, FAVORITE_WEIGHT, FAVORITE_HALF_LIFE_DAYS) }
        recents.take(SEEDS_PER_SOURCE).forEach { add(it, RECENT_WEIGHT, RECENT_HALF_LIFE_DAYS) }

        // 稳定降序：sortedByDescending 是稳定排序，同权重保留「收藏优先、各自列表顺序」
        return best.values
            .sortedByDescending { it.weight } // y33.java:389 d91.V(..., compareByDescending)
            .take(MAX_SEEDS)
    }

    /**
     * 轮转挑选（GZ `y33.a(...)` 的 Kotlin 版）。
     *
     * @param candidates 与 [seeds] 等长：第 i 项是第 i 个种子扩展出来的候选（按相似度排序）
     * @param exclude    排除集：`"platform:songId"`，通常是「收藏 + 最近播放 + 已推荐过」
     * @param maxPerTitle 同名上限；[Int.MAX_VALUE] 表示不限（GZ 的降级档）
     * @param shuffle    是否整体打乱（「换一批」）
     */
    fun pick(
        seeds: List<Seed>,
        candidates: List<List<Song>>,
        exclude: Set<String>,
        maxPerTitle: Int = MAX_PER_TITLE,
        shuffle: Boolean = false,
        limit: Int = MAX_RESULT,
    ): List<Song> {
        if (seeds.isEmpty() || candidates.isEmpty()) return emptyList()

        // y33.java:44-61 —— 轮数 = max(1, round(最大权重))
        val maxRounds = max(1, seeds.maxOf { it.weight }.roundToInt())
        // y33.java:75 —— 每个种子的「轮数上限」也是 max(1, round(weight))
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
                if (round >= roundsPerSeed[i]) continue // 该种子这轮不出歌

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
                    break // 每个种子每轮只出一首（y33.java:112 的 break）
                }
                cursors[i] = cursor
            }
            // 这一轮一个都没加上 → 后面几轮也不可能再加（候选都取完了/全被排除）
            if (!addedThisRound) break
            round++
        }

        // y33.java:127-129 —— 结果多于 2 首才打乱
        return if (shuffle && picked.size > 2) picked.shuffled() else picked
    }

    /**
     * 完整挑选：首选 → 放宽同名上限 → 只按收藏排除，三级降级（GZ `y33.java:617-638`）。
     *
     * @param favoritesOnlyExclude 只含收藏的排除集（降级档用，比全量排除宽松）
     */
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

    /**
     * 同名判定用的归一化（`y33.java:97-101`）：小写后抹掉空白与
     * `- _ · ． . 、 & , ， /` 这些分隔符，于是「起风了 (Live)」与「起风了」不会被误判同名，
     * 而「A - B」「A/B」「A·B」这类同一首歌的多种写法会被判成同名（从而受 [MAX_PER_TITLE] 约束）。
     */
    fun normalizeTitle(raw: String): String =
        raw.lowercase(Locale.ROOT).replace(TITLE_NOISE_REGEX, "")

    private val TITLE_NOISE_REGEX = Regex("[\\s\\-_·．.、&,，/]+")

    /**
     * 保底池挑选 —— **本项目新增，GZ 没有这一档**。
     *
     * 为什么需要：相似歌曲接口（`discovery/simiSong`）只认网易云数字 id。
     * 如果一个用户的收藏与最近播放全来自 QQ / 酷狗 / 酷我，[pick] 的候选池会是空的，
     * 页面直接变成「推荐获取失败」——这显然不是「猜你喜欢」该给人的第一印象。
     * 因此这里用一份通用新歌池（网易云「新歌速递」）兜底，并按「同歌手」优先排序：
     * 用户在 QQ 上收藏了某歌手的歌，这里仍能优先推同歌手的新歌（网易云平台上可播的版本）。
     *
     * 打分：候选的任一歌手 token 命中种子歌手集合 → +2；否则 0（稳定排序，保持接口原序）。
     */
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
        }.sortedByDescending { it.second } // 稳定排序：同分保留接口原序

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

    /** 把「A/B、A & B、A、B」这类多歌手串拆成小写 token 集合 */
    private fun artistTokens(raw: String): List<String> =
        raw.split('/', '&', ',', '，', '、', ';')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }

    /** 歌曲唯一键（与全工程 `platform:id` 口径一致） */
    fun songKey(song: Song): String =
        song.platform.trim().lowercase(Locale.ROOT) + ":" + song.id.trim()
}
