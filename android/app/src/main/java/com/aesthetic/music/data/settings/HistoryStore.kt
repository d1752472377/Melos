package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 最近播放持久化：SharedPreferences 存 JSON，重启不丢失。
 * 之前历史是纯内存（重启即清空），这里补上落盘。
 */
object HistoryStore {

    private const val PREFS = "aesthetic_music_history"
    private const val KEY_SONGS = "history_songs"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun load(): List<Song> = try {
        val raw = prefs?.getString(KEY_SONGS, null) ?: return emptyList()
        val type = object : TypeToken<List<Song>>() {}.type
        gson.fromJson<List<Song>>(raw, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun save(songs: List<Song>) {
        try {
            prefs?.edit()?.putString(KEY_SONGS, gson.toJson(songs.take(100)))?.apply()
        } catch (_: Exception) {
        }
    }
}
