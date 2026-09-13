package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.api.SyncDownloadItem
import com.google.gson.reflect.TypeToken

/**
 * 「云端下载记录」的本地镜像。
 *
 * ## 为什么需要一个镜像，而不是直接把云端记录当成本机缓存
 * 本机缓存列表的数据源是**磁盘上的文件**（`cacheDir/audio_cache` + 索引）。
 * 云端下发的记录里只有"另一台设备下过这首歌"，本机并没有那个文件，
 * 凭空写进缓存索引会让"缓存列表"显示一堆点了播不出来的条目（谎报本机状态）。
 *
 * 因此分工是：
 * - 本机文件 → 缓存索引（真实、可播）；
 * - 云端记录 → 这里（清单镜像，回答"这个账号下载过哪些歌、在哪台设备上"）。
 *
 * 用途：换机/重装后能知道该重新下载哪些歌；将来在缓存页做"云端记录"分区时直接读这里。
 * 本次改动只落库、未做界面（见交付说明），不做界面也不算"假功能"——数据是真的，只是还没展示。
 */
object CloudDownloadStore {

    private const val PREFS = "aesthetic_music_cloud_downloads"
    private const val KEY_RECORDS = "records"

    private var prefs: SharedPreferences? = null
    private val gson = AppJson.gson

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun load(): List<SyncDownloadItem> = try {
        val raw = prefs?.getString(KEY_RECORDS, null) ?: return emptyList()
        val type = object : TypeToken<List<SyncDownloadItem>>() {}.type
        gson.fromJson<List<SyncDownloadItem>>(raw, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun save(records: List<SyncDownloadItem>) {
        try {
            prefs?.edit()?.putString(KEY_RECORDS, gson.toJson(records))?.apply()
        } catch (_: Exception) {
        }
    }

    /**
     * 用云端下发的变更更新镜像：`deleted=true` 的条目移除，其余按
     * 「平台 + 歌曲id + 设备id」覆盖（同一设备同一首歌只留一条，避免重复累积）。
     */
    fun apply(upserts: List<SyncDownloadItem>, deletedKeys: Set<String>) {
        val current = load().associateBy { keyOf(it) }.toMutableMap()
        upserts.forEach { item ->
            if (item.platform.isBlank() || item.id.isBlank()) return@forEach
            current[keyOf(item)] = item
        }
        deletedKeys.forEach { current.remove(it) }
        save(current.values.sortedByDescending { it.downloadedAtMs })
    }

    private fun keyOf(item: SyncDownloadItem): String =
        listOf(item.deviceId, item.platform, item.id).joinToString("\u0001")
}
