package com.aesthetic.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.rememberAppCardTheme
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

// ==================== 为你推荐 ====================

@Composable
fun HomeRecommendSection(
    playlists: List<PlaylistItem>,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
) {
    Column {
        Text(
            text = "为你推荐",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            ),
            color = TextPrimary
        )

        Spacer(Modifier.height(14.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = playlists.take(8).distinctBy { it.id },
                key = { it.id.ifBlank { it.name + it.coverUrl } }
            ) { playlist ->
                RecommendPlaylistCard(
                    playlist = playlist,
                    onClick = { onOpenPlaylistDetail(playlist) }
                )
            }
        }
    }
}

@Composable
private fun RecommendPlaylistCard(
    playlist: PlaylistItem,
    onClick: () -> Unit,
) {
    val theme = rememberAppCardTheme(playlist.coverUrl, defaultAccent = PrimaryBrown)

    // 外层 Box：渐变边框
    Box(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        theme.bgStart,
                        Color.White.copy(alpha = 0.9f)
                    )
                )
            )
            .padding(1.dp)  // 边框宽度
    ) {
        // 内层 Row：实际内容
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))  // 比外层小 1dp
                .background(theme.background)
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongCover(
                url = playlist.coverUrl,
                modifier = Modifier.size(64.dp),
                corner = 14.dp
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "${playlist.trackCount ?: 20} 首歌曲",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = TextSoft,
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(8.dp))

            // 播放按钮：同色系深色大圆角
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(theme.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
