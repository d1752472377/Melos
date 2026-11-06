package com.aesthetic.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aesthetic.music.data.settings.StatusBarMode
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.ui.theme.AppSpacing

/**
 * 主题与外观设置页：主题模式 / 系统状态栏。
 *
 * 两个分段选择都由外部传入当前值（而不是本页去 SettingsStore 一次性读快照）：
 * 读快照的话改完设置不会触发重组，选中态要退出页面再进来才更新。
 */
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

        SettingsGap(10)

        SettingsNote("· 状态栏选「仅播放页」时，进入播放页会隐藏状态栏，退出后自动恢复。")
        SettingsNote("· 部分系统（如 MIUI/HarmonyOS）对隐藏状态栏的策略不同，可能仍会保留一条细线。")
    }
}
