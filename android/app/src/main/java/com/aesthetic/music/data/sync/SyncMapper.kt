package com.aesthetic.music.data.sync

import com.aesthetic.music.data.api.SyncDownloadKey
import com.aesthetic.music.data.api.SyncDownloadUpsert
import com.aesthetic.music.data.api.SyncHistoryUpsert
import com.aesthetic.music.data.api.SyncPlaylistKey
import com.aesthetic.music.data.api.SyncPlaylistSongKey
import com.aesthetic.music.data.api.SyncPlaylistSongUpsert
import com.aesthetic.music.data.api.SyncPlaylistUpsert
import com.aesthetic.music.data.api.SyncScriptKey
import com.aesthetic.music.data.api.SyncScriptUpsert
import com.aesthetic.music.data.api.SyncSongKey
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.data.api.PlaylistItem

/**
 * 本地数据 → 同步数据的**纯映射**：指纹基线、以及"只把我选中的那几条变成 wire 对象"。
 *
 * 为什么要单独抽一层：它是"同步到底同步了什么"的唯一真相来源。
 * 编排层（`LibraryController`）只负责取数据、发请求、写回本地，
 * 全部字段与键的规则都集中在这里，可以被单测逐条钉住（含"哪些字段不该同步"）。
 *
 * ## 明确的取舍
 * - **不**同步播放直链（会过期）、**不**同步本机绝对路径（换设备无意义，且属于本机信息）；
 * - 下载记录只带「哪台设备下过哪首歌 + 体积 + 时间」：文件名/路径属于本机事实，服务端也不存；
 * - 歌单封面只同步 http(s) 封面，本机图片封面在别的设备上必然加载失败。
 *
 * ## 有意未同步的字段（不是遗漏，改写在这里之前请先想清楚）
 * - `MyPlaylist.sourcePlatform / sourcePlaylistId / sourceName`（"从哪个平台的哪个歌单导入"）：
 *   它是**本机来源备注**，歌单内容本身会随 playlistSongs 一起同步，另一台设备拿到内容后可用；
 *   要同步它得给后端加三列，收益只是"在另一台设备上看到来源标签"，暂不做。
 * - 收藏歌单（kind=fav）的 `playCount / trackCount / platform / group`：这些由平台实时接口返回，
 *   换设备打开收藏歌单时会重新取一次，存进云端只会变成过期快照。
 */
object SyncMapper {

    /** 一条本地脚本（元数据 + 源码），由调用方从插件注册表拼出来 */
    data class LocalScript(
        val id: String,
        val name: String,
        val description: String = "",
        val code: String = "",
        val enabled: Boolean = true,
    )

    /** 一条本地下载记录（来自本机音频缓存索引） */
    data class LocalDownload(
        val platform: String,
        val songId: String,
        val title: String = "",
        val artist: String = "",
        val sizeBytes: Long = 0L,
        val downloadedAtMs: Long = 0L,
    )

    // ==================== 指纹基线 ====================

    fun favoriteFingerprints(list: List<Song>): Map<String, String> =
        list.filter { it.id.isNotBlank() }.associate { SyncKey.song(it) to SyncFingerprint.of(it) }

    fun historyFingerprints(list: List<Song>): Map<String, String> =
        list.filter { it.id.isNotBlank() }.associate { SyncKey.song(it) to SyncFingerprint.of(it) }

    fun playlistFingerprints(mine: List<MyPlaylist>, favorite: List<PlaylistItem>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        mine.filter { it.id.isNotBlank() }.forEach {
            out[SyncKey.playlist(PlaylistKind.MINE, it.id)] =
                SyncFingerprint.of(it, PlaylistKind.MINE, wireCover(it.coverUrl))
        }
        favorite.filter { it.id.isNotBlank() }.forEach {
            out[SyncKey.playlist(PlaylistKind.FAV, it.id)] =
                SyncFingerprint.hash(PlaylistKind.FAV, it.id, it.name, wireCover(it.coverUrl))
        }
        return out
    }

