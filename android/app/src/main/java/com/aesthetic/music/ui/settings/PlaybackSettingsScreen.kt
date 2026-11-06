package com.aesthetic.music.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.runtime.Composable

@Composable
fun PlaybackSettingsScreen(
    prefs: com.aesthetic.music.data.settings.UserPrefs,
    onBack: () -> Unit,
    onPreloadNextChange: (Boolean) -> Unit,
    onCrossFadeChange: (Boolean) -> Unit,
    onResumeAfterInterruptChange: (Boolean) -> Unit,
    onMixWithOthersChange: (Boolean) -> Unit,
) {
    SettingsPageScaffold(title = "播放设置", onBack = onBack) {
        SettingsSection(title = "播放行为") {
            SettingsSwitchRow(
                icon = Icons.Filled.Bolt,
                title = "预加载下一首",
                checked = prefs.preloadNext,
                onCheckedChange = onPreloadNextChange,
            )
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.Filled.GraphicEq,
                title = "淡入淡出",
                checked = prefs.crossFade,
                onCheckedChange = onCrossFadeChange,
            )
        }

        SettingsGap()

        SettingsSection(title = "系统音频交互") {
            SettingsSwitchRow(
                icon = Icons.Filled.PlayCircleOutline,
                title = "打断自动恢复",
                checked = prefs.resumeAfterInterrupt,
                onCheckedChange = onResumeAfterInterruptChange,
            )
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "混合播放",
                checked = prefs.mixWithOthers,
                onCheckedChange = onMixWithOthersChange,
            )
        }
    }
}
