package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.model.Song

/**
 * 收藏持久化：SharedPreferences 存歌曲 JSON，重启不丢失。
 * 键按账号命名空间隔离（见 [LibraryNamespace]）——不同账号的收藏互不可见。
 */
object FavoriteStore {

    private const val PREFS = "aesthetic_music_favorites"
    private const val KEY_SONGS = "favorite_songs"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = PrefsJson.prefs(context, PREFS)
    }

    /** 加载全部收藏歌曲（容错：解析失败视为空） */
    fun load(): List<Song> =
        PrefsJson.load(prefs, KEY_SONGS, jsonType<List<Song>>(), namespaced = true) ?: emptyList()

    /** 覆盖保存收藏列表 */
    fun save(songs: List<Song>) =
        PrefsJson.save(prefs, KEY_SONGS, songs, namespaced = true)

    /** 清掉当前账号的收藏（含一次性的旧格式迁移读取） */
    fun clear() = PrefsJson.clear(prefs, KEY_SONGS, namespaced = true)
}
