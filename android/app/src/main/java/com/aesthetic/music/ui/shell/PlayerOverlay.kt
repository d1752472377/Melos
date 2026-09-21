package com.aesthetic.music.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.player.PlayerState
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.artist.ArtistRef
import com.aesthetic.music.ui.components.AddToPlaylistSheet
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.player.PlayerScreen
import com.aesthetic.music.ui.search.SearchType
import dev.chrisbanes.haze.HazeState

/** 全屏播放器浮层（非 NavHost 路由，覆盖在整壳之上）。 */
@Composable
fun PlayerOverlay(
    visible: Boolean,
    playerState: PlayerState,
    playerManager: PlayerManager,
    viewModel: MainViewModel,
    nav: ShellNav,
    hazeState: HazeState? = null,
) {
    val context = LocalContext.current
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // 彻底阻断任何未处理的触摸事件向下透传至 underlying NavHost 页面
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Final)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            PlayerScreen(
                state = playerState,
                progressFlow = playerManager.progress,
                isFavorite = playerState.currentSong?.let { viewModel.isFavorite(it) } == true,
                onClose = { viewModel.closePlayer() },
                onToggle = playerManager::toggle,
                onNext = playerManager::next,
                onPrevious = playerManager::previous,
                onSeek = playerManager::seekTo,
                onSeekToLyric = playerManager::seekToLyric,
                onTogglePlayMode = playerManager::togglePlayMode,
                onToggleFavorite = { playerState.currentSong?.let { viewModel.toggleFavorite(it) } },
                onPlayQueueItem = playerManager::playQueueItem,
                onRemoveFromQueue = playerManager::removeFromQueue,
                onSpeedChange = playerManager::setSpeed,
                onSleepTimer = playerManager::startSleepTimer,
                onCancelSleepTimer = playerManager::cancelSleepTimer,
                onArtistClick = { artistName, artistId, platform ->
                    viewModel.closePlayer()
                    if (!artistId.isNullOrBlank()) {
                        viewModel.openArtist(
                            ArtistRef(
                                platform = platform ?: "netease",
                                id = artistId,
                                name = artistName,
                            )
                        )
                        nav.navigate(ScreenRoute.ArtistDetail.route)
                    } else {
                        viewModel.onSearchQueryChanged(artistName)
                        viewModel.setSearchType(SearchType.ARTIST)
                        viewModel.submitSearch(artistName)
                        nav.navigate(ScreenRoute.Search.route)
                    }
                },
                onDownload = {
                    playerState.currentSong?.let { song ->
                        viewModel.downloadSong(context, song)
                    }
                },
                onAddToPlaylist = {
                    songForAddToPlaylist = playerState.currentSong
                },
                hazeState = hazeState,
            )

            songForAddToPlaylist?.let { song ->
                AddToPlaylistSheet(
                    song = song,
                    myPlaylists = myPlaylists,
                    onDismiss = { songForAddToPlaylist = null },
                    onSelectPlaylist = { playlistId ->
                        viewModel.addSongsToPlaylist(playlistId, listOf(song))
                        songForAddToPlaylist = null
                    },
                    onCreateAndAdd = { newName ->
                        viewModel.createPlaylist(newName, listOf(song))
                        songForAddToPlaylist = null
                    },
                )
            }
        }
    }
}

