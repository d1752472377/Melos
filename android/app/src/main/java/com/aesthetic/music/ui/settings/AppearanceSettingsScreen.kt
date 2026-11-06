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
    SettingsPageScaffold(title = "主题与外观", onBack = onBack) {
        SettingsSection(title = "主题设置") {
            Column(Modifier.padding(top = AppSpacing.xxs)) {
                SettingsSegmentRow(
                    title = "主题模式",
                    options = listOf(
                        SettingOption("自动"),
                        SettingOption("浅色"),
                        SettingOption("深色"),
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
                    options = listOf(
                        SettingOption(StatusBarMode.ALWAYS.label),
                        SettingOption(StatusBarMode.PLAYER_ONLY.label),
                        SettingOption(StatusBarMode.HIDDEN.label),
                    ),
                    selectedIndex = StatusBarMode.entries.indexOf(statusBarMode).coerceAtLeast(0),
                    onSelect = { idx -> onStatusBarModeChange(StatusBarMode.entries[idx]) },
                )
            }
        }

    }
}
