package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistScreen

/** 歌单广场 Tab */
@Composable
fun PlaylistTabDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
) {
    val myPlaylists by viewModel.myPlaylists.collectAsState()
    val favoritePlaylists by viewModel.favoritePlaylists.collectAsState()
    val importPreview by viewModel.playlistImportPreview.collectAsState()

    PlaylistScreen(
        myPlaylists = myPlaylists,
        favoritePlaylists = favoritePlaylists,
        onOpenMyPlaylist = { id ->
            viewModel.openMyPlaylist(id)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        onOpenPlaylistDetail = { pl ->
            viewModel.openPlaylistDetail(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        importPreview = importPreview,
        onResolveImport = viewModel::resolvePlaylistImportPreview,
        onClearImport = viewModel::clearPlaylistImportPreview,
        onCreatePlaylist = { result ->
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
        onEditPlaylist = { id, result ->
            viewModel.updatePlaylistInfo(id, name = result.name, cover = result.cover)
            actions.toast("歌单信息已更新")
        },
        onDeletePlaylist = viewModel::deletePlaylist,
        onOpenImport = { viewModel.openImportDialog() },
        onPlayPlaylist = { songs -> actions.playAll(songs) },
        onToast = actions.toast,
    )
}
