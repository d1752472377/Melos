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

    val displayTitle: String get() = title.ifBlank { "未知歌曲" }
    val displayArtist: String get() = artist.ifBlank { "未知歌手" }
}

data class DayStat(
    val day: String,
    val listenMs: Long = 0,
    val plays: Int = 0,
    val hourMs: List<Long> = emptyList(),
    val songs: List<SongStat> = emptyList(),
) {
    val isActive: Boolean get() = listenMs > 0 || plays > 0
}

data class ListeningStats(
    val totalListenMs: Long = 0,
    val totalPlays: Long = 0,
    val firstDay: String = "",
    val lastAtMs: Long = 0,
    val days: List<DayStat> = emptyList(),
    val topSongs: List<SongStat> = emptyList(),
) {
    val isEmpty: Boolean get() = totalListenMs <= 0 && totalPlays <= 0 && days.isEmpty()

    fun day(day: String): DayStat? = days.firstOrNull { it.day == day }
}

data class LocalStats(
    val totalPlayMs: Long = 0,
    val totalPlays: Long = 0,
    val dayPlayMs: Long = 0,
    val day: String = "",
)

object StatsStore {

    private const val TAG = "StatsStore"
    private const val PREFS = "aesthetic_music_stats"
    private const val KEY_LEGACY = "stats"
    private const val KEY_DETAIL = "stats_detail_v2"

    private const val FORMAT_VERSION = 2

    private const val MAX_DAYS = 180

    private const val MAX_HOUR_DAYS = 31

    private const val MAX_SONG_DAYS = 21

    private const val MAX_SONGS_PER_DAY = 8

    private const val MAX_TOP_SONGS = 60

    private const val FLUSH_INTERVAL_MS = 30_000L

    const val HOURS_PER_DAY = 24

    const val HOUR_WINDOW_DAYS = MAX_HOUR_DAYS

    const val DAY_SONG_WINDOW_DAYS = MAX_SONG_DAYS

    private var prefs: SharedPreferences? = null

    /** 内存模型：所有读写都在锁内（主线程进度轮询 + IO 线程读界面数据） */
    private val lock = Any()
    private var cache: ListeningStats? = null

    @Volatile
    private var lastFlushAt = 0L

    @Volatile
    private var recordingCached = true

    @Volatile
    private var recordingCachedAt = 0L

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

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

    /** 与后端 playSessionGapMs 一致：同一首歌、同设备、间隔不超过 15 秒视为同一次播放。 */
    private const val CLOUD_PLAY_SESSION_GAP_MS = 15_000L

