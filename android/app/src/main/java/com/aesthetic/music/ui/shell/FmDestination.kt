package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.fm.FmScreen

/** FM 电台 Tab */
@Composable
fun FmDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val fmQueue by viewModel.fmQueue.collectAsState()
    val fmIndex by viewModel.fmIndex.collectAsState()
    val fmLoading by viewModel.fmLoading.collectAsState()
    val fmError by viewModel.fmError.collectAsState()
    val fmTreasure by viewModel.fmTreasure.collectAsState()
    val currentFmChannel by viewModel.currentFmChannel.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playerState by playerManager.state.collectAsState()

    // 进入 FM 路由自动设置暗色背景，使底部 NavigationDock 呈现暗黑毛玻璃质感
    DisposableEffect(Unit) {
        viewModel.updatePageBackground(Color(0xFF0D0D11), true)
        onDispose {
            viewModel.updatePageBackground(null, false)
        }
    }

    // 首次切入若队列为空，自动开始调频并启播
    LaunchedEffect(Unit) {
        if (fmQueue.isEmpty() && !fmLoading) {
            viewModel.fmStart(viewModel.currentFmChannel.value) { firstSong, list ->
                actions.playSong(firstSong, list)
            }
        }
    }

    FmScreen(
        fmQueue = fmQueue,
        fmIndex = fmIndex,
        fmLoading = fmLoading,
        fmError = fmError,
        fmTreasure = fmTreasure,
        currentChannel = currentFmChannel,
        channels = viewModel.fmChannels,
        favorites = favorites,
        currentSong = playerState.currentSong,
        isPlaying = playerState.isPlaying,
        isBuffering = playerState.isLoading,
        lyrics = playerState.lyrics,
        syncedLyrics = playerState.syncedLyrics,
        progressFlow = playerManager.progress,
        onSelectChannel = { channel ->
            viewModel.fmStart(channel) { firstSong, list ->
                actions.playSong(firstSong, list)
            }
        },
        onRefreshRecommendation = {
            viewModel.fmRefresh { firstSong, list ->
                actions.playSong(firstSong, list)
            }
        },
        onSelect = { index ->
            viewModel.fmSelect(index)
            val q = viewModel.fmQueue.value
            val target = q.getOrNull(index)
            val alreadySame = (target?.id != null && playerState.currentSong?.id == target.id)
            if (target != null && !alreadySame) actions.playSong(target, q)
        },
        onToggleLike = { song -> viewModel.fmToggleLike(song) },
        onDislike = { song ->
            viewModel.fmDislike(song) { nextSong, list ->
                actions.playSong(nextSong, list)
            }
        },
        onPlayPause = { playerManager.toggle() },
        onNext = {
            val q = viewModel.fmQueue.value
            val nextIdx = (fmIndex + 1).coerceAtMost(q.size - 1)
            val nextSong = q.getOrNull(nextIdx)
            if (nextSong != null) {
                viewModel.fmSelect(nextIdx)
                actions.playSong(nextSong, q)
            }
        },
        onPrevious = {
            val q = viewModel.fmQueue.value
            val prevIdx = (fmIndex - 1).coerceAtLeast(0)
            val prevSong = q.getOrNull(prevIdx)
            if (prevSong != null) {
                viewModel.fmSelect(prevIdx)
                actions.playSong(prevSong, q)
            }
        },
        onSeek = { ms -> playerManager.seekTo(ms) },
        onSeekToLyric = { line -> playerManager.seekToLyric(line) },
        onExport = { songs -> actions.shareSongs(songs) },
    )
}
