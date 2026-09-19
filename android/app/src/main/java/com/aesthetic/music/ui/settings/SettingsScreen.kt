package com.aesthetic.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import com.aesthetic.music.ui.home.iosGlassCard
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.data.settings.UserPrefs
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "自动（跟随系统）"
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
    }

/** 设置主页可跳转的子页面 */
enum class SettingsPage {
    APPEARANCE, QUALITY, PLAYBACK, LYRICS, DOWNLOAD, CACHE
}

/**
 * 设置主页。
 *
 * 结构：常用设置（各项独立成页）→ 关于。
 * 「插件管理」「账号与同步」不再放这里：二者在个人页都有入口，属于同一功能开两个入口。
 * 「桌面悬浮胶囊」已整体移除。本页只显示每项的当前状态摘要，设置项本身各自独立成页。
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    quality: QualityLevel,
    userPrefs: UserPrefs = UserPrefs(),
    onBack: () -> Unit,
    onOpenPage: (SettingsPage) -> Unit = {},
    onCheckUpdate: (onResult: (String) -> Unit) -> Unit,
) {
    var updateStatus by remember { mutableStateOf("点击检查是否有新版本") }
    var checking by remember { mutableStateOf(false) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = BottomContentSpacer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(statusBarTop + 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onBack)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
            )
        }

        Spacer(Modifier.height(18.dp))

        // ===== 常用设置：各项已独立成子页面，这里只放入口 =====
        SectionCard(title = "常用设置") {
            SettingRow(
                icon = Icons.Filled.Palette,
                iconTint = PrimaryBrown,
                label = "主题与外观",
                subtitle = "${themeMode.label} · 状态栏${userPrefs.statusBarMode.label}",
                onClick = { onOpenPage(SettingsPage.APPEARANCE) }
            )
            SettingRow(
                icon = Icons.Filled.MusicNote,
                iconTint = PrimaryBrown,
                label = "音质",
                subtitle = quality.label,
                onClick = { onOpenPage(SettingsPage.QUALITY) }
            )
            SettingRow(
                icon = Icons.Filled.PlayCircleOutline,
                iconTint = PrimaryBrown,
                label = "播放设置",
                subtitle = buildString {
                    append(if (userPrefs.preloadNext) "预加载下一首" else "不预加载")
                    if (userPrefs.crossFade) append(" · 淡入淡出")
                    if (userPrefs.mixWithOthers) append(" · 混合播放")
                },
                onClick = { onOpenPage(SettingsPage.PLAYBACK) }
            )
            SettingRow(
                icon = Icons.Filled.Subtitles,
                iconTint = PrimaryBrown,
                label = "歌词显示",
                subtitle = buildString {
                    append(if (userPrefs.lyricTranslation) "翻译歌词" else "不显示翻译")
                    append(if (userPrefs.lyricOnline) " · 在线" else " · 离线")
                    if (userPrefs.lyricLocal) append(" · 本地优先")
                },
                onClick = { onOpenPage(SettingsPage.LYRICS) }
            )
            SettingRow(
                icon = Icons.Filled.CloudDownload,
                iconTint = PrimaryBrown,
                label = "下载设置",
                subtitle = buildString {
                    append(if (userPrefs.downloadWhilePlaying) "边听边下" else "不自动缓存")
                    if (userPrefs.downloadWifiOnly) append(" · 仅 WiFi")
                },
                onClick = { onOpenPage(SettingsPage.DOWNLOAD) }
            )
            SettingRow(
                icon = Icons.Filled.Storage,
                iconTint = PrimaryBrown,
                label = "缓存管理",
                subtitle = "查看缓存占用、按类型清理",
                onClick = { onOpenPage(SettingsPage.CACHE) }
            )
        }

        Spacer(Modifier.height(14.dp))

        // ===== 关于 =====
        SectionCard(title = "关于软件") {
            SettingRow(
                icon = Icons.Filled.SystemUpdate,
                iconTint = PrimaryBrown,
                label = "检查更新",
                subtitle = updateStatus,
                onClick = {
                    if (checking) return@SettingRow
                    checking = true
                    updateStatus = "检查中…"
                    onCheckUpdate { result ->
                        updateStatus = result
                        checking = false
                    }
                }
            )
            SettingRow(
                icon = Icons.Filled.Info,
                iconTint = PrimaryBrown,
                label = "当前版本",
                subtitle = com.aesthetic.music.BuildConfig.VERSION_NAME,
                onClick = {}
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 4.dp)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
                maxLines = 1
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}