    fun ingestCloudEvents(events: List<SyncPlayEventItem>) {
        if (events.isEmpty()) return
        synchronized(lock) {
            val p = prefs
            val ingestedSet = p?.getStringSet(KEY_INGESTED_EVENTS, emptySet())?.toMutableSet() ?: mutableSetOf()
            val myDevice = com.aesthetic.music.data.sync.SyncStateStore.deviceId()

            val candidates = events
                .filter {
                    it.eventId.isNotBlank() &&
                        it.eventId !in ingestedSet &&
                        (it.deviceId.isBlank() || it.deviceId != myDevice) &&
                        it.listenedMs > 0
                }
                .sortedBy { if (it.listenedAtMs > 0) it.listenedAtMs else it.updatedAtMs }
            if (candidates.isEmpty()) return

            var stats = loadLocked()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            // 按"播放会话"计数：连续的同歌片段只算一次播放，时长累加。
            // 逐条 +1 会把一次收听拆成几十次播放（分片每 20 秒上报一条）。
            var sessionKey: String? = null
            var sessionEnd = 0L
            var sessionMs = 0L
            var sessionTimeMs = 0L
            var sessionSong: Song? = null
            val sessions = mutableListOf<Pair<Song, Pair<Long, Long>>>() // song -> (startMs, listenedMs)

            fun flushSession() {
                val song = sessionSong ?: return
                if (sessionMs <= 0L) return
                sessions.add(song to (sessionTimeMs to sessionMs))
            }

            for (e in candidates) {
                val timeMs = if (e.listenedAtMs > 0) e.listenedAtMs else e.updatedAtMs
                val key = "${e.deviceId}|${e.platform}|${e.songId}"
                val song = Song(
                    platform = e.platform,
                    id = e.songId,
                    title = e.title,
                    artist = e.artist,
                )
                // 缺时间戳的旧数据按同歌同设备直接并入当前会话，避免退化成一条一片段。
                val continuous = sessionKey == key &&
                    (timeMs <= 0L || timeMs <= sessionEnd + CLOUD_PLAY_SESSION_GAP_MS)
                if (continuous) {
                    sessionMs += e.listenedMs
                    sessionEnd = maxOf(sessionEnd, timeMs + e.listenedMs)
                } else {
                    flushSession()
                    sessionKey = key
                    sessionSong = song
                    sessionTimeMs = timeMs
                    sessionMs = e.listenedMs
                    sessionEnd = timeMs + e.listenedMs
                }
                ingestedSet.add(e.eventId)
            }
            flushSession()

            for ((song, data) in sessions) {
                val (startMs, listenedMs) = data
                val dayStr = if (startMs > 0) dateFormat.format(Date(startMs)) else todayKey()
                val hour = if (startMs > 0) hourOf(startMs) else 0
                val day = stats.day(dayStr)
                val songStat = bumpSong(
                    existing = day?.songs?.firstOrNull { it.key == songKey(song) },
                    song = song,
                    playDelta = 1,
                    listenDelta = listenedMs,
                    positionMs = 0L,
                    now = startMs,
                )
                val hourMs = (if (day?.hourMs?.size == HOURS_PER_DAY) day.hourMs else emptyHourMs())
                    .toMutableList()
                    .also { it[hour] = it[hour] + listenedMs }

                val updatedDay = (day ?: DayStat(day = dayStr)).copy(
                    listenMs = (day?.listenMs ?: 0L) + listenedMs,
                    plays = (day?.plays ?: 0) + 1,
                    hourMs = hourMs,
                    songs = putSong(day?.songs ?: emptyList(), songStat),
                )

                stats = stats.copy(
                    totalListenMs = stats.totalListenMs + listenedMs,
                    totalPlays = stats.totalPlays + 1,
                    firstDay = if (stats.firstDay.isBlank() || (dayStr.isNotBlank() && dayStr < stats.firstDay)) dayStr else stats.firstDay,
                    lastAtMs = maxOf(stats.lastAtMs, startMs),
                    days = putDay(stats.days, updatedDay),
                    topSongs = bumpGlobal(stats.topSongs, songStat, playDelta = 1, listenDelta = listenedMs),
                )
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

    fun flush() {
        val json = synchronized(lock) {
            val stats = cache ?: return
            lastFlushAt = System.currentTimeMillis()
            serialize(stats)
        }
        persist(json)
    }

    fun clearAll() {
        val json = synchronized(lock) {
            cache = ListeningStats()
            lastFlushAt = System.currentTimeMillis()
            serialize(ListeningStats())
        }
        persist(json)
    }

    fun invalidateRecordingCache() {
        recordingCachedAt = 0L
    }

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
        title = song.title.ifBlank { existing?.title.orEmpty() },
        artist = song.artist.ifBlank { existing?.artist.orEmpty() },
        plays = (existing?.plays ?: 0) + playDelta,
        listenMs = (existing?.listenMs ?: 0L) + listenDelta,
        lastAtMs = now,
        lastPositionMs = if (positionMs > 0) positionMs else (existing?.lastPositionMs ?: 0L),
    )

    private fun putSong(list: List<SongStat>, incoming: SongStat): List<SongStat> {
        val idx = list.indexOfFirst { it.key == incoming.key }
        val out = if (idx < 0) list + incoming else list.toMutableList().also { it[idx] = incoming }
        return out.sortedWith(SONG_ORDER)
    }

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
            legacyDay > today -> today
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

    fun hourOf(nowMs: Long): Int =
        Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)

    /** 兼容旧接口：人类可读时长 */
    fun formatDuration(ms: Long): String = ListeningInsights.formatDuration(ms)

    // 序列化/反序列化/裁剪是"只在磁盘边界上跑"的逻辑，出错时界面只会显示 0，

    @VisibleForTesting
    internal fun serializeForTest(stats: ListeningStats): String = serialize(stats)

    @VisibleForTesting
    internal fun parseForTest(json: String): ListeningStats = parseDetail(json)

    @VisibleForTesting
    internal fun pruneForTest(stats: ListeningStats): ListeningStats = prune(stats)
}
