package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 本地听歌统计（未登录也记录；登录后与云端合并展示） */
data class LocalStats(
    val totalPlayMs: Long = 0,
    val totalPlays: Long = 0,
    val dayPlayMs: Long = 0,
    val day: String = "",
)

/**
 * 真实听歌统计：累计时长与播放次数，按天归零当日时长。
 * 播放器每 500ms 上报一次增量，这里做本地聚合；登录后 PlayerManager 会再上报云端。
 */
object StatsStore {

    private const val PREFS = "aesthetic_music_stats"
    private const val KEY_STATS = "stats"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun load(): LocalStats {
        val today = today()
        val raw = try {
            val s = prefs?.getString(KEY_STATS, null) ?: return LocalStats(day = today)
            val type = object : TypeToken<LocalStats>() {}.type
            gson.fromJson<LocalStats>(s, type) ?: LocalStats(day = today)
        } catch (_: Exception) {
            LocalStats(day = today)
        }
        return if (raw.day == today) raw else raw.copy(dayPlayMs = 0, day = today)
    }

    fun addListen(deltaMs: Long) {
        if (deltaMs <= 0) return
        val today = today()
        val cur = load()
        val next = cur.copy(
            totalPlayMs = cur.totalPlayMs + deltaMs,
            dayPlayMs = (if (cur.day == today) cur.dayPlayMs else 0) + deltaMs,
            day = today,
        )
        save(next)
    }

    fun addPlay() {
        val today = today()
        val cur = load()
        save(cur.copy(totalPlays = cur.totalPlays + 1, day = today))
    }

    private fun save(stats: LocalStats) {
        try {
            prefs?.edit()?.putString(KEY_STATS, gson.toJson(stats))?.apply()
        } catch (_: Exception) {
        }
    }

    // ThreadLocal 格式化器：SimpleDateFormat 非线程安全，而本类会被主线程与 IO 线程同时使用
    private val dayFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
        override fun initialValue() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    }

    private fun today(): String = dayFormat.get()?.format(java.util.Date()) ?: ""

    fun formatDuration(ms: Long): String {
        val minutes = ms / 60000
        return if (minutes < 60) "${minutes} 分钟"
        else "%.1f 小时".format(minutes / 60.0)
    }
}
