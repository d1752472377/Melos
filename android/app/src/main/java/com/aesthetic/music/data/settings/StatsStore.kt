package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.aesthetic.music.data.api.SyncPlayEventItem
import com.aesthetic.music.data.model.Song
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 单首歌的听歌累计。
 *
 * @param lastAtMs 最后一次播放的墙上时间（单日日记里"最后"的时间点）
 * @param lastPositionMs 最后一次播放到的曲内位置（展示成 00:49 这样的播放时间点）
 */
data class SongStat(
    val platform: String,
    val id: String,
    val title: String,
    val artist: String,
    val plays: Int,
    val listenMs: Long,
    val lastAtMs: Long,
    val lastPositionMs: Long,
) {
    val key: String get() = "$platform:$id"

    /** 展示用歌名/歌手（历史数据可能缺字段） */
    val displayTitle: String get() = title.ifBlank { "未知歌曲" }
    val displayArtist: String get() = artist.ifBlank { "未知歌手" }
}

/**
 * 单日统计。
 *
 * @param hourMs 24 个整点的收听见长；空列表表示该天没有保留时段明细（老数据/超出保留窗口）
 * @param songs 当天歌曲明细（内存中不截断，落盘时只保留前 [StatsStore.MAX_SONGS_PER_DAY] 首）
 */
data class DayStat(
    val day: String,
    val listenMs: Long = 0,
    val plays: Int = 0,
    val hourMs: List<Long> = emptyList(),
    val songs: List<SongStat> = emptyList(),
) {
    val isActive: Boolean get() = listenMs > 0 || plays > 0
}

/** 全量听歌统计（内存模型；[StatsStore] 负责落盘与保留窗口裁剪） */
data class ListeningStats(
    val totalListenMs: Long = 0,
    val totalPlays: Long = 0,
    /** 最早有记录的一天（yyyy-MM-dd），空串表示还没有任何记录 */
    val firstDay: String = "",
    val lastAtMs: Long = 0,
    /** 按天升序 */
    val days: List<DayStat> = emptyList(),
    /** 全期歌曲排行（按播放次数降序） */
    val topSongs: List<SongStat> = emptyList(),
) {
    val isEmpty: Boolean get() = totalListenMs <= 0 && totalPlays <= 0 && days.isEmpty()

    fun day(day: String): DayStat? = days.firstOrNull { it.day == day }
}

/** 旧版扁平统计（保留：ProfileScreen 等处仍在用） */
data class LocalStats(
    val totalPlayMs: Long = 0,
    val totalPlays: Long = 0,
    val dayPlayMs: Long = 0,
    val day: String = "",
)

/**
 * 听歌统计存储（本地真实数据，未登录也记录）。
 *
 * 与旧实现的区别：从"只有 4 个总数"升级为**可下钻的多维明细**——
 * 按天（时长/次数/24 小时时段）、按歌曲（次数/时长/最后播放点）、全期排行。
 * 界面上的 30 天趋势图、时段分布、单日日记、歌曲排行全部来自这一份数据，不再是画出来的假柱子。
 *
 * 三个工程决策（都直接影响可用性，注释在此便于后续维护）：
 * 1. **手写 JSON 而非 Gson 反射**：Gson 反序列化 Kotlin data class 会绕过构造器，
 *    JSON 缺字段就留 null（项目里已有前车之鉴，见 `AppJson.kt` / PlaylistStore 的注释）。
 *    这里用 JsonObject 逐字段读取并回落默认值，脏数据只会让某条明细被丢弃，不会 NPE。
 * 2. **保留窗口**：统计 JSON 每次都是整份重写，无上限增长会把首选项文件撑成几百 KB。
 *    逐日总量留 [MAX_DAYS] 天、小时明细留 [MAX_HOUR_DAYS] 天、单日歌曲明细留
 *    [MAX_SONG_DAYS] 天、全期排行只留前 [MAX_TOP_SONGS] 首；
 *    但**内存中不截断**，当天新出现的歌不会因为"暂时排不进前 8"而丢掉计数。
 * 3. **落盘节流**：播放中每 500ms 上报一次增量，若每次都写盘等于持续擦写闪存。
 *    内存累加 + 最多每 [FLUSH_INTERVAL_MS] 写一次，并在暂停/切歌/退出时立即落盘
 *    （[flush] 已由 StatsTracker.flushNow 在这些时机调用）。
 *    序列化 30KB 级 JSON 约 1ms，配合节流不会影响播放流畅度；`apply()` 本身异步写盘。
 */
