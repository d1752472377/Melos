package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.stats.ListeningStatsScreen

/**
 * 听歌统计页。
 *
 * 进页面时刷新一次明细（播放中统计仍在内存里累加，刷新即可拿到最新值），
 * 两个开关直接写设置（[com.aesthetic.music.ui.settings.SettingsController] 统一落盘，
 * StatsTracker 会在下一次累计时读到新值；关闭记录后新数据不再写入，历史保留）。
 */
@Composable
fun ListeningStatsDestination(viewModel: MainViewModel, nav: ShellNav) {
    val stats by viewModel.listeningStats.collectAsState()
    val prefs by viewModel.settings.prefs.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshListeningStats() }

    ListeningStatsScreen(
        stats = stats,
        recording = prefs.statsRecording,
        onBack = nav.popBack,
    )
}