    /**
     * 歌单曲目的指纹包含 `position`：同一首歌在歌单里被拖动排序后，位置变化必须同步过去。
     */
    fun playlistSongFingerprints(mine: List<MyPlaylist>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        mine.filter { it.id.isNotBlank() }.forEach { p ->
            p.songs.forEachIndexed { index, song ->
                if (song.id.isBlank()) return@forEachIndexed
                val key = SyncKey.playlistSong(PlaylistKind.MINE, p.id, song.platform, song.id)
                out[key] = SyncFingerprint.ofPlaylistSong(p.id, index, song.platform, song.id, song)
            }
        }
        return out
    }

    fun scriptFingerprints(list: List<LocalScript>): Map<String, String> =
        list.filter { it.id.isNotBlank() }.associate {
            SyncKey.script(it.id) to SyncFingerprint.ofScript(it.id, it.name, it.description, it.code, it.enabled)
        }

    fun downloadFingerprints(list: List<LocalDownload>, deviceId: String): Map<String, String> =
        list.filter { it.songId.isNotBlank() }.associate {
            SyncKey.download(deviceId, it.platform, it.songId) to SyncFingerprint.ofDownload(
                platform = it.platform,
                songId = it.songId,
                deviceId = deviceId,
                title = it.title,
                artist = it.artist,
                sizeBytes = it.sizeBytes,
            )
        }

    // ==================== 推送内容（只含传入的键） ====================

    fun favoriteUpserts(list: List<Song>, keys: Collection<String>): List<Song> {
        if (keys.isEmpty()) return emptyList()
        val wanted = keys.toHashSet()
        return list.filter { it.id.isNotBlank() && SyncKey.song(it) in wanted }
    }

    fun historyUpserts(list: List<Song>, keys: Collection<String>): List<SyncHistoryUpsert> {
        if (keys.isEmpty()) return emptyList()
        val wanted = keys.toHashSet()
        return list.filter { it.id.isNotBlank() && SyncKey.song(it) in wanted }.map {
            SyncHistoryUpsert(
                id = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                artwork = it.artwork,
                platform = it.platform,
            )
        }
    }

    fun playlistUpserts(
        mine: List<MyPlaylist>,
        favorite: List<PlaylistItem>,
        keys: Collection<String>,
    ): List<SyncPlaylistUpsert> {
        if (keys.isEmpty()) return emptyList()
        val wanted = keys.toHashSet()
        val out = mutableListOf<SyncPlaylistUpsert>()
        mine.filter { it.id.isNotBlank() && SyncKey.playlist(PlaylistKind.MINE, it.id) in wanted }.forEach {
            out += SyncPlaylistUpsert(
                id = it.id,
                name = it.name,
                coverUrl = wireCover(it.coverUrl),
                kind = PlaylistKind.MINE,
            )
        }
        favorite.filter { it.id.isNotBlank() && SyncKey.playlist(PlaylistKind.FAV, it.id) in wanted }.forEach {
            out += SyncPlaylistUpsert(
                id = it.id,
                name = it.name,
                coverUrl = wireCover(it.coverUrl),
                kind = PlaylistKind.FAV,
            )
        }
        return out
    }

    fun playlistSongUpserts(mine: List<MyPlaylist>, keys: Collection<String>): List<SyncPlaylistSongUpsert> {
        if (keys.isEmpty()) return emptyList()
        val wanted = keys.toHashSet()
        val out = mutableListOf<SyncPlaylistSongUpsert>()
        mine.filter { it.id.isNotBlank() }.forEach { p ->
            p.songs.forEachIndexed { index, song ->
                if (song.id.isBlank()) return@forEachIndexed
                val key = SyncKey.playlistSong(PlaylistKind.MINE, p.id, song.platform, song.id)
                if (key in wanted) out += SyncPlaylistSongUpsert(playlistId = p.id, position = index, song = song)
            }
        }
        return out
    }

    fun scriptUpserts(list: List<LocalScript>, keys: Collection<String>): List<SyncScriptUpsert> {
        if (keys.isEmpty()) return emptyList()
        val wanted = keys.toHashSet()
        return list
            .filter { it.id.isNotBlank() && SyncKey.script(it.id) in wanted && fitsWire(it) }
            .map {
                SyncScriptUpsert(id = it.id, name = it.name, description = it.description, code = it.code, enabled = it.enabled)
            }
    }

