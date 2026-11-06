package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.data.settings.ListeningStats
import com.aesthetic.music.ui.settings.SettingsController
import com.aesthetic.music.ui.stats.ListeningStatsScreen
import kotlinx.coroutines.flow.StateFlow

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
