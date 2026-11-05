package com.aesthetic.music.ui.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.artist.ArtistAllSongsScreen
import com.aesthetic.music.ui.artist.ArtistController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.shell.ShellActions
import com.aesthetic.music.ui.shell.SongActionsHost
import com.aesthetic.music.ui.shell.rememberSongActionsState

/** 全部演唱页 */
@Composable
fun ArtistAllSongsDestination(
    artist: ArtistController,
    library: LibraryController,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val artistRef by artist.showArtist.collectAsState()
    val artistDetail by artist.artistDetail.collectAsState()
    val allSongs by artist.allSongs.collectAsState()
    val isLoadingAllSongs by artist.isLoadingAllSongs.collectAsState()
    val allSongsError by artist.allSongsError.collectAsState()
    val favorites by library.favorites.collectAsState()
    val myPlaylists by library.myPlaylists.collectAsState()

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
        onRetry = { artist.loadAllSongs() },
        favorites = favorites,
        currentSong = currentSong,
        onPlaySong = { actions.playSong(it, songs) },
        onPlayAll = { actions.playAll(songs) },
        // 随机播放：打乱顺序后整队播放（不改全局播放模式）
        onShuffle = { if (songs.isNotEmpty()) actions.playAll(songs.shuffled()) },
        onToggleFavorite = { library.toggleFavorite(it) },
        onPlayNext = { playerManager.playNext(it) },
        onMoreSong = songActions::showActions,
    )

    SongActionsHost(
        state = songActions,
        library = library,
        actions = actions,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
    )
}
