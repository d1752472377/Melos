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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

data class ExtractedThemeColors(
    val bgStart: Color,
    val bgEnd: Color,
    val buttonColor: Color,
    val accentColor: Color,
)

private val colorExtractionCache = androidx.collection.LruCache<String, Int>(60)

/**
 * 根据图片 URL 提取主色调，并生成浅色卡片背景渐变与深色按钮强调色。
 * 具备 LRU 内存高速缓存，滑动时直接命中缓存零开销。
 */
@Composable
fun rememberDominantColors(
    url: String?,
    defaultAccent: Color = com.aesthetic.music.ui.theme.PrimaryBrown,
): ExtractedThemeColors {
    val context = LocalContext.current
    val isDark = Palette.isDark

    var extractedColorInt by androidx.compose.runtime.remember(url) {
        androidx.compose.runtime.mutableStateOf(if (url != null) colorExtractionCache.get(url) else null)
    }

    androidx.compose.runtime.LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        val cached = colorExtractionCache.get(url)
        if (cached != null) {
            extractedColorInt = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val loader = coil.ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(40, 40)
                    .build()
                val result = loader.execute(request)
                val drawable = result.drawable
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    val bitmap = drawable.bitmap
                    if (bitmap != null && !bitmap.isRecycled) {
                        val sampleBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 16, 16, false)
                        var totalR = 0L
                        var totalG = 0L
                        var totalB = 0L
                        var count = 0
                        val width = sampleBitmap.width
                        val height = sampleBitmap.height
                        val pixels = IntArray(width * height)
                        sampleBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                        for (pixel in pixels) {
                            val alpha = android.graphics.Color.alpha(pixel)
                            if (alpha > 128) {
                                val r = android.graphics.Color.red(pixel)
                                val g = android.graphics.Color.green(pixel)
                                val b = android.graphics.Color.blue(pixel)
                                val max = maxOf(r, maxOf(g, b))
                                val min = minOf(r, minOf(g, b))
                                if (max - min > 15 || (max < 240 && min > 20)) {
                                    totalR += r
                                    totalG += g
                                    totalB += b
                                    count++
                                }
                            }
                        }
                        if (count > 0) {
                            val avgR = (totalR / count).toInt()
                            val avgG = (totalG / count).toInt()
                            val avgB = (totalB / count).toInt()
                            val color = android.graphics.Color.rgb(avgR, avgG, avgB)
                            colorExtractionCache.put(url, color)
                            extractedColorInt = color
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    return androidx.compose.runtime.remember(extractedColorInt, isDark, defaultAccent) {
        val baseColor = extractedColorInt?.let { Color(it) } ?: defaultAccent
        val hsl = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.rgb(
                (baseColor.red * 255).toInt(),
                (baseColor.green * 255).toInt(),
                (baseColor.blue * 255).toInt()
            ),
            hsl
        )
        val hue = hsl[0]
        val sat = hsl[1].coerceIn(0.2f, 0.85f)

        if (isDark) {
            val bgStart = Color.hsv(hue, (sat * 0.4f).coerceAtMost(0.45f), 0.20f)
            val bgEnd = Color.hsv(hue, (sat * 0.25f).coerceAtMost(0.35f), 0.13f)
            val buttonColor = Color.hsv(hue, (sat * 0.85f).coerceAtLeast(0.6f), 0.75f)
            val accentColor = Color.hsv(hue, sat, 0.85f)
            ExtractedThemeColors(bgStart, bgEnd, buttonColor, accentColor)
        } else {
            // 浅色系：卡片为浅色清新左右渐变
            val bgStart = Color.hsv(hue, (sat * 0.22f).coerceIn(0.06f, 0.18f), 0.98f)
            val bgEnd = Color.hsv((hue + 12f) % 360f, (sat * 0.10f).coerceIn(0.02f, 0.10f), 0.99f)
            // 按钮同色系深色
            val buttonColor = Color.hsv(hue, (sat * 1.1f).coerceIn(0.65f, 0.95f), 0.52f)
            val accentColor = Color.hsv(hue, sat, 0.60f)
            ExtractedThemeColors(bgStart, bgEnd, buttonColor, accentColor)
        }
    }
}