    /**
     * 太大的脚本不上传（见 [MAX_SCRIPT_BYTES] 的说明）。单独暴露成函数，
     * 是为了让编排层能就"哪些脚本被跳过了"给用户一句提示——静默不同步比报错更糟。
     */
    fun oversizedScripts(list: List<LocalScript>): List<LocalScript> = list.filter { it.id.isNotBlank() && !fitsWire(it) }

    private fun fitsWire(script: LocalScript): Boolean =
        script.code.toByteArray(Charsets.UTF_8).size <= MAX_SCRIPT_BYTES

    fun downloadUpserts(
        list: List<LocalDownload>,
        deviceId: String,
        deviceName: String,
        keys: Collection<String>,
    ): List<SyncDownloadUpsert> {
        if (keys.isEmpty()) return emptyList()
        val wanted = keys.toHashSet()
        return list.filter { it.songId.isNotBlank() && SyncKey.download(deviceId, it.platform, it.songId) in wanted }
            .map {
                SyncDownloadUpsert(
                    platform = it.platform,
                    id = it.songId,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    title = it.title,
                    artist = it.artist,
                    sizeBytes = it.sizeBytes,
                    downloadedAtMs = it.downloadedAtMs,
                )
            }
    }

    // ==================== 删除墓碑（从基线键反解出各字段） ====================

    fun favoriteDeletes(keys: Collection<String>): List<SyncSongKey> = keys.mapNotNull {
        SyncKey.decode(it).takeIf { p -> p.size == 2 }?.let { p -> SyncSongKey(platform = p[0], id = p[1]) }
    }

    fun historyDeletes(keys: Collection<String>): List<SyncSongKey> = favoriteDeletes(keys)

    fun playlistDeletes(keys: Collection<String>): List<SyncPlaylistKey> = keys.mapNotNull {
        // 基线键形如 "<kind>\u0001888"：kind 必须跟着墓碑一起发出去，
        // 否则服务端只能按默认值推断——收藏歌单(id 为平台数字 id)与自建歌单会被混为一谈。
        SyncKey.decode(it).takeIf { p -> p.size == 2 }
            ?.let { p -> SyncPlaylistKey(id = p[1], kind = p[0].ifBlank { PlaylistKind.MINE }) }
    }

    fun playlistSongDeletes(keys: Collection<String>): List<SyncPlaylistSongKey> = keys.mapNotNull {
        SyncKey.decode(it).takeIf { p -> p.size == 4 }
            ?.let { p -> SyncPlaylistSongKey(playlistId = p[1], platform = p[2], songId = p[3]) }
    }

    fun scriptDeletes(keys: Collection<String>): List<SyncScriptKey> = keys.mapNotNull {
        SyncKey.decode(it).firstOrNull()?.takeIf { id -> id.isNotBlank() }?.let { id -> SyncScriptKey(id) }
    }

    fun downloadDeletes(keys: Collection<String>): List<SyncDownloadKey> = keys.mapNotNull {
        SyncKey.decode(it).takeIf { p -> p.size == 3 }
            ?.let { p -> SyncDownloadKey(platform = p[1], id = p[2], deviceId = p[0]) }
    }

    /**
     * 歌单封面归一化：只有 http(s) 才同步。
     * 本机图片封面（应用私有目录下的绝对路径）在其它设备上必然加载失败，
     * 传上去只会让另一台设备看到一个永远坏掉的封面。
     */
    fun wireCover(cover: String?): String? =
        cover?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    /**
     * 单个脚本源码同步上限（按 UTF-8 字节算）。
     *
     * 服务端只对**整个请求体**限制 4MB（Fastify bodyLimit），**没有字段级上限**。
     * 这意味着一个异常巨大的脚本会把一次 push 整个顶爆（413），
     * 连带**同一次同步里所有其它数据**都推不上去——用户看到的是"同步彻底坏了"，
     * 却完全不知道原因。所以这里在源头挡住：超限的脚本只留在本机，并让上层明确提示。
     *
     * 512KB 的取法：正常音源脚本是几 KB~几十 KB，512KB 已属异常；
     * 留足空间后仍能让"多个脚本 + 其它集合"一次性安全通过。
     */
    const val MAX_SCRIPT_BYTES = 512 * 1024
}
