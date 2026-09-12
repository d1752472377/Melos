package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.profile.MyPlaylistsScreen

/** 我的歌单页 */
@Composable
fun MyPlaylistsDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    MyPlaylistsScreen(
        playlists = myPlaylists,
        currentSong = playerState.currentSong,
        onBack = {
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onOpenPlaylist = { id ->
            viewModel.openMyPlaylist(id)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        onCreate = { name -> viewModel.createPlaylist(name) },
        onRename = viewModel::renamePlaylist,
        onDelete = viewModel::deletePlaylist,
        onAddCurrent = { id, song ->
            viewModel.addSongsToPlaylist(id, listOf(song))
            actions.toast("已加入歌单")
        },
    )
}
