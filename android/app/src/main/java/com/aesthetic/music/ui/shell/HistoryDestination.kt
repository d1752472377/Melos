package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.profile.HistoryScreen

/** 最近播放页 */
@Composable
fun HistoryDestination(
    library: LibraryController,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
    onPageBackground: (Color?, Boolean) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val history by library.history.collectAsState()
    val myPlaylists by library.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    HistoryScreen(
        songs = history,
        currentSong = playerState.currentSong,
        isPlaying = playerState.isPlaying,
        onBack = {
            onPageBackground(null, false)
            nav.popBack()
        },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onPlayAll = { list -> actions.playAll(list) },
        onMoreSong = songActions::showActions,
        onClearAll = library::clearHistory,
    )

    SongActionsHost(
        state = songActions,
        library = library,
        actions = actions,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
        // 查看卡片：不是当前播放的歌就先按历史列表播它，再打开全屏播放器
        onViewCard = { song ->
            val cur = playerState.currentSong
            val isCurrent = cur != null && cur.id == song.id && cur.platform == song.platform
            if (!isCurrent) actions.playSong(song, history)
            onOpenPlayer()
        },
        onRemove = library::removeFromHistory,
        removeLabel = "移除",
    )
}
