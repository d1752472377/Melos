package com.aesthetic.music.ui.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.artist.ArtistAllSongsScreen
import com.aesthetic.music.ui.components.SongActionsHost
import com.aesthetic.music.ui.shell.ShellActions
import com.aesthetic.music.ui.shell.ShellNav

/** 全部演唱页 */
@Composable
fun ArtistAllSongsDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val artistRef by viewModel.showArtist.collectAsState()
    val artistDetail by viewModel.artistDetail.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()

    val playerState by playerManager.state.collectAsState()
    val currentSong = playerState.currentSong

    var songForActionMenu by remember { mutableStateOf<Song?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    val name = artistDetail?.info?.name?.takeIf { it.isNotBlank() }
        ?: artistRef?.name
        ?: "歌手"
    val songs = artistDetail?.songs.orEmpty()

    ArtistAllSongsScreen(
        artistName = name,
        songs = songs,
        favorites = favorites,
        currentSong = currentSong,
        onBack = { nav.popBack() },
        onPlaySong = { actions.playSong(it, songs) },
        onPlayAll = { actions.playAll(songs) },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onPlayNext = { playerManager.playNext(it) },
        onMoreSong = { song -> songForActionMenu = song },
    )

    SongActionsHost(
        songForActionMenu = songForActionMenu,
        songForAddToPlaylist = songForAddToPlaylist,
        myPlaylists = myPlaylists,
        isSongFavorite = { viewModel.isFavorite(it) },
        onDismissActionMenu = { songForActionMenu = null },
        onDismissAddToPlaylist = { songForAddToPlaylist = null },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onPlayNext = { playerManager.playNext(it) },
        onAddToQueue = { playerManager.addToQueue(it) },
        onDownload = { /* 同 ArtistDetailDestination，按你 actions 里下载入口补 */ },
        onRequestAddToPlaylist = { song ->
            songForActionMenu = null
            songForAddToPlaylist = song
        },
        onSelectPlaylist = { playlistId, song ->
            viewModel.addSongsToPlaylist(playlistId, listOf(song))
            songForAddToPlaylist = null
        },
        onCreatePlaylist = { newName, song ->
            viewModel.createPlaylist(newName, listOf(song))
            songForAddToPlaylist = null
        },
    )
}