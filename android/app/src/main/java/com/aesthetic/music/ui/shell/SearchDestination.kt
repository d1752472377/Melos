package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.search.SearchScreen

/** 全局搜索页 */
@Composable
fun SearchDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val context = LocalContext.current
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    SearchScreen(
        viewModel = viewModel,
        currentSong = playerState.currentSong,
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onOpenArtist = { artist ->
            viewModel.openArtist(artist)
            nav.navigate(ScreenRoute.ArtistDetail.route)
        },
        onOpenPlaylist = { pl ->
            viewModel.openPlaylistDetail(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        onOpenAlbum = { album ->
            viewModel.openAlbum(album)
            nav.navigate(ScreenRoute.AlbumDetail.route)
        },
        myPlaylists = myPlaylists,
        isSongFavorite = viewModel::isFavorite,
        onToggleFavorite = viewModel::toggleFavorite,
        onPlayNext = { song ->
            playerManager.playNext(song)
            viewModel.showToast("已设为下一首播放")
        },
        onAddToQueue = { song ->
            playerManager.addToQueue(song)
            viewModel.showToast("已加入播放队列")
        },
        onDownloadSong = { song -> viewModel.downloadSong(context, song) },
        onAddToPlaylist = { playlistId, song ->
            viewModel.addSongsToPlaylist(playlistId, listOf(song))
            val target = myPlaylists.firstOrNull { it.id == playlistId }
            viewModel.showToast("已添加到「${target?.name ?: "歌单"}」")
        },
        onCreatePlaylist = { name, song ->
            viewModel.createPlaylist(name, listOf(song))
            viewModel.showToast("已创建并加入「$name」")
        },
    )
}
