package com.aesthetic.music.ui.library

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.CloudSyncPullData
import com.aesthetic.music.data.api.SyncPlaylistItem
import com.aesthetic.music.data.api.SyncPlaylistSongItem
import com.aesthetic.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlaylistSongsTest {

    @Test
    fun `pull cloud playlist and songs restores songs in playlist`() {
        val c = LibraryController(
            repository = MusicRepository(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            onToast = {},
        )

        // Reflection to call private applyCloudChanges
        val method = LibraryController::class.java.getDeclaredMethod(
            "applyCloudChanges",
            Long::class.javaPrimitiveType,
            CloudSyncPullData::class.java
        )
        method.isAccessible = true

        val song1 = Song(id = "s1", platform = "netease", title = "Song 1", artist = "Artist 1")
        val song2 = Song(id = "s2", platform = "netease", title = "Song 2", artist = "Artist 2")

        val pullData = CloudSyncPullData(
            version = 1L,
            playlists = listOf(
                SyncPlaylistItem(id = "local_100", kind = "mine", name = "My Playlist", coverUrl = "")
            ),
            playlistSongs = listOf(
                SyncPlaylistSongItem(playlistId = "local_100", position = 0, platform = "netease", songId = "s1", song = song1),
                SyncPlaylistSongItem(playlistId = "local_100", position = 1, platform = "netease", songId = "s2", song = song2),
            )
        )

        method.invoke(c, 3L, pullData)

        val playlists = c.myPlaylists.value
        assertEquals(1, playlists.size)
        val p = playlists[0]
        assertEquals("local_100", p.id)
        println("Songs in playlist: " + p.songs.size)
        assertEquals(2, p.songs.size)
    }
}
