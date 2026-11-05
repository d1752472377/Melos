package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.playlist.PlaylistDetailScreen

/** 专辑详情页（搜索页专辑 Tab 点击进入，复用歌单详情界面） */
@Composable
fun AlbumDetailDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val albumDetail by viewModel.showAlbumDetail.collectAsState()
    val albumDetailSongs by viewModel.albumDetailSongs.collectAsState()
    val isLoadingAlbum by viewModel.isLoadingAlbum.collectAsState()
    val albumError by viewModel.albumError.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    val album = albumDetail ?: return
    val albumPlaylistItem = remember(album, albumDetailSongs.size) {
        PlaylistItem(
            id = album.id,
            name = album.name,
            coverUrl = album.coverUrl,
            trackCount = album.size ?: albumDetailSongs.size.toLong(),
            platform = album.platform,
        )
    }
    val isFav = viewModel.isPlaylistFavorite(album.id)
    PlaylistDetailScreen(
        playlist = albumPlaylistItem,
        songs = albumDetailSongs,
        isLoading = isLoadingAlbum,
        error = albumError,
        isFavorite = isFav,
        currentSong = playerState.currentSong,
        onBack = {
            viewModel.closeAlbum()
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onRetry = { viewModel.openAlbum(album) },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onPlayAll = { songs -> actions.playAll(songs) },
        onToggleFavoritePlaylist = { viewModel.toggleFavoritePlaylist(albumPlaylistItem) },
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
