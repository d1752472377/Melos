package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.favorites.FavoritesScreen

/** 我的收藏页 */
@Composable
fun FavoritesDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    FavoritesScreen(
        songs = favoriteSongs,
        currentSong = playerState.currentSong,
        isPlaying = playerState.isPlaying,
        onBack = {
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onPlayAll = { list -> actions.playAll(list) },
        onMoreSong = songActions::showActions,
    )

    SongActionsHost(
        state = songActions,
        viewModel = viewModel,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
        onViewCard = { song ->
            val cur = playerState.currentSong
            val isCurrent = cur != null && cur.id == song.id && cur.platform == song.platform
            if (!isCurrent) actions.playSong(song, favoriteSongs)
            viewModel.openPlayer()
        },
    )
}
