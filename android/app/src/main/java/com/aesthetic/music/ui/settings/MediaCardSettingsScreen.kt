package com.aesthetic.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.settings.MediaCardButtonPosition
import com.aesthetic.music.data.settings.MediaCardLyricPosition
import com.aesthetic.music.data.settings.UserPrefs
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/** 媒体卡片设置页：通知栏卡片按钮与歌词显示 */
@Composable
fun MediaCardSettingsScreen(
    prefs: UserPrefs,
    onBack: () -> Unit,
    onFavoriteChange: (Boolean) -> Unit,
    onButtonPositionChange: (MediaCardButtonPosition) -> Unit,
    onShowLyricChange: (Boolean) -> Unit,
    onLyricPositionChange: (MediaCardLyricPosition) -> Unit,
) {
    SettingsPageScaffold(title = "媒体卡片", subtitle = "通知栏播放卡片的按钮与歌词", onBack = onBack) {
        // 预览：改完立刻能看到卡片长什么样
        SettingsSection(title = "预览") {
            MediaCardPreview(prefs)
        }

        SettingsGap()

        SettingsSection(title = "按钮") {
            SettingsSwitchRow(
                icon = Icons.Filled.Favorite,
                title = "收藏按钮",
                subtitle = "在卡片上直接收藏 / 取消收藏当前歌曲",
                checked = prefs.mediaCardFavorite,
                onCheckedChange = onFavoriteChange,
            )
            if (prefs.mediaCardFavorite) {
                SettingsDivider()
                SettingsSegmentRow(
                    title = "收藏按钮位置",
                    subtitle = "决定收藏按钮排在播放控制的哪一侧",
                    options = listOf(
                        SettingOption(MediaCardButtonPosition.LEFT.label),
                        SettingOption(MediaCardButtonPosition.RIGHT.label),
                    ),
                    selectedIndex = MediaCardButtonPosition.entries
                        .indexOf(prefs.mediaCardButtonPosition).coerceAtLeast(0),
                    onSelect = { idx -> onButtonPositionChange(MediaCardButtonPosition.entries[idx]) },
                )
            }
        }

        SettingsGap()

        SettingsSection(title = "歌词") {
            SettingsSwitchRow(
                icon = Icons.Filled.Subtitles,
                title = "显示歌词",
                subtitle = "在卡片上显示当前正在唱的这一句，随播放滚动",
                checked = prefs.mediaCardShowLyric,
                onCheckedChange = onShowLyricChange,
            )
            if (prefs.mediaCardShowLyric) {
                SettingsDivider()
                SettingsSegmentRow(
                    title = "歌词位置",
                    subtitle = "歌词显示在歌名的上方还是下方",
                    options = listOf(
                        SettingOption(MediaCardLyricPosition.ABOVE.label),
                        SettingOption(MediaCardLyricPosition.BELOW.label),
                    ),
                    selectedIndex = MediaCardLyricPosition.entries
                        .indexOf(prefs.mediaCardLyricPosition).coerceAtLeast(0),
                    onSelect = { idx -> onLyricPositionChange(MediaCardLyricPosition.entries[idx]) },
                )
            }
        }

        SettingsGap(10)

        SettingsNote("· 卡片由系统媒体通知渲染：Android 13+ 的折叠态最多显示 3 个按钮，展开后显示全部。")
        SettingsNote("· 「歌词在上方」用系统副标题行实现，部分系统（如 MIUI）折叠态可能不显示副标题。")
        SettingsNote("· 若看不到卡片，请在系统设置里允许本应用发送通知（Android 13+ 需通知权限）。")
    }
}

/** 通知栏卡片外观预览（纯本地绘制，不依赖系统通知） */
@Composable
private fun MediaCardPreview(prefs: UserPrefs) {
    val lyric = "穿过云层 我试着努力向你奔跑"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Stone100)
            .border(1.dp, Stone200, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimaryBrown.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = PrimaryBrown,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (prefs.mediaCardShowLyric && prefs.mediaCardLyricPosition == MediaCardLyricPosition.ABOVE) {
                Text(
                    text = lyric,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = PrimaryBrown,
                    maxLines = 1,
                )
            }
            Text(
                text = "夜曲",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
            )
            val secondLine = when {
                prefs.mediaCardShowLyric && prefs.mediaCardLyricPosition == MediaCardLyricPosition.BELOW -> lyric
                else -> "周杰伦"
            }
            Text(
                text = secondLine,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = if (secondLine == lyric) PrimaryBrown else TextSoft,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val controls: @Composable () -> Unit = {
                Icon(Icons.Filled.SkipPrevious, null, tint = TextSoft, modifier = Modifier.size(18.dp))
                Icon(Icons.Filled.Pause, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                Icon(Icons.Filled.SkipNext, null, tint = TextSoft, modifier = Modifier.size(18.dp))
            }
            val favorite = @Composable {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = if (prefs.mediaCardFavorite) PrimaryBrown else TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (prefs.mediaCardFavorite && prefs.mediaCardButtonPosition == MediaCardButtonPosition.LEFT) {
                favorite()
            }
            controls()
            if (prefs.mediaCardFavorite && prefs.mediaCardButtonPosition == MediaCardButtonPosition.RIGHT) {
                favorite()
            }
        }
    }
    Row(
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Notifications, null, tint = TextMuted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "预览效果，实际以系统媒体控件样式为准",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = TextMuted,
        )
    }
    Spacer(Modifier.height(2.dp))
}
