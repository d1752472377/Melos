package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.components.SongActionsHost
import com.aesthetic.music.ui.favorites.FavoritesScreen

/** 我的收藏页 */
@Composable
fun FavoritesDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val context = LocalContext.current
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()

    var songForActionMenu by remember { mutableStateOf<Song?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }

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
        onRemoveFavorite = viewModel::toggleFavorite,
        onMoreSong = { songForActionMenu = it },
    )

    SongActionsHost(
        songForActionMenu = songForActionMenu,
        songForAddToPlaylist = songForAddToPlaylist,
        myPlaylists = myPlaylists,
        isSongFavorite = { viewModel.isFavorite(it) },
        onDismissActionMenu = { songForActionMenu = null },
        onDismissAddToPlaylist = { songForAddToPlaylist = null },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onPlayNext = {
            playerManager.playNext(it)
            viewModel.showToast("已设为下一首播放")
        },
        onAddToQueue = {
            playerManager.addToQueue(it)
            viewModel.showToast("已加入播放队列")
        },
        onDownload = { viewModel.downloadSong(context, it) },
        onRequestAddToPlaylist = { song ->
            songForActionMenu = null
            songForAddToPlaylist = song
        },
        onSelectPlaylist = { playlistId, song ->
            viewModel.addSongsToPlaylist(playlistId, listOf(song))
            songForAddToPlaylist = null
            viewModel.showToast("已添加到歌单")
        },
        onCreatePlaylist = { name, song ->
            viewModel.createPlaylist(name, listOf(song))
            songForAddToPlaylist = null
            viewModel.showToast("已创建并加入「$name」")
        },
        onViewCard = { song ->
            val cur = playerState.currentSong
            val isCurrent = cur != null && cur.id == song.id && cur.platform == song.platform
            if (!isCurrent) actions.playSong(song, favoriteSongs)
            viewModel.openPlayer()
        },
    )
}
