package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.model.Song
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * 我的歌单（本地数据源，登录后同步到云端）。
 *
 * [cover] 是用户显式设置的封面，取值有两种：
 * - 本机图片：应用私有目录下的绝对路径（由 [PlaylistCoverStore] 落盘，见其注释说明为何必须拷贝）；
 * - 网络封面：从其他平台导入时抓到的原封面 URL。
 * 为空时不落盘任何封面，展示层回落到首曲封面（[coverUrl]）。
 */
data class MyPlaylist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList(),
    /** 云端歌单 id（未同步时为空） */
    val cloudId: String = "",
    /** 自定义封面：本机图片绝对路径或网络 URL；为空时回落到首曲封面 */
    val cover: String = "",
    /** 来源平台（netease/qq/kugou/kuwo；本地自建为空） */
    val sourcePlatform: String = "",
    /** 来源平台的原始歌单 ID（保留"原始记录"出处，便于溯源） */
    val sourcePlaylistId: String = "",
    /** 来源平台的原始歌单名（导入时抓取，用户改名后仍可溯源） */
    val sourceName: String = "",
) {
    /** 列表/详情展示用封面：优先用户设置的封面，其次首曲封面 */
    val coverUrl: String? get() = if (cover.isBlank()) songs.firstOrNull()?.artwork else cover

    /** 是否为外部平台导入的歌单 */
    val isImported: Boolean get() = sourcePlatform.isNotBlank()
}

/**
 * 我的歌单持久化：整组歌单 JSON 落盘。
 *
 * 兼容性（重要）：本项目用 Gson 直接反序列化 Kotlin data class，Gson 绕过构造器、
 * 对 JSON 里缺失的字段不会填 Kotlin 默认值而是留 null。因此每新增一个非空字段，
 * 旧版本落盘的数据就会在该字段上得到 null，进而在 `cover.isBlank()` 这类调用上 NPE。
 * [normalize] 在解析前把缺失/为 null 的字段补成默认值，是这一层唯一的防线。
 */
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
        parsePlaylists(prefs?.getString(KEY_PLAYLISTS, null))
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * 解析整组歌单 JSON（从 [load] 拆出，便于在 JVM 单测里钉住兼容性）。
     *
     * 这里必须容忍"缺字段/字段为 null"：旧版本落盘的数据没有 [MyPlaylist.cover] 等新字段，
     * 而 Gson 绕过 Kotlin 构造器，缺失字段会留下 null 而不是默认值。
     */
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
                prefs?.edit()?.putString(KEY_PLAYLISTS, gson.toJson(playlists))?.apply()
            } catch (_: Exception) {
            }
        }
    }

    fun newId(): String = "local_${System.currentTimeMillis()}_${(1000..9999).random()}"

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null

    /** 补齐旧版本落盘数据缺失的字段，避免 Gson 把 Kotlin 非空字段留成 null */
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
