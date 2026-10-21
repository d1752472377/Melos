package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.AppTab
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.home.HomeScreen
import com.aesthetic.music.ui.navigation.ScreenRoute

/** 主页 */
@Composable
fun HomeDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    isPlaying: Boolean = false,
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
    val recommendPlaylistSongs by viewModel.recommendPlaylistSongs.collectAsState()
    val isLoadingRecommendSongs by viewModel.isLoadingRecommendSongs.collectAsState()
    val newSongRegions by viewModel.newSongRegions.collectAsState()
    HomeScreen(
        dailyPlaylists = dailyPlaylists,
        newSongs = newSongs,
        hotSongs = hotSongs,
        hotPlaylists = hotPlaylists,
        ranks = ranks,
        homeCategories = homeCategories,
        favorites = favorites,
        recommendSongs = recommendPlaylistSongs,
        isLoadingRecommendSongs = isLoadingRecommendSongs,
        onLoadRecommendSongs = viewModel::loadRecommendPlaylistSongs,
        newSongRegions = newSongRegions,
        contentError = contentError,
        isLoading = isLoadingContent,
        onRetryContent = { viewModel.refreshContent() },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onSearchBarClick = { nav.navigateSingleTop(ScreenRoute.Search.route) },
        // GZ 的搜索栏末尾是「听歌识曲」（HomeScreenKt.java:11352-11378）。Melos 暂无识曲能力，
        // 这里沿用项目既有的「功能开发中」提示写法（AppShell 里对账号导入也是这么处理的）。
        onMicClick = { viewModel.showToast("听歌识曲功能开发中，敬请期待") },
        onOpenPlaylistDetail = { pl ->
            viewModel.openPlaylistDetail(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        // 三大入口第一张卡「猜你喜欢」→ 独立推荐页（不再跳歌单详情）
        onOpenGuess = { nav.navigateSingleTop(ScreenRoute.GuessYouLike.route) },
        onOpenRanksPage = { nav.navigate(ScreenRoute.Ranks.route) },
        onOpenFm = { nav.switchTab(ScreenRoute.Fm.route, AppTab.SHEET) },
        onToggleFavorite = viewModel::toggleFavorite,
        isPlaying = isPlaying,
    )
}
