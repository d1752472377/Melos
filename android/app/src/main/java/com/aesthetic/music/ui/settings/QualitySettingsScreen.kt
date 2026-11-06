package com.aesthetic.music.ui.settings

import androidx.compose.runtime.Composable
import com.aesthetic.music.data.settings.QualityLevel

@Composable
fun QualitySettingsScreen(
    quality: QualityLevel,
    onBack: () -> Unit,
    onQualityChange: (QualityLevel) -> Unit,
) {
    SettingsPageScaffold(title = "音质", onBack = onBack) {
        SettingsSection(title = "音质等级") {
            SettingsOptionList(
                title = "播放音质",
                options = listOf(
                    SettingOption("标准 128k"),
                    SettingOption("高品质 320k"),
                    SettingOption("无损 FLAC"),
                ),
                selectedIndex = QualityLevel.entries.indexOf(quality).coerceAtLeast(0),
                onSelect = { idx -> onQualityChange(QualityLevel.entries[idx]) },
            )
        }
    }
}
