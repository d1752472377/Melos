package com.aesthetic.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.Stone200

/** 歌曲封面：带精致渐变占位 + 圆角 + 平滑渐入 */
@Composable
fun SongCover(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
) {
    val context = LocalContext.current
    val placeholderBrush = if (Palette.isDark) {
        Brush.linearGradient(listOf(Color(0xFF26262E), Color(0xFF1B1B22)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEDE9FE), Color(0xFFE0E7FF)))
    }
    val iconTint = if (Palette.isDark) Color(0xFF64748B) else Color(0xFF818CF8).copy(alpha = 0.65f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(placeholderBrush),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .crossfade(200)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** 音波跳动条（当前播放指示） */
@Composable
fun WaveBars(
    color: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
) {
    val transition = rememberInfiniteTransition()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        repeat(3) { i ->
            val h by transition.animateFloat(
                initialValue = 4f,
                targetValue = 14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 180, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "wave$i",
            )
            Box(
                Modifier
                    .width(barWidth)
                    .height(h.dp)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color)
            )
        }
    }
}

/** 旋转唱片（底部 Dock 中央的小 CD） */
@Composable
fun RotatingDisc(
    url: String?,
    modifier: Modifier = Modifier,
    durationMs: Int = 20_000,
) {
    val transition = rememberInfiniteTransition()
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "discSpin",
    )
    Box(modifier.clip(CircleShape).background(Stone200)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = angle },
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = angle }
                    .background(Stone200)
            )
        }
    }
}
