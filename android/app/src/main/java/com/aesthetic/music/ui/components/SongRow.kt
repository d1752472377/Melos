package com.aesthetic.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

/**
 * 通用单曲行：搜索页 / 歌手页 / 全部演唱页共用。
 *
 * - [showPlatformTag] 控制标题下方是否显示平台彩色小标签（搜索页 true，歌手页 false）；
 * - [leading] 左侧插槽（如序号），默认不显示；
 * - [actions] 右侧按钮组插槽，默认一个「...」；传入后完全替换默认内容；
 * - 播放中整行浅色高光 + 标题主题色（[isPlaying]）。
 *
 * 注意：行本身使用半透融合底色，与毛玻璃卡片容器完美融合。
 */
@Composable
fun SongRow(
    song: Song,
    isPlaying: Boolean,
    showPlatformTag: Boolean = true,
    onPlay: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val isDark = Palette.isDark
    val subtitle = listOfNotNull(
        song.artist.ifBlank { null },
        song.album.ifBlank { null },
    ).joinToString(" · ")

    val rowBg = when {
        isPlaying -> if (isDark) AppColors.activeRowBgDark else PrimaryBrown.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val activeTitleColor = if (isDark) AppColors.sky else PrimaryBrown
    val titleColor = if (isPlaying) activeTitleColor else if (isDark) AppColors.slate100 else TextPrimary
    val subtitleColor = if (isDark) AppColors.slate400 else TextSoft

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onPlay)
            .padding(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        SongCover(song.artwork, Modifier.size(48.dp), corner = 10.dp)
        Spacer(Modifier.width(AppSpacing.lg))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showPlatformTag && !song.platform.isNullOrBlank()) {
                    PlatformTag(song.platform)
                    if (subtitle.isNotBlank()) Spacer(Modifier.width(AppSpacing.xs))
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        if (isPlaying) {
            // [GZ 真值] PlayingBarsKt.java:122 固定条高 16.dp，GZ 调用点自行用 modifier 限制高度
            PlayingBars(
                isPlaying = true,
                color = activeTitleColor,
                modifier = Modifier.height(13.dp).padding(horizontal = AppSpacing.xs),
            )
        }
        Spacer(Modifier.width(AppSpacing.xxs))
        if (actions != null) {
            actions()
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = "更多操作",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}