package com.aesthetic.music.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import com.aesthetic.music.data.settings.DownloadFailurePolicy
import com.aesthetic.music.data.settings.DownloadNaming
import com.aesthetic.music.data.settings.UserPrefs

/** 下载与缓存设置页：边听边下 / 命名规则 / 封面与歌词 / 失败策略 / 仅 WiFi */
@Composable
fun DownloadSettingsScreen(
    prefs: UserPrefs,
    onBack: () -> Unit,
    onWhilePlayingChange: (Boolean) -> Unit,
    onNamingChange: (DownloadNaming) -> Unit,
    onCoverLyricsChange: (Boolean) -> Unit,
    onFailurePolicyChange: (DownloadFailurePolicy) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    SettingsPageScaffold(title = "下载设置", subtitle = "边听边下与文件保存规则", onBack = onBack) {
        SettingsSection(title = "下载行为") {
            SettingsSwitchRow(
                icon = Icons.Filled.CloudDownload,
                title = "边听边下",
                subtitle = "播放时后台把整首缓存到本地，下次听不再走网络",
                checked = prefs.downloadWhilePlaying,
                onCheckedChange = onWhilePlayingChange,
            )
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.Filled.Image,
                title = "封面与歌词",
                subtitle = "缓存时一并保存专辑封面与歌词文件，离线也有封面和歌词",
                checked = prefs.downloadCoverLyrics,
                onCheckedChange = onCoverLyricsChange,
            )
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.Filled.Wifi,
                title = "仅 WiFi 下载",
                subtitle = "移动网络下不缓存、不预加载，避免消耗流量",
                checked = prefs.downloadWifiOnly,
                onCheckedChange = onWifiOnlyChange,
            )
        }

        SettingsGap()

        SettingsSection(title = "文件命名") {
            SettingsOptionList(
                title = "缓存文件命名",
                options = DownloadNaming.entries.map { SettingOption(it.label, it.example) },
                selectedIndex = DownloadNaming.entries.indexOf(prefs.downloadNaming).coerceAtLeast(0),
                onSelect = { idx -> onNamingChange(DownloadNaming.entries[idx]) },
            )
        }

        SettingsGap()

        SettingsSection(title = "失败策略") {
            SettingsOptionList(
                title = "缓存失败时",
                options = DownloadFailurePolicy.entries.map { SettingOption(it.label, it.desc) },
                selectedIndex = DownloadFailurePolicy.entries.indexOf(prefs.downloadFailurePolicy)
                    .coerceAtLeast(0),
                onSelect = { idx -> onFailurePolicyChange(DownloadFailurePolicy.entries[idx]) },
            )
        }

        SettingsGap(10)

        SettingsNote("· 缓存落在应用私有目录，卸载即清空；可在「设置 → 缓存管理」查看占用并清理。")
        SettingsNote("· 命名规则只影响新缓存的文件名，已缓存的文件不会被重命名。")
        SettingsNote("· 「提示我」策略下失败会弹一次提示；「自动重试」最多重试 2 次后停止。")
    }
}
