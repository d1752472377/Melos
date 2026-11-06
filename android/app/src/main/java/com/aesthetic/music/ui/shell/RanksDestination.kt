package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.rank.RankController
import com.aesthetic.music.ui.rank.RankPage

@Composable
fun RanksDestination(
    rank: RankController,
    playlistDetail: PlaylistDetailController,
    nav: ShellNav,
) {
    val platform by rank.platform.collectAsState()
    val ranks by rank.ranks.collectAsState()
    val isLoading by rank.isLoading.collectAsState()
    val error by rank.error.collectAsState()
    val loadedAt by rank.loadedAt.collectAsState()

    LaunchedEffect(Unit) { rank.ensureLoaded() }

    RankPage(
        platform = platform,
        ranks = ranks,
        isLoading = isLoading,
        error = error,
        onSelectPlatform = { rank.select(it) },
        onRetry = { rank.retry() },
        loadedAt = loadedAt,
        onBack = nav.popBack,
        onOpenRank = { rankItem ->
            playlistDetail.openRank(rankItem)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
    )
}
