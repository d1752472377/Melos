package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.api.SyncDownloadItem

/**
 * 「云端下载记录」的本地镜像：回答「这个账号下载过哪些歌、在哪台设备上」。
 * 本机可播文件仍以缓存索引为准；这里只是清单镜像，不谎报本机状态。
 * 键按账号命名空间隔离（见 [LibraryNamespace]）。
 */
object CloudDownloadStore {

    private const val PREFS = "aesthetic_music_cloud_downloads"
    private const val KEY_RECORDS = "records"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = PrefsJson.prefs(context, PREFS)
    }

    fun load(): List<SyncDownloadItem> =
        PrefsJson.load(prefs, KEY_RECORDS, jsonType<List<SyncDownloadItem>>(), namespaced = true)
            ?: emptyList()

    /** 保持同步写入：apply 内部先读后写，异步会让紧随其后的读取看到旧镜像 */
    fun save(records: List<SyncDownloadItem>) =
        PrefsJson.save(prefs, KEY_RECORDS, records, namespaced = true, async = false)

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
