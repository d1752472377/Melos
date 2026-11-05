package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.account.AuthController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.profile.CloudManageScreen

/** 账号与云端同步页 */
@Composable
fun CloudManageDestination(
    auth: AuthController,
    library: LibraryController,
    nav: ShellNav,
) {
    val lastSyncedAt by library.lastSyncedAt.collectAsState()
    val lastSyncStatus by library.lastSyncStatus.collectAsState()
    val isSyncing by library.isSyncing.collectAsState()
    val user by auth.user.collectAsState()
    val sessions by auth.sessions.collectAsState()
    val isLoadingSessions by auth.isLoadingSessions.collectAsState()

    CloudManageScreen(
        user = user,
        sessions = sessions,
        isLoadingSessions = isLoadingSessions,
        lastSyncedAt = lastSyncedAt,
        lastSyncStatus = lastSyncStatus,
        isSyncing = isSyncing,
        onBack = nav.popBack,
        onLoadSessions = { auth.loadSessions() },
        onRevokeSession = { auth.revokeSession(it) },
        onLogout = { auth.logout() },
        onLogoutAll = { auth.logoutAll() },
        onPerformSync = { library.performCloudSync() },
    )
}
