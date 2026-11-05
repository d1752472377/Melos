package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.data.settings.ListeningStats
import com.aesthetic.music.ui.settings.SettingsController
import com.aesthetic.music.ui.stats.ListeningStatsScreen
import kotlinx.coroutines.flow.StateFlow

/**
 * 听歌统计页。
 *
 * 进页面时刷新一次明细（播放中统计仍在内存里累加，刷新即可拿到最新值），
 * 两个开关直接写设置（[SettingsController] 统一落盘）。
 */
@Composable
fun ListeningStatsDestination(
    listeningStats: StateFlow<ListeningStats>,
    onRefresh: () -> Unit,
    settings: SettingsController,
    nav: ShellNav,
) {
    val stats by listeningStats.collectAsState()
    val prefs by settings.prefs.collectAsState()

    LaunchedEffect(Unit) { onRefresh() }

    ListeningStatsScreen(
        stats = stats,
        recording = prefs.statsRecording,
        onBack = nav.popBack,
    )
}
