package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.api.SyncDownloadItem

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

    fun save(records: List<SyncDownloadItem>) =
        PrefsJson.save(prefs, KEY_RECORDS, records, namespaced = true, async = false)

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