object StatsStore {

    private const val TAG = "StatsStore"
    private const val PREFS = "aesthetic_music_stats"
    /** 旧版扁平统计的键（迁移来源；迁移后保留不删，便于回滚旧版本） */
    private const val KEY_LEGACY = "stats"
    private const val KEY_DETAIL = "stats_detail_v2"

    private const val FORMAT_VERSION = 2

    /** 逐日总量保留天数 */
    private const val MAX_DAYS = 180

    /** 小时时段明细保留天数（时段分布按这个窗口统计，界面文案与之一致） */
    private const val MAX_HOUR_DAYS = 31

    /** 单日歌曲明细保留天数（单日日记的数据源） */
    private const val MAX_SONG_DAYS = 21

    /** 每日歌曲明细落盘条数上限 */
    private const val MAX_SONGS_PER_DAY = 8

    /** 全期排行落盘条数上限 */
    private const val MAX_TOP_SONGS = 60

    /** 落盘最小间隔 */
    private const val FLUSH_INTERVAL_MS = 30_000L

    const val HOURS_PER_DAY = 24

    /** 时段分布统计窗口（天），界面文案用它 |
     *  注：`|` 在 KDoc 里会被当表格分隔符，这里用普通注释块说明 */
    const val HOUR_WINDOW_DAYS = MAX_HOUR_DAYS

    /** 单日歌曲明细保留窗口（天） */
    const val DAY_SONG_WINDOW_DAYS = MAX_SONG_DAYS

    private var prefs: SharedPreferences? = null

    /** 内存模型：所有读写都在锁内（主线程进度轮询 + IO 线程读界面数据） */
    private val lock = Any()
    private var cache: ListeningStats? = null

    /** 上次落盘时间（节流用） */
    @Volatile
    private var lastFlushAt = 0L

    /** 「记录听歌数据」开关的短缓存：进度循环每 500ms 都要判断一次，不必每次都读首选项 */
    @Volatile
    private var recordingCached = true

    @Volatile
    private var recordingCachedAt = 0L

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    // ==================== 读取 ====================

    /** 界面用的全量数据（首次调用解析一次，之后走内存） */
    fun snapshot(): ListeningStats = synchronized(lock) { loadLocked() }

    /** 兼容旧接口：概要数据（ProfileScreen / MainViewModel 用） */
    fun load(): LocalStats {
        val stats = snapshot()
        val today = todayKey()
        return LocalStats(
            totalPlayMs = stats.totalListenMs,
            totalPlays = stats.totalPlays,
            dayPlayMs = stats.day(today)?.listenMs ?: 0L,
            day = today,
        )
    }

    // ==================== 写入 ====================

