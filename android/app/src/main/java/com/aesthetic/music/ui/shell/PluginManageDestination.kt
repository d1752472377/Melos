package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.profile.PluginManageScreen

/** 插件管理页 */
@Composable
fun PluginManageDestination(viewModel: MainViewModel, nav: ShellNav) {
    val activeScriptInfo by viewModel.activeScriptInfo.collectAsState()
    val isEngineReady by viewModel.isEngineReady.collectAsState()
    PluginManageScreen(
        activeScript = activeScriptInfo,
        isEngineReady = isEngineReady,
        onBack = nav.popBack,
        onOpenSettings = { nav.navigate(ScreenRoute.Settings.route) },
        onReloadScript = { viewModel.retryLoadScript() },
    )
}
