package com.aesthetic.music.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.runtime.Composable

/** 播放设置页：预加载 / 淡入淡出 / 打断自动恢复 / 混合播放 */
@Composable
fun PlaybackSettingsScreen(
    prefs: com.aesthetic.music.data.settings.UserPrefs,
    onBack: () -> Unit,
    onPreloadNextChange: (Boolean) -> Unit,
    onCrossFadeChange: (Boolean) -> Unit,
    onResumeAfterInterruptChange: (Boolean) -> Unit,
    onMixWithOthersChange: (Boolean) -> Unit,
) {
    SettingsPageScaffold(title = "播放设置", subtitle = "播放行为与系统音频交互", onBack = onBack) {
        SettingsSection(title = "播放行为") {
            SettingsSwitchRow(
                icon = Icons.Filled.Bolt,
                title = "预加载下一首",
                subtitle = "提前缓存下一首歌曲，切歌更快、弱网更稳（会多用一点流量）",
                checked = prefs.preloadNext,
                onCheckedChange = onPreloadNextChange,
            )
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.Filled.GraphicEq,
                title = "淡入淡出",
                subtitle = "播放开始淡入、暂停切歌淡出，避免音量突跳",
                checked = prefs.crossFade,
                onCheckedChange = onCrossFadeChange,
            )
        }

        SettingsGap()

        SettingsSection(title = "系统音频交互") {
            SettingsSwitchRow(
                icon = Icons.Filled.PlayCircleOutline,
                title = "打断自动恢复",
                subtitle = "被来电 / 其他 App 打断暂停后，打断结束自动继续播放",
                checked = prefs.resumeAfterInterrupt,
                onCheckedChange = onResumeAfterInterruptChange,
            )
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "混合播放",
                subtitle = "与其他应用混音、互不打扰；麦克风被占用时自动暂停",
                checked = prefs.mixWithOthers,
                onCheckedChange = onMixWithOthersChange,
            )
        }

        SettingsGap(10)

        SettingsNote("· 「预加载下一首」在下拉歌单/连播时生效；开启「仅 WiFi 下载」后，预加载也只在 WiFi 下进行。")
        SettingsNote("· 「混合播放」开启后本 App 不再独占音频焦点，可与其他播放器同时出声；检测到录音/通话占用麦克风时会暂停，结束自动继续。")
        SettingsNote("· 「打断自动恢复」关闭后，被来电打断会停在暂停态，需要手动点播放。")
    }
}
