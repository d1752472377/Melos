package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.settings.SettingsController
import com.aesthetic.music.ui.settings.SettingsPage
import com.aesthetic.music.ui.settings.SettingsScreen

/**
 * 设置页。
 *
 * 本页只保留「常用设置 / 播放与音效 / 关于」：
 * 「插件管理」「账号与同步」在个人页都有入口，重复入口已移除；
 * 「桌面悬浮胶囊」已整体删除。
 */
@Composable
fun SettingsDestination(
    settings: SettingsController,
    isCheckingUpdate: Boolean,
    onCheckUpdate: (onResult: (String) -> Unit) -> Unit,
    nav: ShellNav,
) {
    val themeMode by settings.themeMode.collectAsState()
    val qualityLevel by settings.quality.collectAsState()
    val userPrefs by settings.prefs.collectAsState()

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
                    SettingsPage.EQUALIZER -> ScreenRoute.Equalizer
                    SettingsPage.LYRICS -> ScreenRoute.SettingsLyrics
                    SettingsPage.DOWNLOAD -> ScreenRoute.SettingsDownload
                    SettingsPage.CACHE -> ScreenRoute.SettingsCache
                }.route
            )
        },
        onCheckUpdate = onCheckUpdate,
        isCheckingUpdate = isCheckingUpdate,
    )
}
