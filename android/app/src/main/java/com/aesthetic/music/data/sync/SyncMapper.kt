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

object SyncMapper {

    data class LocalScript(
        val id: String,
        val name: String,
        val description: String = "",
        val code: String = "",
        val enabled: Boolean = true,
    )

    data class LocalDownload(
        val platform: String,
        val songId: String,
        val title: String = "",
        val artist: String = "",
        val sizeBytes: Long = 0L,
        val downloadedAtMs: Long = 0L,
    )

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

    fun favoriteDeletes(keys: Collection<String>): List<SyncSongKey> = keys.mapNotNull {
        SyncKey.decode(it).takeIf { p -> p.size == 2 }?.let { p -> SyncSongKey(platform = p[0], id = p[1]) }
    }

    fun historyDeletes(keys: Collection<String>): List<SyncSongKey> = favoriteDeletes(keys)

    fun playlistDeletes(keys: Collection<String>): List<SyncPlaylistKey> = keys.mapNotNull {
        // 基线键形如 "<kind>\u0001888"：kind 必须跟着墓碑一起发出去，
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

    fun wireCover(cover: String?): String? = cover?.trim()?.takeIf {
        it.startsWith("http://") || it.startsWith("https://") ||
            it.startsWith("data:image/", ignoreCase = true)
    }

    const val MAX_SCRIPT_BYTES = 512 * 1024
}