    /**
     * 一首歌真正开始播放：当天与全期次数 +1，并更新最后播放时间。
     * 关闭「记录听歌数据」时什么都不做。
     */
    fun recordPlay(song: Song) {
        if (!recordingEnabled()) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val stats = loadLocked()
            val today = todayKey()
            val day = stats.day(today)
            val songStat = bumpSong(
                existing = day?.songs?.firstOrNull { it.key == songKey(song) },
                song = song,
                playDelta = 1,
                listenDelta = 0L,
                positionMs = 0L,
                now = now,
            )
            val updatedDay = (day ?: DayStat(day = today)).copy(
                plays = (day?.plays ?: 0) + 1,
                songs = putSong(day?.songs ?: emptyList(), songStat),
            )
            cache = stats.copy(
                totalPlays = stats.totalPlays + 1,
                firstDay = if (stats.firstDay.isBlank()) today else stats.firstDay,
                lastAtMs = now,
                days = putDay(stats.days, updatedDay),
                topSongs = bumpGlobal(stats.topSongs, songStat, playDelta = 1, listenDelta = 0L),
            )
            maybeFlushLocked()
        }
    }

    /** 累计收听增量（进度轮询每 500ms 一次）：按当前时刻归入当天与对应小时段 */
    fun recordListen(song: Song, deltaMs: Long, positionMs: Long) {
        if (deltaMs <= 0) return
        if (!recordingEnabled()) return
        val now = System.currentTimeMillis()
        val hour = hourOf(now)
        synchronized(lock) {
            val stats = loadLocked()
            val today = todayKey()
            val day = stats.day(today)
            val songStat = bumpSong(
                existing = day?.songs?.firstOrNull { it.key == songKey(song) },
                song = song,
                playDelta = 0,
                listenDelta = deltaMs,
                positionMs = positionMs,
                now = now,
            )
            val hourMs = (if (day?.hourMs?.size == HOURS_PER_DAY) day.hourMs else emptyHourMs())
                .toMutableList()
                .also { it[hour] = it[hour] + deltaMs }
            val updatedDay = (day ?: DayStat(day = today)).copy(
                listenMs = (day?.listenMs ?: 0L) + deltaMs,
                hourMs = hourMs,
                songs = putSong(day?.songs ?: emptyList(), songStat),
            )
            cache = stats.copy(
                totalListenMs = stats.totalListenMs + deltaMs,
                firstDay = if (stats.firstDay.isBlank()) today else stats.firstDay,
                lastAtMs = now,
                days = putDay(stats.days, updatedDay),
                topSongs = bumpGlobal(stats.topSongs, songStat, playDelta = 0, listenDelta = deltaMs),
            )
            maybeFlushLocked()
        }
    }

    private const val KEY_INGESTED_EVENTS = "ingested_cloud_events"
    private const val MAX_INGESTED_TRACKED = 2000

    /**
     * 吸收云端拉取到的其它设备播放事件（增量合并入本地全量明细与日记）。
     *
     * 幂等防重：
     * 1. 忽略本设备自身上报的事件（[SyncPlayEventItem.deviceId] == 本地设备 ID）；
     * 2. 本地记录已处理过的 [SyncPlayEventItem.eventId]，避免重复累加。
     */
    fun ingestCloudEvents(events: List<SyncPlayEventItem>) {
        if (events.isEmpty()) return
        synchronized(lock) {
            val p = prefs
            val ingestedSet = p?.getStringSet(KEY_INGESTED_EVENTS, emptySet())?.toMutableSet() ?: mutableSetOf()
            val myDevice = com.aesthetic.music.data.sync.SyncStateStore.deviceId()

            val toIngest = events.filter {
                it.eventId.isNotBlank() &&
                it.eventId !in ingestedSet &&
                (it.deviceId.isBlank() || it.deviceId != myDevice) &&
                it.listenedMs > 0
            }
            if (toIngest.isEmpty()) return

            var stats = loadLocked()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            for (e in toIngest) {
                val timeMs = if (e.listenedAtMs > 0) e.listenedAtMs else e.updatedAtMs
                val dayStr = if (timeMs > 0) dateFormat.format(Date(timeMs)) else todayKey()
                val hour = if (timeMs > 0) hourOf(timeMs) else 0
                val song = Song(
                    platform = e.platform,
                    id = e.songId,
                    title = e.title,
                    artist = e.artist,
                )

                val day = stats.day(dayStr)
                val songStat = bumpSong(
                    existing = day?.songs?.firstOrNull { it.key == songKey(song) },
                    song = song,
                    playDelta = 1,
                    listenDelta = e.listenedMs,
                    positionMs = 0L,
                    now = timeMs,
                )
                val hourMs = (if (day?.hourMs?.size == HOURS_PER_DAY) day.hourMs else emptyHourMs())
                    .toMutableList()
                    .also { it[hour] = it[hour] + e.listenedMs }

                val updatedDay = (day ?: DayStat(day = dayStr)).copy(
                    listenMs = (day?.listenMs ?: 0L) + e.listenedMs,
                    plays = (day?.plays ?: 0) + 1,
                    hourMs = hourMs,
                    songs = putSong(day?.songs ?: emptyList(), songStat),
                )

                stats = stats.copy(
                    totalListenMs = stats.totalListenMs + e.listenedMs,
                    totalPlays = stats.totalPlays + 1,
                    firstDay = if (stats.firstDay.isBlank() || (dayStr.isNotBlank() && dayStr < stats.firstDay)) dayStr else stats.firstDay,
                    lastAtMs = maxOf(stats.lastAtMs, timeMs),
                    days = putDay(stats.days, updatedDay),
                    topSongs = bumpGlobal(stats.topSongs, songStat, playDelta = 1, listenDelta = e.listenedMs),
                )
                ingestedSet.add(e.eventId)
            }

            val trimmedSet = if (ingestedSet.size > MAX_INGESTED_TRACKED) {
                ingestedSet.toList().takeLast(MAX_INGESTED_TRACKED).toSet()
            } else {
                ingestedSet
            }
            p?.edit()?.putStringSet(KEY_INGESTED_EVENTS, trimmedSet)?.apply()

            cache = stats
            lastFlushAt = System.currentTimeMillis()
            persist(serialize(stats))
        }
    }

    /** 立即落盘（暂停/切歌/退出时调用） */
    fun flush() {
        val json = synchronized(lock) {
            val stats = cache ?: return
            lastFlushAt = System.currentTimeMillis()
            serialize(stats)
        }
        persist(json)
    }

    /** 清空全部统计 */
    fun clearAll() {
        val json = synchronized(lock) {
            cache = ListeningStats()
            lastFlushAt = System.currentTimeMillis()
            serialize(ListeningStats())
        }
        persist(json)
    }

    /** 设置页改了「记录听歌数据」开关时调用，让新值立即生效（否则最多滞后 2 秒） */
    fun invalidateRecordingCache() {
        recordingCachedAt = 0L
    }

    // ==================== 内部：模型维护 ====================

    private fun recordingEnabled(): Boolean {
        val now = System.currentTimeMillis()
        if (now - recordingCachedAt < 2_000) return recordingCached
        recordingCached = try {
            SettingsStore.readPrefs().statsRecording
        } catch (_: Exception) {
            true
        }
        recordingCachedAt = now
        return recordingCached
    }

    private fun songKey(song: Song): String = "${song.platform}:${song.id}"

    /** 在给定基础上叠加一次播放/收听，返回该歌在当前天的新条目 */
    private fun bumpSong(
        existing: SongStat?,
        song: Song,
        playDelta: Int,
        listenDelta: Long,
        positionMs: Long,
        now: Long,
    ): SongStat = SongStat(
        platform = song.platform,
        id = song.id,
        // 歌名/歌手以最新一次播放为准（被更正过时能跟上），空值则沿用旧记录
        title = song.title.ifBlank { existing?.title.orEmpty() },
        artist = song.artist.ifBlank { existing?.artist.orEmpty() },
        plays = (existing?.plays ?: 0) + playDelta,
        listenMs = (existing?.listenMs ?: 0L) + listenDelta,
        lastAtMs = now,
        lastPositionMs = if (positionMs > 0) positionMs else (existing?.lastPositionMs ?: 0L),
    )

    /** 把某天的歌曲条目替换/插入（同 key 覆盖，不累加：day 条目本身已是当天累计值） */
    private fun putSong(list: List<SongStat>, incoming: SongStat): List<SongStat> {
        val idx = list.indexOfFirst { it.key == incoming.key }
        val out = if (idx < 0) list + incoming else list.toMutableList().also { it[idx] = incoming }
        return out.sortedWith(SONG_ORDER)
    }

    /**
     * 全期排行按**增量**累加。
     *
     * 关键点：day 条目是"当天累计"，而全期条目是"历史累计"，
     * 直接相加会把当天更早的收听重复计数，所以这里显式用本次 delta。
     * 首次出现（全期列表里没有）时，day 条目的值就是本次增量（含当天更早的部分），可直接入列。
     */
    private fun bumpGlobal(
        list: List<SongStat>,
        dayStat: SongStat,
        playDelta: Int,
        listenDelta: Long,
    ): List<SongStat> {
        val idx = list.indexOfFirst { it.key == dayStat.key }
        val out = if (idx < 0) {
            list + dayStat.copy(plays = playDelta.coerceAtLeast(0), listenMs = listenDelta.coerceAtLeast(0))
        } else {
            val old = list[idx]
            list.toMutableList().also {
                it[idx] = old.copy(
                    title = dayStat.title,
                    artist = dayStat.artist,
                    plays = old.plays + playDelta,
                    listenMs = old.listenMs + listenDelta,
                    lastAtMs = dayStat.lastAtMs,
                    lastPositionMs = dayStat.lastPositionMs,
                )
            }
        }
        return out.sortedWith(SONG_ORDER)
    }

    private fun putDay(days: List<DayStat>, updated: DayStat): List<DayStat> {
        val idx = days.indexOfFirst { it.day == updated.day }
        val list = if (idx < 0) days + updated else days.toMutableList().also { it[idx] = updated }
        return list.sortedBy { it.day }
    }

    private fun maybeFlushLocked() {
        val now = System.currentTimeMillis()
        if (now - lastFlushAt < FLUSH_INTERVAL_MS) return
        lastFlushAt = now
        persist(serialize(cache ?: return))
    }

    private fun persist(json: String) {
        try {
            prefs?.edit()?.putString(KEY_DETAIL, json)?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "听歌统计落盘失败: ${e.message}")
        }
    }

    // ==================== 内部：序列化 ====================

    /**
     * 手写 JSON 结构：
     * ```
     * {
     *   "v":2, "totalMs":.., "totalPlays":.., "firstDay":"..", "lastAtMs":..,
     *   "days":[{"d":"2026-09-13","ms":123,"n":4}],
     *   "hours":[{"d":"2026-09-13","h":[24 个数]}],
     *   "daySongs":[{"d":"2026-09-13","s":[songObj,..]}],
     *   "songs":[songObj,..]
     * }
     * songObj = {"p":平台,"i":id,"t":歌名,"a":歌手,"n":次数,"ms":时长,"at":最后时间,"ps":最后位置}
     * ```
     */
    private fun serialize(stats: ListeningStats): String {
        val trimmed = prune(stats)
        val root = JsonObject()
        root.addProperty("v", FORMAT_VERSION)
        root.addProperty("totalMs", trimmed.totalListenMs)
        root.addProperty("totalPlays", trimmed.totalPlays)
        root.addProperty("firstDay", trimmed.firstDay)
        root.addProperty("lastAtMs", trimmed.lastAtMs)

        val days = JsonArray()
        val hours = JsonArray()
        val daySongs = JsonArray()
        for (d in trimmed.days) {
            days.add(JsonObject().apply {
                addProperty("d", d.day)
                addProperty("ms", d.listenMs)
                addProperty("n", d.plays)
            })
            if (d.hourMs.size == HOURS_PER_DAY) {
                hours.add(JsonObject().apply {
                    addProperty("d", d.day)
                    add("h", JsonArray().apply { d.hourMs.forEach { add(it) } })
                })
            }
            if (d.songs.isNotEmpty()) {
                daySongs.add(JsonObject().apply {
                    addProperty("d", d.day)
                    add("s", JsonArray().apply { d.songs.forEach { add(songJson(it)) } })
                })
            }
        }
        root.add("days", days)
        root.add("hours", hours)
        root.add("daySongs", daySongs)
        root.add("songs", JsonArray().apply { trimmed.topSongs.forEach { add(songJson(it)) } })

        return root.toString()
    }

    private fun songJson(s: SongStat): JsonObject = JsonObject().apply {
        addProperty("p", s.platform)
        addProperty("i", s.id)
        addProperty("t", s.title)
        addProperty("a", s.artist)
        addProperty("n", s.plays)
        addProperty("ms", s.listenMs)
        addProperty("at", s.lastAtMs)
        addProperty("ps", s.lastPositionMs)
    }

    /**
     * 读取。
     *
     * 优先读 v2 明细；没有明细时用旧版扁平统计迁移（老用户升级后累计时长/次数不丢，
     * 但"逐日历史"无法还原，如实留空而不是编造）。
     * 任何异常都返回空数据，绝不让统计把页面带崩。
     */
    private fun loadLocked(): ListeningStats {
        cache?.let { return it }
        val p = prefs ?: return ListeningStats()
        val parsed = try {
            val raw = p.getString(KEY_DETAIL, null)
            if (raw.isNullOrBlank()) migrateLegacy(p) else parseDetail(raw)
        } catch (e: Exception) {
            Log.w(TAG, "听歌统计解析失败，按空数据继续: ${e.message}")
            ListeningStats()
        }
        cache = parsed
        return parsed
    }

    private fun migrateLegacy(p: SharedPreferences): ListeningStats =
        migrateLegacyJson(try { p.getString(KEY_LEGACY, null) } catch (_: Exception) { null }, todayKey())

    /**
     * 旧版扁平统计 → 新版明细的迁移。
     *
     * 独立成纯函数（不碰 SharedPreferences）是为了能在 JVM 单测里直接覆盖：
     * 迁移只在"老用户第一次升级"时跑一次，写错了没人会发现。
     */
    internal fun migrateLegacyJson(raw: String?, today: String): ListeningStats {
        val o = try {
            if (raw.isNullOrBlank()) return ListeningStats()
            JsonParser.parseString(raw).asJsonObject
        } catch (_: Exception) {
            return ListeningStats()
        }
        val totalMs = o.optLongOrZero("totalPlayMs")
        val totalPlays = o.optLongOrZero("totalPlays")
        val legacyDay = o.optStringOrEmpty("day")
        val legacyDayMs = o.optLongOrZero("dayPlayMs")
        if (totalMs <= 0 && totalPlays <= 0) return ListeningStats()
        val day = when {
            legacyDay.isBlank() -> today
            legacyDay > today -> today // 设备时钟被往回调过：不留"未来的日期"
            else -> legacyDay
        }
        val days = if (legacyDayMs > 0) listOf(DayStat(day = day, listenMs = legacyDayMs)) else emptyList()
        return ListeningStats(
            totalListenMs = totalMs,
            totalPlays = totalPlays,
            firstDay = days.firstOrNull()?.day ?: "",
            lastAtMs = 0L,
            days = days,
            topSongs = emptyList(),
        )
    }

    private fun parseDetail(raw: String): ListeningStats {
        val root = JsonParser.parseString(raw).asJsonObject

        val hourByDay = HashMap<String, List<Long>>()
        root.optJsonArray("hours")?.forEach { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val d = o.optStringOrEmpty("d")
            val arr = o.optJsonArray("h") ?: return@forEach
            if (d.isNotBlank()) hourByDay[d] = List(HOURS_PER_DAY) { i -> arr.optLongAt(i) }
        }

        val songsByDay = HashMap<String, List<SongStat>>()
        root.optJsonArray("daySongs")?.forEach { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val d = o.optStringOrEmpty("d")
            val arr = o.optJsonArray("s") ?: return@forEach
            val list = parseSongs(arr)
            if (d.isNotBlank() && list.isNotEmpty()) songsByDay[d] = list
        }

        val days = root.optJsonArray("days")?.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val d = o.optStringOrEmpty("d")
            if (d.isBlank()) return@mapNotNull null
            DayStat(
                day = d,
                listenMs = o.optLongOrZero("ms"),
                plays = o.optIntOrZero("n"),
                hourMs = hourByDay[d] ?: emptyList(),
                songs = songsByDay[d] ?: emptyList(),
            )
        }?.sortedBy { it.day } ?: emptyList()

        return ListeningStats(
            totalListenMs = root.optLongOrZero("totalMs"),
            totalPlays = root.optLongOrZero("totalPlays"),
            firstDay = root.optStringOrEmpty("firstDay").ifBlank { days.firstOrNull()?.day ?: "" },
            lastAtMs = root.optLongOrZero("lastAtMs"),
            days = days,
            topSongs = parseSongs(root.optJsonArray("songs") ?: JsonArray()).sortedWith(SONG_ORDER),
        )
    }

    private fun parseSongs(arr: JsonArray): List<SongStat> = arr.mapNotNull { el ->
        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val platform = o.optStringOrEmpty("p")
        val id = o.optStringOrEmpty("i")
        // 平台/id 是排行的身份，缺了这条明细就没有意义，直接丢弃
        if (platform.isBlank() || id.isBlank()) return@mapNotNull null
        SongStat(
            platform = platform,
            id = id,
            title = o.optStringOrEmpty("t"),
            artist = o.optStringOrEmpty("a"),
            plays = o.optIntOrZero("n"),
            listenMs = o.optLongOrZero("ms"),
            lastAtMs = o.optLongOrZero("at"),
            lastPositionMs = o.optLongOrZero("ps"),
        )
    }

    /** 保留窗口裁剪（写盘前调用，保证文件不会无限增长） */
    private fun prune(stats: ListeningStats): ListeningStats {
        val today = todayKey()
        val dayCut = shiftDay(today, -(MAX_DAYS - 1))
        val hourCut = shiftDay(today, -(MAX_HOUR_DAYS - 1))
        val songCut = shiftDay(today, -(MAX_SONG_DAYS - 1))
        val days = stats.days
            .filter { it.day >= dayCut }
            .map { d ->
                d.copy(
                    hourMs = if (d.day >= hourCut && d.hourMs.size == HOURS_PER_DAY) d.hourMs else emptyList(),
                    songs = if (d.day >= songCut) d.songs.take(MAX_SONGS_PER_DAY) else emptyList(),
                )
            }
            .sortedBy { it.day }
        return stats.copy(
            days = days,
            topSongs = stats.topSongs.sortedWith(SONG_ORDER).take(MAX_TOP_SONGS),
        )
    }

    // ==================== 内部：小工具 ====================

    /** 排行顺序：次数 → 时长 → 歌名（稳定，避免同次数时顺序抖动） */
    private val SONG_ORDER = Comparator<SongStat> { a, b ->
        val byPlays = b.plays.compareTo(a.plays)
        if (byPlays != 0) byPlays
        else {
            val byMs = b.listenMs.compareTo(a.listenMs)
            if (byMs != 0) byMs else a.displayTitle.compareTo(b.displayTitle)
        }
    }

    private fun emptyHourMs(): List<Long> = List(HOURS_PER_DAY) { 0L }

    private fun JsonObject.optLongOrZero(key: String): Long {
        val el = if (has(key)) get(key) else null
        return if (el != null && el.isJsonPrimitive) runCatching { el.asLong }.getOrDefault(0L) else 0L
    }

    private fun JsonObject.optIntOrZero(key: String): Int {
        val el = if (has(key)) get(key) else null
        return if (el != null && el.isJsonPrimitive) runCatching { el.asInt }.getOrDefault(0) else 0
    }

    private fun JsonObject.optStringOrEmpty(key: String): String {
        val el = if (has(key)) get(key) else null
        return if (el != null && el.isJsonPrimitive) runCatching { el.asString }.getOrDefault("") else ""
    }

    /** 取数组字段：不是数组时返回 null（脏数据不抛异常） */
    private fun JsonObject.optJsonArray(key: String): JsonArray? {
        val el = if (has(key)) get(key) else null
        return if (el != null && el.isJsonArray) runCatching { el.asJsonArray }.getOrNull() else null
    }

    private fun JsonArray.optLongAt(index: Int): Long {
        if (index < 0 || index >= size()) return 0L
        val el = get(index) ?: return 0L
        return if (el.isJsonPrimitive) runCatching { el.asLong }.getOrDefault(0L) else 0L
    }

    // ThreadLocal 格式化器：SimpleDateFormat 非线程安全，本类会被主线程与 IO 线程同时使用
    private val dayFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    fun todayKey(): String = dayFormat.get()?.format(Date()) ?: ""

    /** 以 yyyy-MM-dd 字符串做日期加减（交给 Calendar，避免手写日期数学） */
    fun shiftDay(day: String, deltaDays: Int): String {
        val fmt = dayFormat.get() ?: return day
        return try {
            val date = fmt.parse(day) ?: return day
            val cal = Calendar.getInstance().apply {
                time = date
                add(Calendar.DAY_OF_YEAR, deltaDays)
            }
            fmt.format(cal.time)
        } catch (_: Exception) {
            day
        }
    }

    /** 当前时刻属于第几个小时（时段分布用） */
    fun hourOf(nowMs: Long): Int =
        Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)

    /** 兼容旧接口：人类可读时长 */
    fun formatDuration(ms: Long): String = ListeningInsights.formatDuration(ms)

    // ==================== 单测入口 ====================
    // 序列化/反序列化/裁剪是"只在磁盘边界上跑"的逻辑，出错时界面只会显示 0，
    // 因此单独开放给单测直接验证（不依赖 SharedPreferences，JVM 可跑）。

    @VisibleForTesting
    internal fun serializeForTest(stats: ListeningStats): String = serialize(stats)

    @VisibleForTesting
    internal fun parseForTest(json: String): ListeningStats = parseDetail(json)

    @VisibleForTesting
    internal fun pruneForTest(stats: ListeningStats): ListeningStats = prune(stats)
}
