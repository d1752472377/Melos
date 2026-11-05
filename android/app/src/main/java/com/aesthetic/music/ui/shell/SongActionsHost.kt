package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.playlist.AddToPlaylistSheet
import com.aesthetic.music.ui.playlist.SongActionSheet

/**
 * 单曲操作抽屉的共享状态：当前打开的操作菜单 / 当前加入歌单的歌曲。
 *
 * 只负责“哪首歌、开哪个抽屉”，不持有业务逻辑。
 */
@Stable
class SongActionsState {
    var actionSong by mutableStateOf<Song?>(null)
        private set
    var addToPlaylistSong by mutableStateOf<Song?>(null)
        private set

    fun showActions(song: Song) {
        actionSong = song
    }

    fun showAddToPlaylist(song: Song) {
        actionSong = null
        addToPlaylistSong = song
    }

    fun dismissActions() {
        actionSong = null
    }

    fun dismissAddToPlaylist() {
        addToPlaylistSong = null
    }
}

@Composable
fun rememberSongActionsState(): SongActionsState = remember { SongActionsState() }

/**
 * 全 App 共用的单曲操作抽屉宿主。
 *
 * 这里统一了此前散落在收藏 / 历史 / 歌手 / 歌单详情 / 搜索 / 播放器浮层的重复接线：
 * - 下一首、加入队列的提示文案；
 * - 下载入口（此前歌手页是空实现）；
 * - “添加到歌单”的级联与成功提示。
 *
 * 页面只需要决定“查看卡片 / 从当前列表移除”这两个上下文动作。
 */
@Composable
fun SongActionsHost(
    state: SongActionsState,
    library: LibraryController,
    playerManager: PlayerManager,
    myPlaylists: List<MyPlaylist>,
    actions: ShellActions,
    onViewCard: ((Song) -> Unit)? = null,
    onRemove: ((Song) -> Unit)? = null,
    removeLabel: String = "移除",
) {
    state.actionSong?.let { song ->
        SongActionSheet(
            song = song,
            isFavorite = library.isFavorite(song),
            onDismiss = state::dismissActions,
            onToggleFavorite = { library.toggleFavorite(song) },
            onPlayNext = {
                playerManager.playNext(song)
                actions.toast("已设为下一首播放")
            },
            onAddToQueue = {
                playerManager.addToQueue(song)
                actions.toast("已加入播放队列")
            },
            onDownload = { actions.downloadSong(song) },
            onAddToPlaylist = { state.showAddToPlaylist(song) },
            onViewCard = onViewCard?.let { callback -> { callback(song) } },
            onRemove = onRemove?.let { callback -> { callback(song) } },
            removeLabel = removeLabel,
        )
    }

    state.addToPlaylistSong?.let { song ->
        AddToPlaylistSheet(
            song = song,
            myPlaylists = myPlaylists,
            onDismiss = state::dismissAddToPlaylist,
            onSelectPlaylist = { playlistId ->
                library.addSongsToPlaylist(playlistId, listOf(song))
                val target = myPlaylists.firstOrNull { it.id == playlistId }
                actions.toast("已添加到「${target?.name ?: "歌单"}」")
                state.dismissAddToPlaylist()
            },
            onCreateAndAdd = { newName ->
                library.createPlaylist(newName, listOf(song))
                actions.toast("已创建并加入「$newName」")
                state.dismissAddToPlaylist()
            },
        )
    }
}
