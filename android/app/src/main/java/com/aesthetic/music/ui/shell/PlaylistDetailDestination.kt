package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.playlist.PlaylistDetailScreen

/** 歌单详情页（含本地歌单 / 榜单） */
@Composable
fun PlaylistDetailDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val playlistDetail by viewModel.showPlaylistDetail.collectAsState()
    val playlistSongs by viewModel.playlistSongs.collectAsState()
    val isLoadingPlaylist by viewModel.isLoadingPlaylist.collectAsState()
    val playlistError by viewModel.playlistError.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    val detail = playlistDetail ?: return
    val isFav = viewModel.isPlaylistFavorite(detail.id)
    PlaylistDetailScreen(
        playlist = detail,
        songs = playlistSongs,
        isLoading = isLoadingPlaylist,
        error = playlistError,
        isFavorite = isFav,
        currentSong = playerState.currentSong,
        onBack = {
            viewModel.closePlaylistDetail()
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onRetry = { viewModel.openPlaylistDetail(detail) },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onPlayAll = { songs -> actions.playAll(songs) },
        onToggleFavoritePlaylist = { viewModel.toggleFavoritePlaylist(detail) },
        onMoreSong = songActions::showActions,
        onBackgroundColorChange = viewModel::updatePageBackground,
    )

    SongActionsHost(
        state = songActions,
        viewModel = viewModel,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
    )
}
