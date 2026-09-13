package com.aesthetic.music.data.sync

import android.content.Context
import com.aesthetic.music.data.api.SyncDownloadItem
import com.aesthetic.music.data.settings.CloudDownloadStore
import com.aesthetic.music.data.source.SourcePlugin
import com.aesthetic.music.data.source.SourceRegistryStore
import com.aesthetic.music.player.AudioCacheService
import java.io.File

/**
 * 「只存在于本机」的数据与同步流程之间的适配器：音源脚本（源码文件）与下载记录（缓存文件）。
 *
 * ## 为什么要这一层
 * 收藏/歌单/历史都存在 SharedPreferences 的 JSON 里，同步时直接读写即可；
 * 但脚本的**源码**在 `filesDir/sources/<id>.js`、下载的**文件**在 `cacheDir/audio_cache/`，
 * 都不是"一个键值对"。把这两种存储的细节关在这个适配器里，
 * 同步编排层（`LibraryController`）就只面对普通列表，不必知道文件与索引的存在。
 *
 * 接口而不是直接用单例，是为了让编排层在单测里可以注入假的实现。
 */
interface DeviceSyncAdapter {

    /** 本机所有音源脚本（元数据 + 源码） */
    fun scripts(): List<SyncMapper.LocalScript>

    /** 云端下发的脚本落到本机（新增或覆盖源码） */
    fun applyScript(script: SyncMapper.LocalScript)

    /** 云端删除了脚本：本机也删（含源码文件） */
    fun removeScript(id: String)

    /** 本机下载记录（来自音频缓存索引，只含文件当前真实存在的条目） */
    fun downloads(): List<SyncMapper.LocalDownload>

    /** 云端下载记录镜像（另一台设备的文件不会真的出现在本机，只记录"下过什么"） */
    fun mergeCloudDownloads(upserts: List<SyncDownloadItem>, deletedKeys: Set<String>)

    fun deviceId(): String

    fun deviceName(): String
}

/** Android 实现：读写插件注册表与音频缓存。 */
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
        // 只覆盖"同步语义里存在的字段"，本机特有的字段（版本/作者/主页/适用平台/声明源）保持不变：
        // 那些字段来自脚本沙箱的真实解析结果，云端没有，用默认值覆盖等于把本机信息弄丢
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
                // 缓存索引里没有"下载时间"，用文件的最后修改时间兜底：它是这份文件真实落盘的时刻
                downloadedAtMs = runCatching { File(cached.filePath).lastModified() }.getOrDefault(0L),
            )
        }
    }

    override fun mergeCloudDownloads(upserts: List<SyncDownloadItem>, deletedKeys: Set<String>) {
        CloudDownloadStore.apply(upserts, deletedKeys)
    }

    override fun deviceId(): String = SyncStateStore.deviceId()

    override fun deviceName(): String = SyncStateStore.deviceName()

    /**
     * 取全局 Application Context。
     *
     * 适配器需要在没有 Activity 的场景（后台同步、服务里）也能工作，
     * 因此不从调用方传 Context，而是走应用级单例（[com.aesthetic.music.MelosApplication]）。
     * 未初始化（例如 JVM 单测）时返回 null，调用方按"没有本机数据"处理而不是崩溃。
     */
    private fun appContextOrNull(): Context? =
        runCatching { com.aesthetic.music.MelosApplication.instance }.getOrNull()
}
