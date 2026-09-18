package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.profile.CloudManageScreen

/** 账号与云端同步页 */
@Composable
fun CloudManageDestination(viewModel: MainViewModel, nav: ShellNav) {
    val syncServerUrl by viewModel.syncServerUrl.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val lastSyncStatus by viewModel.lastSyncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val sessions by viewModel.loginSessions.collectAsState()
    val isLoadingSessions by viewModel.isLoadingSessions.collectAsState()

    CloudManageScreen(
        serverUrl = syncServerUrl,
        isLoggedIn = isLoggedIn,
        user = user,
        sessions = sessions,
        isLoadingSessions = isLoadingSessions,
        lastSyncedAt = lastSyncedAt,
        lastSyncStatus = lastSyncStatus,
        isSyncing = isSyncing,
        onBack = nav.popBack,
        onLoadSessions = { viewModel.loadLoginSessions() },
        onRevokeSession = { viewModel.revokeLoginSession(it) },
        onLogout = { viewModel.logout() },
        onLogoutAll = { viewModel.logoutAllDevices() },
        onPerformSync = { viewModel.performCloudSync() },
    )
}
