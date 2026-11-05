package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.favorites.FavoritesScreen
import com.aesthetic.music.ui.library.LibraryController

/** 我的收藏页 */
@Composable
fun FavoritesDestination(
    library: LibraryController,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
    onPageBackground: (Color?, Boolean) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val favoriteSongs by library.favoriteSongs.collectAsState()
    val myPlaylists by library.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    FavoritesScreen(
        songs = favoriteSongs,
        currentSong = playerState.currentSong,
        isPlaying = playerState.isPlaying,
        onBack = {
            onPageBackground(null, false)
            nav.popBack()
        },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onPlayAll = { list -> actions.playAll(list) },
        onMoreSong = songActions::showActions,
    )

    SongActionsHost(
        state = songActions,
        library = library,
        actions = actions,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
        onViewCard = { song ->
            val cur = playerState.currentSong
            val isCurrent = cur != null && cur.id == song.id && cur.platform == song.platform
            if (!isCurrent) actions.playSong(song, favoriteSongs)
            onOpenPlayer()
        },
    )
}
