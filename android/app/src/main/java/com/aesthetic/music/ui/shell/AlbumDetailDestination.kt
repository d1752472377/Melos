package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val albumDetail by viewModel.showAlbumDetail.collectAsState()
    val albumDetailSongs by viewModel.albumDetailSongs.collectAsState()
    val isLoadingAlbum by viewModel.isLoadingAlbum.collectAsState()
    val albumError by viewModel.albumError.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()

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
        myPlaylists = myPlaylists,
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
