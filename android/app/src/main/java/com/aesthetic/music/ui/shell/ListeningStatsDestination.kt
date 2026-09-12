package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.profile.ListeningStatsScreen

/** 听歌时长页 */
@Composable
fun ListeningStatsDestination(viewModel: MainViewModel, nav: ShellNav) {
    val localStats by viewModel.localStats.collectAsState()
    ListeningStatsScreen(
        localStats = localStats,
        onBack = nav.popBack,
    )
}
