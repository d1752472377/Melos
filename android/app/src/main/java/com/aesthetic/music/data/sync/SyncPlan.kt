package com.aesthetic.music.data.sync

import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import java.security.MessageDigest

object SyncCollections {
    const val FAVORITES = "favorites"
    const val HISTORY = "history"

    const val PLAYLISTS = "playlists"
    const val PLAYLIST_SONGS = "playlistSongs"
    const val SCRIPTS = "scripts"
    const val DOWNLOADS = "downloads"

    val ALL = listOf(FAVORITES, HISTORY, PLAYLISTS, PLAYLIST_SONGS, SCRIPTS, DOWNLOADS)
}

object PlaylistKind {
    const val MINE = "mine"
    const val FAV = "fav"
}

object SyncKey {

    private const val SEP = "\u0001"

    fun encode(vararg parts: String?): String = parts.joinToString(SEP) { it.orEmpty() }

    fun decode(encoded: String): List<String> = encoded.split(SEP)

    fun song(platform: String?, id: String?): String = encode(platform, id)

    fun song(song: Song): String = song(song.platform, song.id)

    /** 歌单：`kind` + `歌单id` —— 自建歌单与"收藏的网络歌单"可能撞 id，必须分域 */
    fun playlist(kind: String, id: String): String = encode(kind, id)

    fun playlistSong(kind: String, playlistId: String, platform: String?, songId: String?): String =
        encode(kind, playlistId, platform, songId)

    fun script(id: String): String = encode(id)

    fun download(deviceId: String, platform: String?, songId: String?): String =
        encode(deviceId, platform, songId)
}

object SyncFingerprint {

    private const val SEP = "\u0001"

    fun hash(vararg parts: Any?): String {
        val canonical = parts.joinToString(SEP) { it?.toString().orEmpty() }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return buildString(16) {
            for (i in 0 until 8) append("%02x".format(digest[i]))
        }
    }

    fun of(song: Song): String = hash(song.platform, song.id, song.title, song.artist, song.album, song.artwork)

    fun of(playlist: MyPlaylist, kind: String, wireCover: String?): String =
        hash(kind, playlist.id, playlist.name, wireCover)

    fun ofPlaylistSong(
        playlistId: String,
        position: Int,
        platform: String?,
        songId: String?,
        song: Song?,
    ): String = hash(playlistId, position, platform, songId, song?.title, song?.artist, song?.album, song?.artwork)

    fun ofScript(id: String, name: String, description: String, code: String, enabled: Boolean): String =
        hash(id, name, description, code, enabled)

    fun ofDownload(
        platform: String?,
        songId: String?,
        deviceId: String,
        title: String,
        artist: String,
        sizeBytes: Long,
    ): String = hash(platform, songId, deviceId, title, artist, sizeBytes)
}

data class SyncSnapshot(
    val fingerprints: Map<String, String> = emptyMap(),
    val pendingDeletes: Set<String> = emptySet(),
)

object SyncPlan {

    fun changedKeys(local: Map<String, String>, snapshot: SyncSnapshot): List<String> =
        local.filter { (key, fp) -> snapshot.fingerprints[key] != fp }
            .keys
            .sorted()

    fun effectiveDeletes(pending: Set<String>, localKeys: Set<String>): List<String> =
        pending.filterNot { it in localKeys }.sorted()

    fun applyPulled(
        snapshot: SyncSnapshot,
        upserts: Map<String, String> = emptyMap(),
        deletes: Collection<String> = emptyList(),
    ): SyncSnapshot {
        val next = snapshot.fingerprints.toMutableMap()
        upserts.forEach { (key, fp) -> next[key] = fp }
        deletes.forEach { next.remove(it) }
        return snapshot.copy(fingerprints = next)
    }

    fun afterPush(
        snapshot: SyncSnapshot,
        pushedUpserts: Map<String, String>,
        pushedDeletes: Collection<String>,
    ): SyncSnapshot = snapshot.copy(
        fingerprints = snapshot.fingerprints + pushedUpserts,
        pendingDeletes = snapshot.pendingDeletes - pushedDeletes.toSet(),
    )

    fun recordDelete(snapshot: SyncSnapshot, key: String): SyncSnapshot = snapshot.copy(
        fingerprints = snapshot.fingerprints - key,
        pendingDeletes = snapshot.pendingDeletes + key,
    )

    fun recordAdd(snapshot: SyncSnapshot, key: String): SyncSnapshot =
        snapshot.copy(pendingDeletes = snapshot.pendingDeletes - key)

    fun shouldApplyPulledUpsert(key: String, snapshot: SyncSnapshot): Boolean =
        key !in snapshot.pendingDeletes

    fun clearCollection(snapshot: SyncSnapshot, keys: Collection<String>): SyncSnapshot = snapshot.copy(
        fingerprints = snapshot.fingerprints - keys.toSet(),
        pendingDeletes = snapshot.pendingDeletes - keys.toSet(),
    )
}
