package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.settings.SettingsPage
import com.aesthetic.music.ui.settings.SettingsScreen
import kotlinx.coroutines.CoroutineScope

/**
 * 设置页。
 *
 * 本页只保留「常用设置 / 播放与音效 / 关于」：
 * 「插件管理」「账号与同步」在个人页都有入口，重复入口已移除；
 * 「桌面悬浮胶囊」已整体删除。
 */
@Composable
fun SettingsDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    actions: ShellActions,
    /** 壳层长生命周期 scope：检查更新/下载需在离开本页后继续，故不使用页面自身的 scope */
    scope: CoroutineScope,
) {
    val context = LocalContext.current

    val themeMode by viewModel.themeMode.collectAsState()
    val qualityLevel by viewModel.quality.collectAsState()
    val userPrefs by viewModel.settings.prefs.collectAsState()

    SettingsScreen(
        themeMode = themeMode,
        quality = qualityLevel,
        userPrefs = userPrefs,
        onBack = nav.popBack,
        // 设置项各自独立成页：主页只做入口，改设置不再需要一页滚到底
        onOpenPage = { page ->
            nav.navigate(
                when (page) {
                    SettingsPage.APPEARANCE -> ScreenRoute.SettingsAppearance
                    SettingsPage.QUALITY -> ScreenRoute.SettingsQuality
                    SettingsPage.PLAYBACK -> ScreenRoute.SettingsPlayback
                    SettingsPage.LYRICS -> ScreenRoute.SettingsLyrics
                    SettingsPage.DOWNLOAD -> ScreenRoute.SettingsDownload
                    SettingsPage.CACHE -> ScreenRoute.SettingsCache
                }.route
            )
        },
        onCheckUpdate = { onResult ->
            launchUpdateCheck(
                context = context,
                scope = scope,
                onResult = onResult,
                onError = { msg -> actions.toast(msg) },
            )
        },
    )
}
