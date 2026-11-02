package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.api.PlaylistItem
import com.google.gson.reflect.TypeToken

/**
 * 收藏歌单持久化：
 * 用户收藏的网络热门歌单/精选歌单，存入本地 SharedPreferences，重启不丢失。
 * 键按账号命名空间隔离（见 [LibraryNamespace]）。
 */
object FavoritePlaylistStore {

    private const val PREFS = "aesthetic_music_fav_playlists"
    private const val KEY_PLAYLISTS = "favorite_playlists"

    private var prefs: SharedPreferences? = null
    private val gson = AppJson.gson

    /** 单线程后台写入器：异步落盘 */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    /** 加载全部收藏歌单（容错：解析失败视为空列表） */
    fun load(): List<PlaylistItem> = try {
        val raw = LibraryNamespace.read(prefs, KEY_PLAYLISTS) ?: return emptyList()
        val type = object : TypeToken<List<PlaylistItem>>() {}.type
        gson.fromJson<List<PlaylistItem>>(raw, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** 覆盖保存收藏歌单列表 */
    fun save(playlists: List<PlaylistItem>) {
        writer.execute {
            try {
                LibraryNamespace.write(prefs, KEY_PLAYLISTS, gson.toJson(playlists))
            } catch (_: Exception) {
            }
        }
    }

    /** 清掉当前账号的收藏歌单 */
    fun clear() {
        LibraryNamespace.clear(prefs, KEY_PLAYLISTS)
    }
}
