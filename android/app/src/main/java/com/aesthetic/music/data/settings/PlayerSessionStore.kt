package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 播放断点记忆：保存队列 / 当前曲目 / 进度 / 倍速。
 * App 下次启动时恢复，点“继续播放”即可从上次位置接着听。
 */
data class PlayerSession(
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0,
)

object PlayerSessionStore {

    private const val PREFS = "aesthetic_music_session"
    private const val KEY_QUEUE = "queue"
    private const val KEY_INDEX = "index"
    private const val KEY_POSITION = "position"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun load(): PlayerSession = try {
        val raw = prefs?.getString(KEY_QUEUE, null) ?: return PlayerSession()
        val type = object : TypeToken<List<Song>>() {}.type
        val queue = gson.fromJson<List<Song>>(raw, type) ?: emptyList()
        PlayerSession(
            queue = queue,
            currentIndex = (prefs?.getInt(KEY_INDEX, -1) ?: -1).coerceIn(-1, queue.size - 1),
            positionMs = (prefs?.getLong(KEY_POSITION, 0) ?: 0).coerceAtLeast(0),
        )
    } catch (_: Exception) {
        PlayerSession()
    }

    fun save(session: PlayerSession) {
        try {
            prefs?.edit()
                ?.putString(KEY_QUEUE, gson.toJson(session.queue))
                ?.putInt(KEY_INDEX, session.currentIndex)
                ?.putLong(KEY_POSITION, session.positionMs)
                ?.apply()
        } catch (_: Exception) {
        }
    }

    fun clear() {
        try {
            prefs?.edit()?.remove(KEY_QUEUE)?.remove(KEY_INDEX)?.remove(KEY_POSITION)?.apply()
        } catch (_: Exception) {
        }
    }
}
