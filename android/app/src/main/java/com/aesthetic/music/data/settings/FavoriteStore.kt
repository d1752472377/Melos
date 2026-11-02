package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.model.Song
import com.google.gson.reflect.TypeToken

/**
 * 收藏持久化（对应参考 App 的 FavoriteService）：
 * SharedPreferences 存歌曲 JSON，重启不丢失。
 * 键按账号命名空间隔离（见 [LibraryNamespace]）——不同账号的收藏互不可见。
 */
object FavoriteStore {

    private const val PREFS = "aesthetic_music_favorites"
    private const val KEY_SONGS = "favorite_songs"

    private var prefs: SharedPreferences? = null
    private val gson = AppJson.gson

    /** 单线程后台写入器：序列化与落盘移出调用线程 */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    /** 加载全部收藏歌曲（容错：解析失败视为空） */
    fun load(): List<Song> = try {
        val raw = LibraryNamespace.read(prefs, KEY_SONGS) ?: return emptyList()
        val type = object : TypeToken<List<Song>>() {}.type
        gson.fromJson<List<Song>>(raw, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** 覆盖保存收藏列表 */
    fun save(songs: List<Song>) {
        writer.execute {
            try {
                LibraryNamespace.write(prefs, KEY_SONGS, gson.toJson(songs))
            } catch (_: Exception) {
            }
        }
    }

    /** 清掉当前账号的收藏（含一次性的旧格式迁移读取） */
    fun clear() {
        LibraryNamespace.clear(prefs, KEY_SONGS)
    }
}
