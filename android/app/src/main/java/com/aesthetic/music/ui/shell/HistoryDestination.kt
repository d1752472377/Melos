package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.profile.HistoryScreen

/** 最近播放页 */
@Composable
fun HistoryDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val history by viewModel.history.collectAsState()
    val playerState by playerManager.state.collectAsState()
    HistoryScreen(
        songs = history,
        currentSong = playerState.currentSong,
        onBack = {
            viewModel.closeHistory()
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onPlaySong = { actions.playSong(it, history) },
        onPlayAll = { actions.playAll(history) },
        onRemoveOne = viewModel::removeFromHistory,
        onClearAll = viewModel::clearHistory,
    )
}
