package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.artist.ArtistScreen

/** 歌手页 */
@Composable
fun ArtistDetailDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val artistRef by viewModel.showArtist.collectAsState()
    val artistDetail by viewModel.artistDetail.collectAsState()
    val isLoadingArtist by viewModel.isLoadingArtist.collectAsState()
    val artistError by viewModel.artistError.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    val ref = artistRef ?: return
    val info = artistDetail?.info
    val artistSongs = artistDetail?.songs.orEmpty()
    ArtistScreen(
        name = info?.name?.takeIf { it.isNotBlank() } ?: ref.name,
        alias = info?.alias,
        avatar = info?.avatar,
        songs = artistSongs,
        isLoading = isLoadingArtist,
        errorMessage = artistError,
        onRetry = { viewModel.openArtist(ref) },
        favorites = favorites,
        onBack = {
            viewModel.closeArtist()
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onPlaySong = { actions.playSong(it, artistSongs) },
        onPlayAll = { actions.playAll(artistSongs) },
        onToggleFavorite = viewModel::toggleFavorite,
    )
}
