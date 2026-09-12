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
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 通用单曲行：搜索页 / 歌手页 / 全部演唱页共用。
 *
 * - [showPlatformTag] 控制标题下方是否显示平台彩色小标签（搜索页 true，歌手页 false）；
 * - [leading] 左侧插槽（如序号），默认不显示；
 * - [actions] 右侧按钮组插槽，默认一个「...」；传入后完全替换默认内容；
 * - 播放中整行浅棕底 + 标题棕色（[isPlaying]）。
 *
 * 注意：行本身不带边框/背景圆角，白底与分隔线由外层列表容器提供。
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
    val subtitle = listOfNotNull(
        song.artist.ifBlank { null },
        song.album.ifBlank { null },
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isPlaying) PrimaryBrown.copy(alpha = 0.06f) else Surface)
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        SongCover(song.artwork, Modifier.size(48.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isPlaying) PrimaryBrown else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showPlatformTag && !song.platform.isNullOrBlank()) {
                    PlatformTag(song.platform)
                    if (subtitle.isNotBlank()) Spacer(Modifier.width(6.dp))
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
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