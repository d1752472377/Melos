package com.aesthetic.music.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import com.aesthetic.music.data.settings.UserPrefs

@Composable
fun LyricSettingsScreen(
    prefs: UserPrefs,
    onBack: () -> Unit,
    onTranslationChange: (Boolean) -> Unit,
    onRomanizationChange: (Boolean) -> Unit,
    onOnlineChange: (Boolean) -> Unit,
    onLocalChange: (Boolean) -> Unit,
) {
    SettingsPageScaffold(title = "歌词显示", onBack = onBack) {
        SettingsSection(title = "歌词内容") {
            SettingsSwitchRow(
                icon = Icons.Filled.Translate,
                title = "翻译歌词",
                checked = prefs.lyricTranslation,
                onCheckedChange = onTranslationChange,
            )
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.Filled.TextFields,
                title = "音译歌词",
                checked = prefs.lyricRomanization,
                onCheckedChange = onRomanizationChange,
            )
        }

        SettingsGap()

        SettingsSection(title = "歌词来源") {
            SettingsSwitchRow(
                icon = Icons.Filled.CloudDownload,
                title = "在线歌词",
                checked = prefs.lyricOnline,
                onCheckedChange = onOnlineChange,
            )
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.Filled.FolderOpen,
                title = "本地歌词",
                checked = prefs.lyricLocal,
                onCheckedChange = onLocalChange,
            )
        }
    }
}
