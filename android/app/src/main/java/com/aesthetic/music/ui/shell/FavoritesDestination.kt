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
    val playerState by playerManager.state.collectAsState()
    FavoritesScreen(
        songs = favoriteSongs,
        currentSong = playerState.currentSong,
        onBack = {
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onPlaySong = { actions.playSong(it, favoriteSongs) },
        onPlayAll = { actions.playAll(favoriteSongs) },
        onRemoveFavorite = viewModel::toggleFavorite,
    )
}
