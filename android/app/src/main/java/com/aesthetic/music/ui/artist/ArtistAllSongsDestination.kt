package com.aesthetic.music.ui.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.artist.ArtistAllSongsScreen
import com.aesthetic.music.ui.shell.ShellActions
import com.aesthetic.music.ui.shell.SongActionsHost
import com.aesthetic.music.ui.shell.rememberSongActionsState

/** 全部演唱页 */
@Composable
fun ArtistAllSongsDestination(
    viewModel: MainViewModel,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val artistRef by viewModel.showArtist.collectAsState()
    val artistDetail by viewModel.artistDetail.collectAsState()
    val allSongs by viewModel.allArtistSongs.collectAsState()
    val isLoadingAllSongs by viewModel.isLoadingArtistAllSongs.collectAsState()
    val allSongsError by viewModel.artistAllSongsError.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val myPlaylists by viewModel.myPlaylists.collectAsState()

    val playerState by playerManager.state.collectAsState()
    val currentSong = playerState.currentSong
    val songActions = rememberSongActionsState()

    val name = artistDetail?.info?.name?.takeIf { it.isNotBlank() }
        ?: artistRef?.name
        ?: "歌手"
    val alias = artistDetail?.info?.alias ?: artistRef?.alias
    val avatar = artistDetail?.info?.avatar ?: artistRef?.avatar
    val songs = if (allSongs.isNotEmpty()) allSongs else artistDetail?.songs.orEmpty()

    ArtistAllSongsScreen(
        artistName = name,
        alias = alias,
        avatar = avatar,
        songs = songs,
        isLoading = isLoadingAllSongs && songs.isEmpty(),
        isLoadingMore = isLoadingAllSongs && songs.isNotEmpty(),
        errorMessage = if (songs.isEmpty()) allSongsError else null,
        onRetry = { viewModel.loadArtistAllSongs() },
        favorites = favorites,
        currentSong = currentSong,
        onPlaySong = { actions.playSong(it, songs) },
        onPlayAll = { actions.playAll(songs) },
        // 随机播放：打乱顺序后整队播放（不改全局播放模式）
        onShuffle = { if (songs.isNotEmpty()) actions.playAll(songs.shuffled()) },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onPlayNext = { playerManager.playNext(it) },
        onMoreSong = songActions::showActions,
    )

    SongActionsHost(
        state = songActions,
        viewModel = viewModel,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
    )
}