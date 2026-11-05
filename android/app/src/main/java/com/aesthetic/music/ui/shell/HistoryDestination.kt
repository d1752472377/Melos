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
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    HistoryScreen(
        songs = history,
        currentSong = playerState.currentSong,
        isPlaying = playerState.isPlaying,
        onBack = {
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onPlayAll = { list -> actions.playAll(list) },
        onMoreSong = songActions::showActions,
        onClearAll = viewModel::clearHistory,
    )

    SongActionsHost(
        state = songActions,
        viewModel = viewModel,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
        // 查看卡片：不是当前播放的歌就先按历史列表播它，再打开全屏播放器
        onViewCard = { song ->
            val cur = playerState.currentSong
            val isCurrent = cur != null && cur.id == song.id && cur.platform == song.platform
            if (!isCurrent) actions.playSong(song, history)
            viewModel.openPlayer()
        },
        onRemove = { viewModel.removeFromHistory(it) },
        removeLabel = "移除",
    )
}
