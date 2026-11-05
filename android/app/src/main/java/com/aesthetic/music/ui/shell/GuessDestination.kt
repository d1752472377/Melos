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

/**
 * 「猜你喜欢」页 destination：把 [GuessController] 的状态、[ShellActions] 的动作、
 * 播放器的「当前播放/是否播放中」接到 [GuessYouLikeScreen]。
 *
 * 三个动作的语义对齐 GZ：
 *  - **播放全部** → 整批结果按顺序入队；
 *  - **随机播放** → 先把结果打乱再整体入队；
 *  - **保存为歌单** → 用 GZ 同款命名 `猜你喜欢 yyyy-MM-dd` 建一个自建歌单。
 */
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

    // 进页面即算一次（幂等：已出过结果就不重复联网）
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
