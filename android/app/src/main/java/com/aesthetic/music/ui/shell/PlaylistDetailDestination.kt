package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val playlistDetail by viewModel.showPlaylistDetail.collectAsState()
    val playlistSongs by viewModel.playlistSongs.collectAsState()
    val isLoadingPlaylist by viewModel.isLoadingPlaylist.collectAsState()
    val playlistError by viewModel.playlistError.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()

    val detail = playlistDetail ?: return
    val isFav = viewModel.isPlaylistFavorite(detail.id)
    PlaylistDetailScreen(
        playlist = detail,
        songs = playlistSongs,
        isLoading = isLoadingPlaylist,
        error = playlistError,
        isFavorite = isFav,
        myPlaylists = myPlaylists,
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
        onToggleFavoriteSong = viewModel::toggleFavorite,
        isSongFavorite = viewModel::isFavorite,
        onAddToQueue = { song ->
            playerManager.addToQueue(song)
            viewModel.showToast("已加入播放队列")
        },
        onPlayNext = { song ->
            playerManager.playNext(song)
            viewModel.showToast("已设为下一首播放")
        },
        onDownloadSong = { song -> viewModel.downloadSong(context, song) },
        onAddToPlaylist = { playlistId, song ->
            viewModel.addSongsToPlaylist(playlistId, listOf(song))
            val targetPlaylist = myPlaylists.firstOrNull { it.id == playlistId }
            viewModel.showToast("已添加到「${targetPlaylist?.name ?: "歌单"}」")
        },
        onCreatePlaylist = { name, song ->
            viewModel.createPlaylist(name, listOfNotNull(song))
            viewModel.showToast("已创建并加入「$name」")
        },
        onBackgroundColorChange = viewModel::updatePageBackground,
    )
}
