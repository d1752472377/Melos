package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.AppTab
import com.aesthetic.music.ui.home.ContentController
import com.aesthetic.music.ui.home.HomeScreen
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistDetailController

/** 主页 */
@Composable
fun HomeDestination(
    content: ContentController,
    playlistDetail: PlaylistDetailController,
    nav: ShellNav,
    actions: ShellActions,
    isPlaying: Boolean = false,
) {
    val dailyPlaylists by content.dailyPlaylists.collectAsState()
    val newSongs by content.newSongs.collectAsState()
    val hotSongs by content.hotSongs.collectAsState()
    val hotPlaylists by content.hotPlaylists.collectAsState()
    val ranks by content.ranks.collectAsState()
    val contentError by content.contentError.collectAsState()
    val isLoadingContent by content.isLoadingContent.collectAsState()
    val recommendPlaylistSongs by content.recommendPlaylistSongs.collectAsState()
    val isLoadingRecommendSongs by content.isLoadingRecommendSongs.collectAsState()
    val newSongRegions by content.newSongRegions.collectAsState()
    HomeScreen(
        dailyPlaylists = dailyPlaylists,
        newSongs = newSongs,
        hotSongs = hotSongs,
        hotPlaylists = hotPlaylists,
        ranks = ranks,
        recommendSongs = recommendPlaylistSongs,
        isLoadingRecommendSongs = isLoadingRecommendSongs,
        onLoadRecommendSongs = content::loadRecommendPlaylistSongs,
        newSongRegions = newSongRegions,
        contentError = contentError,
        isLoading = isLoadingContent,
        onRetryContent = { content.refreshContent() },
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onSearchBarClick = { nav.navigateSingleTop(ScreenRoute.Search.route) },
        // GZ 的搜索栏末尾是「听歌识曲」（HomeScreenKt.java:11352-11378）。Melos 暂无识曲能力，
        // 这里沿用项目既有的「功能开发中」提示写法（AppShell 里对账号导入也是这么处理的）。
        onMicClick = { actions.toast("听歌识曲功能开发中，敬请期待") },
        onOpenPlaylistDetail = { pl ->
            playlistDetail.open(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        // 三大入口第一张卡「猜你喜欢」→ 独立推荐页（不再跳歌单详情）
        onOpenGuess = { nav.navigateSingleTop(ScreenRoute.GuessYouLike.route) },
        onOpenRanksPage = { nav.navigate(ScreenRoute.Ranks.route) },
        onOpenFm = { nav.switchTab(ScreenRoute.Fm.route, AppTab.SHEET) },
        isPlaying = isPlaying,
    )
}
