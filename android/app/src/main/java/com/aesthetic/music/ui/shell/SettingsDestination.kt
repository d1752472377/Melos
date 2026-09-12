package com.aesthetic.music.ui.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.service.FloatCapsuleManager
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.settings.SettingsPage
import com.aesthetic.music.ui.settings.SettingsScreen
import kotlinx.coroutines.CoroutineScope

/** 设置页 */
@Composable
fun SettingsDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    /** 壳层长生命周期 scope：检查更新/下载需在离开本页后继续，故不使用页面自身的 scope */
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val themeMode by viewModel.themeMode.collectAsState()
    val qualityLevel by viewModel.quality.collectAsState()
    val activeScriptInfo by viewModel.activeScriptInfo.collectAsState()
    val scriptCatalog by viewModel.scriptCatalog.collectAsState()
    val isEngineReady by viewModel.isEngineReady.collectAsState()
    val engineError by viewModel.engineError.collectAsState()
    val isSyncConfigured by viewModel.isSyncConfigured.collectAsState()
    val syncServerUrl by viewModel.syncServerUrl.collectAsState()
    val syncToken by viewModel.syncToken.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val lastSyncStatus by viewModel.lastSyncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val userPrefs by viewModel.settings.prefs.collectAsState()

    // 悬浮胶囊开关
    var capsuleEnabled by remember { mutableStateOf(SettingsStore.isFloatingCapsuleEnabled()) }

    SettingsScreen(
        themeMode = themeMode,
        quality = qualityLevel,
        userPrefs = userPrefs,
        floatingCapsuleEnabled = capsuleEnabled,
        activeScript = activeScriptInfo,
        scriptCatalog = scriptCatalog,
        isEngineReady = isEngineReady,
        engineError = engineError,
        onRetryLoadScript = viewModel::retryLoadScript,
        onSwitchScript = viewModel::switchScript,
        isSyncConfigured = isSyncConfigured,
        syncServerUrl = syncServerUrl,
        syncToken = syncToken,
        lastSyncedAt = lastSyncedAt,
        lastSyncStatus = lastSyncStatus,
        isSyncing = isSyncing,
        onBack = nav.popBack,
        // 设置项各自独立成页：主页只做入口，改设置不再需要一页滚到底
        onOpenPage = { page ->
            nav.navigate(
                when (page) {
                    SettingsPage.APPEARANCE -> ScreenRoute.SettingsAppearance
                    SettingsPage.QUALITY -> ScreenRoute.SettingsQuality
                    SettingsPage.PLAYBACK -> ScreenRoute.SettingsPlayback
                    SettingsPage.LYRICS -> ScreenRoute.SettingsLyrics
                    SettingsPage.MEDIA_CARD -> ScreenRoute.SettingsMediaCard
                    SettingsPage.DOWNLOAD -> ScreenRoute.SettingsDownload
                    SettingsPage.CACHE -> ScreenRoute.SettingsCache
                }.route
            )
        },
        onFloatingCapsuleChange = { enabled ->
            if (enabled) {
                if (FloatCapsuleManager.hasPermission(context)) {
                    SettingsStore.setFloatingCapsuleEnabled(true)
                    capsuleEnabled = true
                } else {
                    SettingsStore.setFloatingCapsuleEnabled(false)
                    capsuleEnabled = false
                    FloatCapsuleManager.openPermissionSettings(context)
                    actions.toast("请在系统设置中开启「悬浮窗」权限，返回后重新打开开关")
                }
            } else {
                SettingsStore.setFloatingCapsuleEnabled(false)
                capsuleEnabled = false
                FloatCapsuleManager.hide()
            }
        },
        onCheckUpdate = { onResult ->
            launchUpdateCheck(
                context = context,
                scope = scope,
                onResult = onResult,
                onError = { msg -> actions.toast(msg) },
            )
        },
        onSaveSyncConfig = viewModel::saveSyncConfig,
        onTestSyncConnection = viewModel::testSyncConnection,
        onPerformSync = viewModel::performCloudSync,
        onImportScriptText = viewModel::importScriptFromText,
        onImportScriptUrl = viewModel::importScriptFromUrl,
        onUnloadScript = viewModel::unloadScript,
    )
}
