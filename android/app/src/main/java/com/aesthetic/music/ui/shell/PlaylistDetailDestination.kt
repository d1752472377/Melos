package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.playlist.PlaylistDetailScreen

/** 歌单详情页（含本地歌单 / 榜单） */
@Composable
fun PlaylistDetailDestination(
    playlistDetail: PlaylistDetailController,
    library: LibraryController,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    val detailItem by playlistDetail.showDetail.collectAsState()
    val playlistSongs by playlistDetail.songs.collectAsState()
    val isLoadingPlaylist by playlistDetail.isLoading.collectAsState()
    val playlistError by playlistDetail.error.collectAsState()
    val myPlaylists by library.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    val detail = detailItem ?: return
    val isFav = library.isPlaylistFavorite(detail.id)
    PlaylistDetailScreen(
        playlist = detail,
        songs = playlistSongs,
        isLoading = isLoadingPlaylist,
        error = playlistError,
        isFavorite = isFav,
        currentSong = playerState.currentSong,
        onBack = {
            playlistDetail.close()
            onPageBackground(null, false)
            nav.popBack()
        },
        onRetry = { playlistDetail.open(detail) },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onPlayAll = { songs -> actions.playAll(songs) },
        onToggleFavoritePlaylist = { library.toggleFavoritePlaylist(detail) },
        onMoreSong = songActions::showActions,
        onBackgroundColorChange = onPageBackground,
    )

    SongActionsHost(
        state = songActions,
        library = library,
        actions = actions,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
    )
}
