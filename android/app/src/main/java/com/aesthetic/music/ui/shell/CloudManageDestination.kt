package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.profile.CloudManageScreen

/** 云端管理页 */
@Composable
fun CloudManageDestination(viewModel: MainViewModel, nav: ShellNav) {
    val isSyncConfigured by viewModel.isSyncConfigured.collectAsState()
    val syncServerUrl by viewModel.syncServerUrl.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val lastSyncStatus by viewModel.lastSyncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    CloudManageScreen(
        isSyncConfigured = isSyncConfigured,
        syncServerUrl = syncServerUrl,
        lastSyncedAt = lastSyncedAt,
        lastSyncStatus = lastSyncStatus,
        isSyncing = isSyncing,
        onBack = nav.popBack,
        onOpenSettings = { nav.navigate(ScreenRoute.Settings.route) },
        onPerformSync = { viewModel.performCloudSync() },
    )
}
