package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistScreen

/** 歌单广场 Tab */
@Composable
fun PlaylistTabDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
) {
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val favoritePlaylists by viewModel.favoritePlaylists.collectAsState()
    PlaylistScreen(
        myPlaylists = myPlaylists,
        favoritePlaylists = favoritePlaylists,
        onOpenMyPlaylist = { id ->
            viewModel.openMyPlaylist(id)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        onOpenPlaylistDetail = { pl ->
            viewModel.openPlaylistDetail(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
        onRenamePlaylist = viewModel::renamePlaylist,
        onDeletePlaylist = viewModel::deletePlaylist,
        onOpenImport = { viewModel.openImportDialog() },
        onPlayPlaylist = { songs -> actions.playAll(songs) },
    )
}
