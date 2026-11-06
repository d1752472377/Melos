package com.aesthetic.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.ui.theme.AppSpacing

@Composable
fun QualitySettingsScreen(
    quality: QualityLevel,
    onBack: () -> Unit,
    onQualityChange: (QualityLevel) -> Unit,
) {
    SettingsPageScaffold(title = "音质", subtitle = "播放与下载使用的音频质量", onBack = onBack) {
        SettingsSection(title = "音质等级") {
            SettingsOptionList(
                title = "播放音质",
                options = listOf(
                    SettingOption("标准 128k", "流量最省，适合移动网络"),
                    SettingOption("高品质 320k", "音质与流量的平衡"),
                    SettingOption("无损 FLAC", "音质最好，文件更大"),
                ),
                selectedIndex = QualityLevel.entries.indexOf(quality).coerceAtLeast(0),
                onSelect = { idx -> onQualityChange(QualityLevel.entries[idx]) },
            )
        }

        SettingsGap()

        SettingsSection(title = "后续补充") {
            Column(Modifier.padding(AppSpacing.xxl)) {
                SettingsValueRow(
                    title = "均衡器 / 音效",
                    value = "敬请期待",
                    subtitle = "自定义均衡器、环绕声等",
                )
                SettingsValueRow(
                    title = "按网络自动切换音质",
                    value = "敬请期待",
                    subtitle = "WiFi 用无损、移动网络降级",
                )
                SettingsValueRow(
                    title = "下载音质",
                    value = "跟随播放音质",
                    subtitle = "可单独指定下载使用的音质",
                )
            }
        }

    }
}
