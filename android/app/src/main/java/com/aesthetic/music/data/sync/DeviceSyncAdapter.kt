package com.aesthetic.music.data.sync

import android.content.Context
import com.aesthetic.music.data.api.SyncDownloadItem
import com.aesthetic.music.data.settings.CloudDownloadStore
import com.aesthetic.music.data.source.SourcePlugin
import com.aesthetic.music.data.source.SourceRegistryStore
import com.aesthetic.music.player.AudioCacheService
import java.io.File

interface DeviceSyncAdapter {

    fun scripts(): List<SyncMapper.LocalScript>

    fun applyScript(script: SyncMapper.LocalScript)

    fun removeScript(id: String)

    fun downloads(): List<SyncMapper.LocalDownload>

    fun mergeCloudDownloads(upserts: List<SyncDownloadItem>, deletedKeys: Set<String>)

    fun deviceId(): String

    fun deviceName(): String
}

object AndroidDeviceSyncAdapter : DeviceSyncAdapter {

    override fun scripts(): List<SyncMapper.LocalScript> =
        SourceRegistryStore.list().map { plugin ->
            SyncMapper.LocalScript(
                id = plugin.id,
                name = plugin.name,
                description = plugin.description,
                code = SourceRegistryStore.getContent(plugin.id).orEmpty(),
                enabled = plugin.enable,
            )
        }

    override fun applyScript(script: SyncMapper.LocalScript) {
        if (script.id.isBlank() || script.code.isBlank()) return
        val existing = SourceRegistryStore.find(script.id)
        val merged = (existing ?: SourcePlugin(id = script.id)).copy(
            id = script.id,
            name = script.name.ifBlank { existing?.name ?: "云端音源" },
            description = script.description,
            enable = script.enabled,
        )
        SourceRegistryStore.upsert(merged, script.code)
    }

    override fun removeScript(id: String) {
        if (id.isNotBlank()) SourceRegistryStore.remove(id)
    }

    override fun downloads(): List<SyncMapper.LocalDownload> {
        val ctx = appContextOrNull() ?: return emptyList()
        return AudioCacheService.listCached(ctx).mapNotNull { cached ->
            if (cached.platformCode.isBlank() || cached.songId.isBlank()) return@mapNotNull null
            SyncMapper.LocalDownload(
                platform = cached.platformCode,
                songId = cached.songId,
                title = cached.title,
                artist = cached.artist,
                sizeBytes = cached.size,
                downloadedAtMs = runCatching { File(cached.filePath).lastModified() }.getOrDefault(0L),
            )
        }
    }

    override fun mergeCloudDownloads(upserts: List<SyncDownloadItem>, deletedKeys: Set<String>) {
        CloudDownloadStore.apply(upserts, deletedKeys)
    }

    override fun deviceId(): String = SyncStateStore.deviceId()

    override fun deviceName(): String = SyncStateStore.deviceName()

    private fun appContextOrNull(): Context? =
        runCatching { com.aesthetic.music.MelosApplication.instance }.getOrNull()
}
