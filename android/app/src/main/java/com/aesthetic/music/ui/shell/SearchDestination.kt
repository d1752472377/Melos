package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.search.SearchScreen

/** 全局搜索页 */
@Composable
fun SearchDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    SearchScreen(
        viewModel = viewModel,
        currentSong = playerState.currentSong,
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onOpenArtist = { artist ->
            viewModel.openArtist(artist)
            nav.navigate(ScreenRoute.ArtistDetail.route)
        },
        onOpenPlaylist = { pl ->
            viewModel.openPlaylistDetail(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        onOpenAlbum = { album ->
            viewModel.openAlbum(album)
            nav.navigate(ScreenRoute.AlbumDetail.route)
        },
        onMoreSong = songActions::showActions,
    )

    SongActionsHost(
        state = songActions,
        viewModel = viewModel,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
    )
}
