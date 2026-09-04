package com.aesthetic.music.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.player.PlaybackProgress
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import kotlinx.coroutines.flow.StateFlow

/**
 * 迷你播放器：精致微圆角封面 + Level 1 核心播放圆键 + 顶部进度条。
 * 进度条数据在组件内部订阅 [progressFlow]：进度每 500ms 跳动时只重组本组件，
 * 不会向上扩散到 AppShell / 各内容页。
 */
@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    progressFlow: StateFlow<PlaybackProgress>,
    onOpenPlayer: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // collectAsState 必须无条件先调用（Composable 钩子顺序稳定），再判断 song 是否为空
    val progress by progressFlow.collectAsState()
    if (song == null) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .shadow(5.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.06f))
            .border(1.dp, Stone200.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
    ) {
        // 顶部进度细条 (2.dp)
        if (progress.durationMs > 0) {
            LinearProgressIndicator(
                progress = {
                    val duration = progress.durationMs.toFloat()
                    if (duration > 0f) {
                        val frac = progress.positionMs.toFloat() / duration
                        if (frac.isNaN() || frac.isInfinite()) 0f else frac.coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .align(Alignment.TopCenter),
                color = PrimaryBrown,
                trackColor = Stone200.copy(alpha = 0.4f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenPlayer)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 8.dp 唱片封面
            SongCover(song.artwork, Modifier.size(42.dp), corner = 8.dp)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                AnimatedContent(targetState = song.title, label = "song_title") { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                AnimatedContent(targetState = song.artist.ifBlank { "未知歌手" }, label = "song_artist") { artist ->
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp
                        ),
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Level 1 实色圆形播放按钮（外层 44dp 触控热区，内层保持 34dp 视觉不变）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(34.dp).background(PrimaryBrown, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        // 加载中：圆形微调器替代播放/暂停图标（白描边在 PrimaryBrown 实色圆底上）
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Level 4 Ghost 切歌键（触控热区扩到 44dp）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "下一首",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

