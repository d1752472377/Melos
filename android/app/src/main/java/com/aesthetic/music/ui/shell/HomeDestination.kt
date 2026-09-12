package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.home.HomeScreen
import com.aesthetic.music.ui.navigation.ScreenRoute

/** 主页 */
@Composable
fun HomeDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
) {
    val dailyPlaylists by viewModel.dailyPlaylists.collectAsState()
    val newSongs by viewModel.newSongs.collectAsState()
    val hotSongs by viewModel.hotSongs.collectAsState()
    val hotPlaylists by viewModel.hotPlaylists.collectAsState()
    val ranks by viewModel.ranks.collectAsState()
    val homeCategories by viewModel.homeCategories.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val contentError by viewModel.contentError.collectAsState()
    val isLoadingContent by viewModel.isLoadingContent.collectAsState()
    HomeScreen(
        dailyPlaylists = dailyPlaylists,
        newSongs = newSongs,
        hotSongs = hotSongs,
        hotPlaylists = hotPlaylists,
        ranks = ranks,
        homeCategories = homeCategories,
        favorites = favorites,
        contentError = contentError,
        isLoading = isLoadingContent,
        onRetryContent = { viewModel.refreshContent() },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onSearchBarClick = { nav.navigateSingleTop(ScreenRoute.Search.route) },
        onOpenPlaylistDetail = { pl ->
            viewModel.openPlaylistDetail(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        onOpenSheet = { nav.navigate(ScreenRoute.SheetSquare.route) },
        onOpenRanksPage = { nav.navigate(ScreenRoute.Ranks.route) },
        onToggleFavorite = viewModel::toggleFavorite,
    )
}
