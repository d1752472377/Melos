package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.guess.GuessController
import com.aesthetic.music.ui.guess.GuessYouLikeScreen
import com.aesthetic.music.ui.library.LibraryController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GuessDestination(
    guess: GuessController,
    library: LibraryController,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val state by guess.state.collectAsState()
    val refreshing by guess.refreshing.collectAsState()
    val favorites by library.favorites.collectAsState()
    val playerState by playerManager.state.collectAsState()

    LaunchedEffect(Unit) { guess.ensureLoaded() }

    GuessYouLikeScreen(
        state = state,
        refreshing = refreshing,
        isPlaying = playerState.isPlaying,
        currentSongId = playerState.currentSong?.let { "${it.platform}:${it.id}" },
        favoriteKeys = favorites,
        onBack = { nav.popBack() },
        onRefresh = { guess.refresh() },
        onRetry = { guess.retry() },
        onPlayAll = { songs -> actions.playAll(songs) },
        onShuffle = { songs -> actions.playAll(songs.shuffled()) },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onToggleFavorite = { library.toggleFavorite(it) },
        onSavePlaylist = { songs ->
            if (songs.isEmpty()) {
                actions.toast("还没有可保存的歌曲")
            } else {
                val name = "猜你喜欢 " +
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val id = library.createPlaylist(name = name, initialSongs = songs)
                actions.toast(
                    if (id != null) "已保存为歌单「$name」" else "保存歌单失败，请稍后再试",
                )
            }
        },
    )
}
