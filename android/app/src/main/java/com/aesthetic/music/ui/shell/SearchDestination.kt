package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.artist.ArtistController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.search.SearchController
import com.aesthetic.music.ui.search.SearchScreen

@Composable
fun SearchDestination(
    search: SearchController,
    artist: ArtistController,
    playlistDetail: PlaylistDetailController,
    library: LibraryController,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val myPlaylists by library.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    SearchScreen(
        search = search,
        currentSong = playerState.currentSong,
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onOpenArtist = { item ->
            artist.open(item)
            nav.navigate(ScreenRoute.ArtistDetail.route)
        },
        onOpenPlaylist = { pl ->
            playlistDetail.open(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        onOpenAlbum = { item ->
            playlistDetail.openAlbum(item)
            nav.navigate(ScreenRoute.AlbumDetail.route)
        },
        onMoreSong = songActions::showActions,
    )

    SongActionsHost(
        state = songActions,
        library = library,
        actions = actions,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
    )
}
