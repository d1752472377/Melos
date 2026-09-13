package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.profile.CloudManageScreen

/** 账号与同步页（服务器地址 + 登录注册 + 同步状态） */
@Composable
fun CloudManageDestination(viewModel: MainViewModel, nav: ShellNav) {
    val syncServerUrl by viewModel.syncServerUrl.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val lastSyncStatus by viewModel.lastSyncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isTestingSync by viewModel.isTestingSync.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val isAuthBusy by viewModel.isAuthBusy.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val sessions by viewModel.loginSessions.collectAsState()
    val isLoadingSessions by viewModel.isLoadingSessions.collectAsState()

    CloudManageScreen(
        serverUrl = syncServerUrl,
        // "填了服务器地址"与"能同步"是两件事：前者只要地址非空，后者还要求已登录（isSyncConfigured）。
        isServerConfigured = syncServerUrl.isNotBlank(),
        isLoggedIn = isLoggedIn,
        user = user,
        isBusy = isAuthBusy,
        authError = authError,
        sessions = sessions,
        isLoadingSessions = isLoadingSessions,
        lastSyncedAt = lastSyncedAt,
        lastSyncStatus = lastSyncStatus,
        isSyncing = isSyncing,
        isTesting = isTestingSync,
        onBack = nav.popBack,
        onSaveServerUrl = { viewModel.saveSyncServerUrl(it) },
        onTestConnection = { onResult -> viewModel.testSyncConnection(onResult) },
        onLogin = { u, p -> viewModel.login(u, p) },
        onRegister = { u, p, n -> viewModel.register(u, p, n) },
        onClearAuthError = { viewModel.clearAuthError() },
        onLoadSessions = { viewModel.loadLoginSessions() },
        onRevokeSession = { viewModel.revokeLoginSession(it) },
        onLogout = { viewModel.logout() },
        onLogoutAll = { viewModel.logoutAllDevices() },
        onPerformSync = { viewModel.performCloudSync() },
        onLogoutAndClearLocal = { viewModel.logoutAndClearLocalData() },
    )
}
