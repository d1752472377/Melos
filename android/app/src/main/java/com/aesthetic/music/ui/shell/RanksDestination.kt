package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.rank.RankPage

/** 排行榜列表页 */
@Composable
fun RanksDestination(viewModel: MainViewModel, nav: ShellNav) {
    val ranks by viewModel.ranks.collectAsState()
    RankPage(
        ranks = ranks,
        onBack = nav.popBack,
        onOpenRank = { rankItem ->
            viewModel.openRankDetail(rankItem)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
    )
}
