package com.aesthetic.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aesthetic.music.data.settings.StatusBarMode
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.ui.theme.AppSpacing

@Composable
fun AppearanceSettingsScreen(
    themeMode: ThemeMode,
    statusBarMode: StatusBarMode,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onStatusBarModeChange: (StatusBarMode) -> Unit,
) {
    SettingsPageScaffold(title = "主题与外观", subtitle = "主题模式、状态栏与桌面显示", onBack = onBack) {
        SettingsSection(title = "主题设置") {
            Column(Modifier.padding(top = AppSpacing.xxs)) {
                SettingsSegmentRow(
                    title = "主题模式",
                    subtitle = "深浅色外观的跟随方式",
                    options = listOf(
                        SettingOption("自动", "跟随系统深浅自动切换"),
                        SettingOption("浅色", "始终保持浅色"),
                        SettingOption("深色", "始终保持深色"),
                    ),
                    selectedIndex = ThemeMode.entries.indexOf(themeMode).coerceAtLeast(0),
                    onSelect = { idx -> onThemeModeChange(ThemeMode.entries[idx]) },
                )
            }
        }

        SettingsGap()

        SettingsSection(title = "系统状态栏") {
            Column(Modifier.padding(top = AppSpacing.xxs)) {
                SettingsSegmentRow(
                    title = "状态栏显示",
                    subtitle = "控制页面顶部系统状态栏的显示方式",
                    options = listOf(
                        SettingOption(StatusBarMode.ALWAYS.label, StatusBarMode.ALWAYS.desc),
                        SettingOption(StatusBarMode.PLAYER_ONLY.label, StatusBarMode.PLAYER_ONLY.desc),
                        SettingOption(StatusBarMode.HIDDEN.label, StatusBarMode.HIDDEN.desc),
                    ),
                    selectedIndex = StatusBarMode.entries.indexOf(statusBarMode).coerceAtLeast(0),
                    onSelect = { idx -> onStatusBarModeChange(StatusBarMode.entries[idx]) },
                )
            }
        }

    }
}
