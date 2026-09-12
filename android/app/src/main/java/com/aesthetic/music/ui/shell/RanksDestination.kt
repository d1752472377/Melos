package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.rank.RankPage

/** 排行榜列表页（顶部平台 tag：网易云 / QQ / 酷狗 / 酷我） */
@Composable
fun RanksDestination(viewModel: MainViewModel, nav: ShellNav) {
    val platform by viewModel.rankPlatform.collectAsState()
    val ranks by viewModel.rankList.collectAsState()
    val isLoading by viewModel.isLoadingRanks.collectAsState()
    val error by viewModel.rankError.collectAsState()

    // 首次进入才联网；返回再进入时用控制器里的缓存，不再闪一次 loading
    LaunchedEffect(Unit) { viewModel.ensureRanksLoaded() }

    RankPage(
        platform = platform,
        ranks = ranks,
        isLoading = isLoading,
        error = error,
        onSelectPlatform = { viewModel.selectRankPlatform(it) },
        onRetry = { viewModel.retryRanks() },
        onBack = nav.popBack,
        onOpenRank = { rankItem ->
            viewModel.openRankDetail(rankItem)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
    )
}
