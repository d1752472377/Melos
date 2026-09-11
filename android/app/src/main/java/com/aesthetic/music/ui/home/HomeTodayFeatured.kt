package com.aesthetic.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.appCard
import com.aesthetic.music.ui.components.rememberAppCardTheme
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

// ==================== 今日推荐 Banner 卡片 ====================

@Composable
fun TodayFeaturedCard(
    playlist: PlaylistItem?,
    fallbackSong: Song?,
    onClick: () -> Unit,
) {
    val title = playlist?.name ?: fallbackSong?.title ?: "粤难粤爱 | 靠拥抱取暖的季节"
    val coverUrl = playlist?.coverUrl ?: fallbackSong?.artwork

    val theme = rememberAppCardTheme(coverUrl, defaultAccent = PrimaryBrown)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.White,
                spotColor = Color.White
            )
            .appCard(background = theme.background, borderWidth = 0.5.dp)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 今日推荐小标签 (6dp radius)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (Palette.isDark) theme.accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.85f))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "今日推荐",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.accent,
                        letterSpacing = 0.2.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 歌单标题：大字重黑体
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.3).sp,
                        lineHeight = 24.sp
                    ),
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // 副标题：细一号灰色说明
                Text(
                    text = "为你精挑细选的音乐灵感",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextSoft,
                    maxLines = 1
                )

                Spacer(Modifier.height(18.dp))

                // Level 1 主操作 CTA 胶囊按钮（大圆角、充足呼吸感）
                Row(
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(22.dp), ambientColor = Color.White)
                        .clip(RoundedCornerShape(22.dp))
                        .background(theme.accent)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "立即收听",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(18.dp))

            // 右侧大圆角封面
            SongCover(
                url = coverUrl,
                modifier = Modifier
                    .size(104.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                corner = 16.dp
            )
        }
    }
}
