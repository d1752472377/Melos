package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.model.Song
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class MyPlaylist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList(),
    val cloudId: String = "",
    val cover: String = "",
    val sourcePlatform: String = "",
    val sourcePlaylistId: String = "",
    val sourceName: String = "",
) {
    val coverUrl: String? get() = if (cover.isBlank()) songs.firstOrNull()?.artwork else cover

    val isImported: Boolean get() = sourcePlatform.isNotBlank()
}

object PlaylistStore {

    private const val PREFS = "aesthetic_music_playlists"
    private const val KEY_PLAYLISTS = "playlists"

    /** 必须保证非 null 的字符串字段（其余字段缺省可空/可空集合） */
    private val REQUIRED_STRING_FIELDS = listOf(
        "id", "name", "cloudId", "cover", "sourcePlatform", "sourcePlaylistId", "sourceName",
    )

    private var prefs: SharedPreferences? = null
    private val gson = AppJson.gson

    /** 单线程后台写入器：整组歌单 JSON 的序列化与落盘移出调用线程 */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun load(): List<MyPlaylist> = try {
        parsePlaylists(LibraryNamespace.read(prefs, KEY_PLAYLISTS))
    } catch (_: Exception) {
        emptyList()
    }

    internal fun parsePlaylists(raw: String?): List<MyPlaylist> {
        if (raw.isNullOrBlank()) return emptyList()
        // 落盘内容可能被截断/覆盖成非数组：解析失败等同于"没有歌单"，不能把异常抛给调用方
        val array = try {
            gson.fromJson(raw, JsonArray::class.java)
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            normalize(obj)
            try {
                gson.fromJson(obj, MyPlaylist::class.java)
            } catch (_: Exception) {
                null
            }
        }.filter { it.id.isNotBlank() }
    }

    fun save(playlists: List<MyPlaylist>) {
        writer.execute {
            try {
                LibraryNamespace.write(prefs, KEY_PLAYLISTS, gson.toJson(playlists))
            } catch (_: Exception) {
            }
        }
    }

    fun clear() {
        LibraryNamespace.clear(prefs, KEY_PLAYLISTS)
    }

    fun newId(): String = "local_${System.currentTimeMillis()}_${(1000..9999).random()}"

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null

    private fun normalize(obj: JsonObject) {
        val songs = obj.get("songs")
        if (songs == null || songs.isJsonNull || !songs.isJsonArray) {
            obj.add("songs", JsonArray())
        }
        for (key in REQUIRED_STRING_FIELDS) {
            val value = obj.get(key)
            if (value == null || value.isJsonNull || !value.isJsonPrimitive) {
                obj.addProperty(key, "")
            }
        }
        // 读取期间字段必须非空：`id` 为空的历史脏数据在调用方被过滤掉
        if (obj.get("id")?.asString.isNullOrBlank() && obj.get("name")?.asString.isNullOrBlank()) {
            obj.addProperty("name", "未命名歌单")
        }
    }
}
