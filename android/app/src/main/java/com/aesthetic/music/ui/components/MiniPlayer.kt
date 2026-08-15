package com.aesthetic.music.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 迷你播放器（移植自参考 App 的 MiniPlayer）：
 * 底部悬浮迷你条，显示当前歌曲 + 播放/暂停 + 下一首，点击打开全屏播放器。
 */
@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onOpenPlayer: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (song == null) return
    Box(modifier = modifier.fillMaxWidth().background(Surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenPlayer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongCover(song.artwork, Modifier.size(40.dp), corner = 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = TextSoft, maxLines = 1)
            }
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = PrimaryBrown,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onToggle),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "下一首",
                tint = TextSoft,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onNext),
            )
        }
        // 底部进度细条
        if (durationMs > 0) {
            LinearProgressIndicator(
                progress = { (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter),
                color = PrimaryBrown,
                trackColor = Color.Transparent,
            )
        }
    }
}
