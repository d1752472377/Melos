package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.artist.ArtistAllSongsScreen
import com.aesthetic.music.ui.artist.ArtistController
import com.aesthetic.music.ui.artist.ArtistScreen
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.playlist.PlaylistDetailScreen
import com.aesthetic.music.ui.shell.ShellActions
import com.aesthetic.music.ui.shell.SongActionsHost
import com.aesthetic.music.ui.shell.rememberSongActionsState

@Composable
fun PlaylistDetailDestination(
    playlistDetail: PlaylistDetailController,
    library: LibraryController,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    val detailItem by playlistDetail.showDetail.collectAsState()
    val playlistSongs by playlistDetail.songs.collectAsState()
    val isLoadingPlaylist by playlistDetail.isLoading.collectAsState()
    val playlistError by playlistDetail.error.collectAsState()
    val myPlaylists by library.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    val detail = detailItem ?: return
    val isFav = library.isPlaylistFavorite(detail.id)
    PlaylistDetailScreen(
        playlist = detail,
        songs = playlistSongs,
        isLoading = isLoadingPlaylist,
        error = playlistError,
        isFavorite = isFav,
        currentSong = playerState.currentSong,
        onBack = {
            playlistDetail.close()
            onPageBackground(null, false)
            nav.popBack()
        },
        onRetry = { playlistDetail.open(detail) },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onPlayAll = { songs -> actions.playAll(songs) },
        onToggleFavoritePlaylist = { library.toggleFavoritePlaylist(detail) },
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

@Composable
fun AlbumDetailDestination(
    playlistDetail: PlaylistDetailController,
    library: LibraryController,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    val detail by playlistDetail.showDetail.collectAsState()
    val albumDetailSongs by playlistDetail.songs.collectAsState()
    val isLoadingAlbum by playlistDetail.isLoading.collectAsState()
    val albumError by playlistDetail.error.collectAsState()
    val myPlaylists by library.myPlaylists.collectAsState()
    val playerState by playerManager.state.collectAsState()
    val songActions = rememberSongActionsState()

    val albumItem = detail ?: return
    val isFav = library.isPlaylistFavorite(albumItem.id)
    PlaylistDetailScreen(
        playlist = albumItem,
        songs = albumDetailSongs,
        isLoading = isLoadingAlbum,
        error = albumError,
        isFavorite = isFav,
        currentSong = playerState.currentSong,
        onBack = {
            playlistDetail.close()
            onPageBackground(null, false)
            nav.popBack()
        },
        onRetry = { playlistDetail.retry() },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onPlayAll = { songs -> actions.playAll(songs) },
        onToggleFavoritePlaylist = { library.toggleFavoritePlaylist(albumItem) },
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

@Composable
fun ArtistDetailDestination(
    artist: ArtistController,
    library: LibraryController,
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val artistRef by artist.showArtist.collectAsState()
    val artistDetail by artist.artistDetail.collectAsState()
    val isLoadingArtist by artist.isLoading.collectAsState()
    val artistError by artist.error.collectAsState()
    val myPlaylists by library.myPlaylists.collectAsState()

    val playerState by playerManager.state.collectAsState()
    val currentSong = playerState.currentSong
    val songActions = rememberSongActionsState()

    val ref = artistRef ?: return
    val info = artistDetail?.info
    val artistSongs = artistDetail?.songs.orEmpty()
    val allSongs by artist.allSongs.collectAsState()

    ArtistScreen(
        name = info?.name?.takeIf { it.isNotBlank() } ?: ref.name,
        alias = info?.alias ?: ref.alias,
        avatar = info?.avatar ?: ref.avatar,
        songs = artistSongs,
        isLoading = isLoadingArtist,
        errorMessage = artistError,
        onRetry = { artist.open(ref) },
        currentSong = currentSong,
        onPlaySong = { actions.playSong(it, if (allSongs.isNotEmpty()) allSongs else artistSongs) },
        onPlayAll = { actions.playAll(if (allSongs.isNotEmpty()) allSongs else artistSongs) },
        onShuffle = {
            val list = if (allSongs.isNotEmpty()) allSongs else artistSongs
            if (list.isNotEmpty()) actions.playAll(list.shuffled())
        },
        onMoreSong = songActions::showActions,
        onOpenAllSongs = { nav.navigate(ScreenRoute.ArtistAllSongs.route) },
    )

    SongActionsHost(
        state = songActions,
        library = library,
        actions = actions,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
    )
}

@Composable
fun ArtistAllSongsDestination(
    artist: ArtistController,
    library: LibraryController,
    actions: ShellActions,
    playerManager: PlayerManager,
) {
    val artistRef by artist.showArtist.collectAsState()
    val artistDetail by artist.artistDetail.collectAsState()
    val allSongs by artist.allSongs.collectAsState()
    val isLoadingAllSongs by artist.isLoadingAllSongs.collectAsState()
    val allSongsError by artist.allSongsError.collectAsState()
    val favorites by library.favorites.collectAsState()
    val myPlaylists by library.myPlaylists.collectAsState()

    val playerState by playerManager.state.collectAsState()
    val currentSong = playerState.currentSong
    val songActions = rememberSongActionsState()

    val name = artistDetail?.info?.name?.takeIf { it.isNotBlank() }
        ?: artistRef?.name
        ?: "歌手"
    val alias = artistDetail?.info?.alias ?: artistRef?.alias
    val avatar = artistDetail?.info?.avatar ?: artistRef?.avatar
    val songs = if (allSongs.isNotEmpty()) allSongs else artistDetail?.songs.orEmpty()

    ArtistAllSongsScreen(
        artistName = name,
        alias = alias,
        avatar = avatar,
        songs = songs,
        isLoading = isLoadingAllSongs && songs.isEmpty(),
        isLoadingMore = isLoadingAllSongs && songs.isNotEmpty(),
        errorMessage = if (songs.isEmpty()) allSongsError else null,
        onRetry = { artist.loadAllSongs() },
        favorites = favorites,
        currentSong = currentSong,
        onPlaySong = { actions.playSong(it, songs) },
        onPlayAll = { actions.playAll(songs) },
        onShuffle = { if (songs.isNotEmpty()) actions.playAll(songs.shuffled()) },
        onToggleFavorite = { library.toggleFavorite(it) },
        onPlayNext = { playerManager.playNext(it) },
        onMoreSong = songActions::showActions,
    )

    SongActionsHost(
        state = songActions,
        library = library,
        actions = actions,
        playerManager = playerManager,
        myPlaylists = myPlaylists,
    )
}
