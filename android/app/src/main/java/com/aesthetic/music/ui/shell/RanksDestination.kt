package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.rank.RankController
import com.aesthetic.music.ui.rank.RankPage

/** 排行榜列表页（顶部平台 tag：网易云 / QQ / 酷狗 / 酷我） */
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

    // 首次进入才联网；返回再进入时用控制器里的缓存，不再闪一次 loading
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
