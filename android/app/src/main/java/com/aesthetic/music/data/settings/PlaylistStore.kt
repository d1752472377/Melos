package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 我的歌单（本地数据源，登录后同步到云端） */
data class MyPlaylist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList(),
    /** 云端歌单 id（未同步时为空） */
    val cloudId: String = "",
) {
    val coverUrl: String? get() = songs.firstOrNull()?.artwork
}

/**
 * 我的歌单持久化：整组歌单 JSON 落盘。
 */
object PlaylistStore {

    private const val PREFS = "aesthetic_music_playlists"
    private const val KEY_PLAYLISTS = "playlists"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    /** 单线程后台写入器：整组歌单 JSON 的序列化与落盘移出调用线程 */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun load(): List<MyPlaylist> = try {
        val raw = prefs?.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        val type = object : TypeToken<List<MyPlaylist>>() {}.type
        gson.fromJson<List<MyPlaylist>>(raw, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun save(playlists: List<MyPlaylist>) {
        writer.execute {
            try {
                prefs?.edit()?.putString(KEY_PLAYLISTS, gson.toJson(playlists))?.apply()
            } catch (_: Exception) {
            }
        }
    }

    fun newId(): String = "local_${System.currentTimeMillis()}_${(1000..9999).random()}"
}
