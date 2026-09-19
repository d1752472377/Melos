package com.aesthetic.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextFaint

/**
 * 2x2 四宫格封面组件：
 * 从封面列表中提取最多前 4 张图片拼贴成一个方形圆角封面卡片；
 * 当封面只有 1 张时展示单张；为空时展示默认占位图。
 */
@Composable
fun QuadCover(
    covers: List<String?>,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    corner: Dp = 16.dp,
) {
    val validCovers = covers.filter { !it.isNullOrBlank() }
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(SurfaceSoft),
        contentAlignment = Alignment.Center
    ) {
        when {
            validCovers.isEmpty() -> {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = TextFaint,
                    modifier = Modifier.size(size * 0.45f)
                )
            }
            validCovers.size == 1 -> {
                AsyncCoverImage(
                    url = validCovers[0],
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // 2x2 四宫格拼贴
                val c0 = validCovers.getOrNull(0)
                val c1 = validCovers.getOrNull(1) ?: c0
                val c2 = validCovers.getOrNull(2) ?: c0
                val c3 = validCovers.getOrNull(3) ?: c1 ?: c0

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncCoverImage(url = c0, modifier = Modifier.weight(1f).fillMaxSize())
                        AsyncCoverImage(url = c1, modifier = Modifier.weight(1f).fillMaxSize())
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncCoverImage(url = c2, modifier = Modifier.weight(1f).fillMaxSize())
                        AsyncCoverImage(url = c3, modifier = Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun AsyncCoverImage(
    url: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(200)
                .build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(SurfaceSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
