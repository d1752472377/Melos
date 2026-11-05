package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.album.AlbumController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.playlist.PlaylistDetailScreen

/** 专辑详情页（搜索页专辑 Tab 点击进入，复用歌单详情界面） */
@Composable
fun AlbumDetailDestination(
    albumPage: AlbumController,
    library: LibraryController,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    val albumDetail by albumPage.showAlbumDetail.collectAsState()
    val albumDetailSongs by albumPage.albumDetailSongs.collectAsState()
    val isLoadingAlbum by albumPage.isLoading.collectAsState()
    val albumError by albumPage.error.collectAsState()
    val myPlaylists by library.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    val album = albumDetail ?: return
    val albumPlaylistItem = remember(album, albumDetailSongs.size) {
        PlaylistItem(
            id = album.id,
            name = album.name,
            coverUrl = album.coverUrl,
            trackCount = album.size ?: albumDetailSongs.size.toLong(),
            platform = album.platform,
        )
    }
    val isFav = library.isPlaylistFavorite(album.id)
    PlaylistDetailScreen(
        playlist = albumPlaylistItem,
        songs = albumDetailSongs,
        isLoading = isLoadingAlbum,
        error = albumError,
        isFavorite = isFav,
        currentSong = playerState.currentSong,
        onBack = {
            albumPage.close()
            onPageBackground(null, false)
            nav.popBack()
        },
        onRetry = { albumPage.open(album) },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onPlayAll = { songs -> actions.playAll(songs) },
        onToggleFavoritePlaylist = { library.toggleFavoritePlaylist(albumPlaylistItem) },
        onMoreSong = songActions::showActions,
        onBackgroundColorChange = onPageBackground,
    )

    SongActionsHost(
        state = songActions,
        library = library,
        actions = actions,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
    )
}
