package com.aesthetic.music.ui.components

import androidx.compose.runtime.Composable
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist

/**
 * 单曲操作抽屉宿主：搜索页 / 歌手页共用。
 *
 * 调用方只需维护两个状态（当前操作歌 / 当前加入歌单的歌），
 * 本组件负责渲染 SongActionSheet 与 AddToPlaylistSheet 的级联关系。
 */
@Composable
fun SongActionsHost(
    songForActionMenu: Song?,
    songForAddToPlaylist: Song?,
    myPlaylists: List<MyPlaylist>,
    isSongFavorite: (Song) -> Boolean,
    onDismissActionMenu: () -> Unit,
    onDismissAddToPlaylist: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onRequestAddToPlaylist: (Song) -> Unit,
    onSelectPlaylist: (String, Song) -> Unit,
    onCreatePlaylist: (String, Song) -> Unit,
) {
    songForActionMenu?.let { song ->
        SongActionSheet(
            song = song,
            isFavorite = isSongFavorite(song),
            onDismiss = onDismissActionMenu,
            onToggleFavorite = { onToggleFavorite(song) },
            onPlayNext = { onPlayNext(song) },
            onAddToQueue = { onAddToQueue(song) },
            onDownload = { onDownload(song) },
            onAddToPlaylist = { onRequestAddToPlaylist(song) },
        )
    }

    songForAddToPlaylist?.let { song ->
        AddToPlaylistSheet(
            song = song,
            myPlaylists = myPlaylists,
            onDismiss = onDismissAddToPlaylist,
            onSelectPlaylist = { playlistId -> onSelectPlaylist(playlistId, song) },
            onCreateAndAdd = { newName -> onCreatePlaylist(newName, song) },
        )
    }
}