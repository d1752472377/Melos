package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.artist.ArtistScreen
import com.aesthetic.music.ui.components.SongActionsHost
import com.aesthetic.music.ui.navigation.ScreenRoute

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
    val myPlaylists by viewModel.myPlaylists.collectAsState()

    // 当前播放歌：PlayerManager 通过 state: StateFlow<PlayerState> 暴露，currentSong 是 PlayerState 的计算属性
    val playerState by playerManager.state.collectAsState()
    val currentSong = playerState.currentSong

    var songForActionMenu by remember { mutableStateOf<Song?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }

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
        currentSong = currentSong,
        onBack = {
            viewModel.closeArtist()
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onPlaySong = { actions.playSong(it, artistSongs) },
        onPlayAll = { actions.playAll(artistSongs) },
        onMoreSong = { song -> songForActionMenu = song },
        onOpenAllSongs = { nav.navigate(ScreenRoute.ArtistAllSongs.route) },
    )

    SongActionsHost(
        songForActionMenu = songForActionMenu,
        songForAddToPlaylist = songForAddToPlaylist,
        myPlaylists = myPlaylists,
        isSongFavorite = { viewModel.isFavorite(it) },
        onDismissActionMenu = { songForActionMenu = null },
        onDismissAddToPlaylist = { songForAddToPlaylist = null },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onPlayNext = { playerManager.playNext(it) },
        onAddToQueue = { playerManager.addToQueue(it) },
        onDownload = { /* 下载需要 Context；若 actions 里已有 downloadSong，改成 actions.downloadSong(it) */ },
        onRequestAddToPlaylist = { song ->
            songForActionMenu = null
            songForAddToPlaylist = song
        },
        onSelectPlaylist = { playlistId, song ->
            viewModel.addSongsToPlaylist(playlistId, listOf(song))
            songForAddToPlaylist = null
        },
        onCreatePlaylist = { newName, song ->
            viewModel.createPlaylist(newName, listOf(song))
            songForAddToPlaylist = null
        },
    )
}