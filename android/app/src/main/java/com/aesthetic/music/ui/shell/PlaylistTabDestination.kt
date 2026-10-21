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
    val context = androidx.compose.ui.platform.LocalContext.current

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
        onSharePlaylist = { name, subtitle ->
            val ctx = context
            val text = "歌单《$name》 · $subtitle" + "\n" + "—— 来自 Melos"
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, name)
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            ctx.startActivity(android.content.Intent.createChooser(intent, "分享歌单"))
        },
        onRefreshFavorite = { item -> viewModel.refreshFavoritePlaylist(item) },
        onRemoveFavorite = { item -> viewModel.toggleFavoritePlaylist(item) },
    )
}
