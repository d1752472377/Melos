package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 收藏持久化（对应参考 App 的 FavoriteService）：
 * SharedPreferences 存歌曲 JSON，重启不丢失。
 */
object FavoriteStore {

    private const val PREFS = "aesthetic_music_favorites"
    private const val KEY_SONGS = "favorite_songs"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    /** 加载全部收藏歌曲（容错：解析失败视为空） */
    fun load(): List<Song> = try {
        val raw = prefs?.getString(KEY_SONGS, null) ?: return emptyList()
        val type = object : TypeToken<List<Song>>() {}.type
        gson.fromJson<List<Song>>(raw, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** 覆盖保存收藏列表 */
    fun save(songs: List<Song>) {
        try {
            prefs?.edit()?.putString(KEY_SONGS, gson.toJson(songs))?.apply()
        } catch (_: Exception) {
        }
    }
}
