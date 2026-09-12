package com.aesthetic.music.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.player.PlayerState
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.player.PlayerScreen

/** 全屏播放器浮层（非 NavHost 路由，覆盖在整壳之上）。 */
@Composable
fun PlayerOverlay(
    visible: Boolean,
    playerState: PlayerState,
    playerManager: PlayerManager,
    viewModel: MainViewModel,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
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
        )
    }
}
