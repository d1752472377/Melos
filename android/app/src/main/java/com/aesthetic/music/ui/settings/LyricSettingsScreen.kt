package com.aesthetic.music.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import com.aesthetic.music.data.settings.UserPrefs

/** 歌词设置页：翻译 / 音译 / 歌词来源（在线、本地） */
@Composable
fun LyricSettingsScreen(
    prefs: UserPrefs,
    onBack: () -> Unit,
    onTranslationChange: (Boolean) -> Unit,
    onRomanizationChange: (Boolean) -> Unit,
    onOnlineChange: (Boolean) -> Unit,
    onLocalChange: (Boolean) -> Unit,
) {
    SettingsPageScaffold(title = "歌词显示", subtitle = "歌词内容与来源", onBack = onBack) {
        SettingsSection(title = "歌词内容") {
            SettingsSwitchRow(
                icon = Icons.Filled.Translate,
                title = "翻译歌词",
                subtitle = "歌词有官方/第三方翻译时，显示原文与译文两行",
                checked = prefs.lyricTranslation,
                onCheckedChange = onTranslationChange,
            )
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.Filled.TextFields,
                title = "音译歌词",
                subtitle = "外文歌词显示罗马音（需歌词源提供音译行，暂多数歌源未提供）",
                checked = prefs.lyricRomanization,
                onCheckedChange = onRomanizationChange,
            )
        }

        SettingsGap()

        SettingsSection(title = "歌词来源") {
            SettingsSwitchRow(
                icon = Icons.Filled.CloudDownload,
                title = "在线歌词",
                subtitle = "从音乐平台在线获取歌词",
                checked = prefs.lyricOnline,
                onCheckedChange = onOnlineChange,
            )
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.Filled.FolderOpen,
                title = "本地歌词",
                subtitle = "优先读取已下载歌词文件（缓存音频同名 .lrc）",
                checked = prefs.lyricLocal,
                onCheckedChange = onLocalChange,
            )
        }

        SettingsGap(10)

        SettingsNote(
            "优先级：同时开启时先用本地歌词，本地没有再用在线歌词；" +
                "只开本地 = 纯离线歌词（没有本地文件就没有歌词）；两个都关 = 不显示歌词。"
        )
        SettingsNote("· 开启「下载与缓存 → 封面与歌词」后，边听边下会把歌词一并保存到本地，供这里离线使用。")
    }
}
