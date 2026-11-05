package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.artist.ArtistScreen
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
    val myPlaylists by viewModel.myPlaylists.collectAsState()

    // 当前播放歌：PlayerManager 通过 state: StateFlow<PlayerState> 暴露，currentSong 是 PlayerState 的计算属性
    val playerState by playerManager.state.collectAsState()
    val currentSong = playerState.currentSong
    val songActions = rememberSongActionsState()

    val ref = artistRef ?: return
    val info = artistDetail?.info
    val artistSongs = artistDetail?.songs.orEmpty()
    val allSongs by viewModel.allArtistSongs.collectAsState()

    ArtistScreen(
        name = info?.name?.takeIf { it.isNotBlank() } ?: ref.name,
        alias = info?.alias ?: ref.alias,
        avatar = info?.avatar ?: ref.avatar,
        songs = artistSongs,
        isLoading = isLoadingArtist,
        errorMessage = artistError,
        onRetry = { viewModel.openArtist(ref) },
        currentSong = currentSong,
        onPlaySong = { actions.playSong(it, if (allSongs.isNotEmpty()) allSongs else artistSongs) },
        onPlayAll = { actions.playAll(if (allSongs.isNotEmpty()) allSongs else artistSongs) },
        // 随机播放：打乱顺序后整队播放（不改全局播放模式）
        onShuffle = {
            val list = if (allSongs.isNotEmpty()) allSongs else artistSongs
            if (list.isNotEmpty()) actions.playAll(list.shuffled())
        },
        onMoreSong = songActions::showActions,
        onOpenAllSongs = { nav.navigate(ScreenRoute.ArtistAllSongs.route) },
    )

    SongActionsHost(
        state = songActions,
        viewModel = viewModel,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
    )
}