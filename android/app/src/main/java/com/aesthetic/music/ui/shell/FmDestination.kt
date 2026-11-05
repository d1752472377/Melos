package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.fm.FmController
import com.aesthetic.music.ui.fm.FmScreen
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.theme.FmBackground
import com.aesthetic.music.ui.theme.Palette

/** FM 电台 Tab */
@Composable
fun FmDestination(
    fm: FmController,
    library: LibraryController,
    actions: ShellActions,
    playerManager: PlayerManager,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    val fmQueue by fm.queue.collectAsState()
    val fmIndex by fm.index.collectAsState()
    val fmLoading by fm.loading.collectAsState()
    val fmError by fm.error.collectAsState()
    val fmTreasure by fm.treasure.collectAsState()
    val currentFmChannel by fm.currentChannel.collectAsState()
    val favorites by library.favorites.collectAsState()
    val playerState by playerManager.state.collectAsState()

    // 进入 FM 路由设置与主题匹配的背景，使底部 NavigationDock 呈现深/浅两套毛玻璃质感
    val fmPageBackground = FmBackground
    val fmIsDark = Palette.isDark
    DisposableEffect(fmPageBackground, fmIsDark) {
        onPageBackground(fmPageBackground, fmIsDark)
        onDispose {
            onPageBackground(null, false)
        }
    }

    // 首次切入若队列为空，自动开始调频并启播
    LaunchedEffect(Unit) {
        if (fmQueue.isEmpty() && !fmLoading) {
            fm.start(fm.currentChannel.value) { firstSong, list ->
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
        channels = fm.channels,
        favorites = favorites,
        currentSong = playerState.currentSong,
        isPlaying = playerState.isPlaying,
        isBuffering = playerState.isLoading,
        lyrics = playerState.lyrics,
        syncedLyrics = playerState.syncedLyrics,
        lyricsLoading = playerState.lyricsLoading,
        onReloadLyrics = playerManager::reloadLyrics,
        progressFlow = playerManager.progress,
        onSelectChannel = { channel ->
            fm.start(channel) { firstSong, list ->
                actions.playSong(firstSong, list)
            }
        },
        onRefreshRecommendation = {
            fm.refresh { firstSong, list ->
                actions.playSong(firstSong, list)
            }
        },
        onSelect = { index ->
            fm.select(index)
            val q = fm.queue.value
            val target = q.getOrNull(index)
            val alreadySame = (target?.id != null && playerState.currentSong?.id == target.id)
            if (target != null && !alreadySame) actions.playSong(target, q)
        },
        onToggleLike = { song -> fm.toggleLike(song) },
        onDislike = { song ->
            fm.dislike(song) { nextSong, list ->
                actions.playSong(nextSong, list)
            }
        },
        onPlayPause = { playerManager.toggle() },
        onPrevious = {
            val q = fm.queue.value
            val prevIdx = (fmIndex - 1).coerceAtLeast(0)
            val prevSong = q.getOrNull(prevIdx)
            if (prevSong != null) {
                fm.select(prevIdx)
                actions.playSong(prevSong, q)
            }
        },
        onNext = {
            val q = fm.queue.value
            val nextIdx = (fmIndex + 1).coerceAtMost(q.size - 1)
            val nextSong = q.getOrNull(nextIdx)
            if (nextSong != null) {
                fm.select(nextIdx)
                actions.playSong(nextSong, q)
            }
        },
        onSeek = { ms -> playerManager.seekTo(ms) },
        onSeekToLyric = { line -> playerManager.seekToLyric(line) },
        onExportToPlaylist = { result ->
            // 宝藏歌曲整体放进新建的歌单
            val id = library.createPlaylist(
                name = result.name,
                initialSongs = fmTreasure,
                cover = result.cover,
                sourcePlatform = result.sourcePlatform,
                sourcePlaylistId = result.sourcePlaylistId,
                sourceName = result.sourceName,
            )
            if (id != null) {
                actions.toast("已创建《${result.name}》，收录 ${fmTreasure.size} 首")
            }
        },
    )
}
