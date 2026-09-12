package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.profile.MyPlaylistsScreen

/** 我的歌单页 */
@Composable
fun MyPlaylistsDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val importPreview by viewModel.playlistImportPreview.collectAsState()

    MyPlaylistsScreen(
        playlists = myPlaylists,
        currentSong = playerState.currentSong,
        onBack = {
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onOpenPlaylist = { id ->
            viewModel.openMyPlaylist(id)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        importPreview = importPreview,
        onResolveImport = viewModel::resolvePlaylistImportPreview,
        onClearImport = viewModel::clearPlaylistImportPreview,
        onCreate = { result ->
            val id = viewModel.createPlaylist(
                name = result.name,
                initialSongs = result.importedSongs,
                cover = result.cover,
                sourcePlatform = result.sourcePlatform,
                sourcePlaylistId = result.sourcePlaylistId,
                sourceName = result.sourceName,
            )
            if (id != null) {
                actions.toast(
                    if (result.importedSongs.isEmpty()) {
                        "已创建歌单《${result.name}》"
                    } else {
                        "已创建《${result.name}》，含 ${result.importedSongs.size} 首原始曲目"
                    }
                )
            }
        },
        onEdit = { id, result ->
            viewModel.updatePlaylistInfo(id, name = result.name, cover = result.cover)
            actions.toast("歌单信息已更新")
        },
        onDelete = viewModel::deletePlaylist,
        onAddCurrent = { id, song ->
            viewModel.addSongsToPlaylist(id, listOf(song))
            actions.toast("已加入歌单")
        },
        onToast = actions.toast,
    )
}
