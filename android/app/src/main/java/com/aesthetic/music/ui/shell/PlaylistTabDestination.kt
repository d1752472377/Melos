package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.library.PlaylistImportController
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.playlist.PlaylistScreen

/** 歌单广场 Tab */
@Composable
fun PlaylistTabDestination(
    library: LibraryController,
    playlistImport: PlaylistImportController,
    playlistDetail: PlaylistDetailController,
    nav: ShellNav,
    actions: ShellActions,
    onOpenImportDialog: () -> Unit,
) {
    val myPlaylists by library.myPlaylists.collectAsState()
    val favoritePlaylists by library.favoritePlaylists.collectAsState()
    val importPreview by playlistImport.preview.collectAsState()
    val context = LocalContext.current

    PlaylistScreen(
        myPlaylists = myPlaylists,
        favoritePlaylists = favoritePlaylists,
        onOpenMyPlaylist = { id ->
            library.myPlaylist(id)?.let(playlistDetail::showLocalPlaylist)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        onOpenPlaylistDetail = { pl ->
            playlistDetail.open(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        importPreview = importPreview,
        onResolveImport = playlistImport::resolveForPreview,
        onClearImport = playlistImport::clearPreview,
        onCreatePlaylist = { result ->
            val id = library.createPlaylist(
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
            library.updatePlaylistInfo(id, name = result.name, cover = result.cover)
            actions.toast("歌单信息已更新")
        },
        onDeletePlaylist = library::deletePlaylist,
        onOpenImport = onOpenImportDialog,
        onPlayPlaylist = { songs -> actions.playAll(songs) },
        onToast = actions.toast,
        onSharePlaylist = { name, subtitle ->
            val text = "歌单《$name》 · $subtitle" + "\n" + "—— 来自 Melos"
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, name)
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "分享歌单"))
        },
        onRefreshFavorite = library::refreshFavoritePlaylist,
        onRemoveFavorite = library::toggleFavoritePlaylist,
    )
}
