package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.guess.GuessYouLikeScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「猜你喜欢」页 destination：把 [MainViewModel] 的状态、[ShellActions] 的动作、
 * 播放器的「当前播放/是否播放中」接到 [GuessYouLikeScreen]。
 *
 * 三个动作的语义对齐 GZ：
 *  - **播放全部**（`GuessHero` 的 `ys2Var`）→ 整批结果按顺序入队（GZ `n43.java:...`
 *    的 `PlayerViewModel.playSong(..., QueueSource("guess_you_like", "猜你喜欢", ...))`）；
 *  - **随机播放**（`ys2Var2`）→ 先把结果打乱再整体入队，队列内容与「播放全部」一致，
 *    只是起始位置随机（不额外消耗一次网络）；
 *  - **保存为歌单**（`ys2Var3`）→ 用 GZ 同款命名 `猜你喜欢 yyyy-MM-dd`（`x43.java:88`）
 *    建一个自建歌单，已存在同名歌单时 `createPlaylist` 会自己加序号后缀
 *    （`x43.java:105-121` 的 `while (set.contains(name)) name = base + " " + n`）。
 */
@Composable
fun GuessDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val state by viewModel.guessState.collectAsState()
    val refreshing by viewModel.guessRefreshing.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playerState by playerManager.state.collectAsState()

    // 进页面即算一次（幂等：已出过结果就不重复联网）
    LaunchedEffect(Unit) { viewModel.ensureGuessLoaded() }

    GuessYouLikeScreen(
        state = state,
        refreshing = refreshing,
        isPlaying = playerState.isPlaying,
        currentSongId = playerState.currentSong?.let { "${it.platform}:${it.id}" },
        favoriteKeys = favorites,
        onBack = { nav.popBack() },
        onRefresh = { viewModel.refreshGuess() },
        onRetry = { viewModel.retryGuess() },
        onPlayAll = { songs -> actions.playAll(songs) },
        onShuffle = { songs -> actions.playAll(songs.shuffled()) },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onSavePlaylist = { songs ->
            if (songs.isEmpty()) {
                actions.toast("还没有可保存的歌曲")
            } else {
                val name = "猜你喜欢 " +
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val id = viewModel.createPlaylist(name = name, initialSongs = songs)
                actions.toast(
                    if (id != null) "已保存为歌单「$name」" else "保存歌单失败，请稍后再试",
                )
            }
        },
    )
}
