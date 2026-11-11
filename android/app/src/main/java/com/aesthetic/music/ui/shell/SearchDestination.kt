package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.artist.ArtistController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.search.SearchController
import com.aesthetic.music.ui.search.SearchScreen
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SearchDestination(
    search: SearchController,
    artist: ArtistController,
    playlistDetail: PlaylistDetailController,
    library: LibraryController,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
    recognitionRequest: StateFlow<Boolean>,
    onRecognitionConsumed: () -> Unit,
) {
    val myPlaylists by library.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()
    val pendingRecognition by recognitionRequest.collectAsState()

    SearchScreen(
        search = search,
        currentSong = playerState.currentSong,
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onOpenArtist = { item ->
            artist.open(item)
            // 详情页是无参数路由，用单顶入栈：从同一页反复点进来时只更新内容，不再压新层
            nav.navigateSingleTop(ScreenRoute.ArtistDetail.route)
        },
        onOpenPlaylist = { pl ->
            playlistDetail.open(pl)
            nav.navigateSingleTop(ScreenRoute.PlaylistDetail.route)
        },
        onOpenAlbum = { item ->
            playlistDetail.openAlbum(item)
            nav.navigateSingleTop(ScreenRoute.AlbumDetail.route)
        },
        onMoreSong = songActions::showActions,
        openRecognition = pendingRecognition,
        onRecognitionOpened = onRecognitionConsumed,
        onOpenRecognition = { nav.navigate(ScreenRoute.Recognition.route) },
    )

    SongActionsHost(
        state = songActions,
        library = library,
        actions = actions,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
    )
}
