package com.aesthetic.music.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.AppColors

private val CoverPlaceholderBrushLight =
    Brush.linearGradient(listOf(AppColors.coverPlaceholderLight1, AppColors.coverPlaceholderLight2))
private val CoverPlaceholderBrushDark =
    Brush.linearGradient(listOf(AppColors.coverPlaceholderDark1, AppColors.coverPlaceholderDark2))
private val CoverIconTintLight = AppColors.indigoLight.copy(alpha = 0.65f)
private val CoverIconTintDark = AppColors.slate500

@Composable
fun SongCover(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
) {
    val context = LocalContext.current
    val isDark = Palette.isDark
    val placeholderBrush = if (isDark) CoverPlaceholderBrushDark else CoverPlaceholderBrushLight
    val iconTint = if (isDark) CoverIconTintDark else CoverIconTintLight

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
                    .height(14.dp)
                    .graphicsLayer {
                        scaleY = h / 14f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color)
            )
        }
    }
}

@Composable
fun RotatingDisc(
    url: String?,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    durationMs: Int = 20_000,
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var last = rotation.value
            while (true) {
                val target = ((last / 360f).toInt() + 1) * 360f
                rotation.animateTo(target, tween(durationMs, easing = LinearEasing))
                last = target
            }
        }
    }
    Box(modifier.clip(CircleShape).background(Stone200)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rotation.value },
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rotation.value }
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
        // 在 IO 线程只做计算，返回结果；写 State 回到主线程
        val color: Int? = withContext(Dispatchers.IO) {
            try {
                val loader = coil.Coil.imageLoader(context)
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
                        try {
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
                                val colorInt = android.graphics.Color.rgb(avgR, avgG, avgB)
                                colorExtractionCache.put(url, colorInt)
                                colorInt
                            } else null
                        } finally {
                            sampleBitmap.recycle()
                        }
                    } else null
                } else null
            } catch (_: Exception) {
                null
            }
        }
        if (color != null) {
            extractedColorInt = color
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
            val bgStart = Color.hsv(hue, (sat * 0.22f).coerceIn(0.06f, 0.18f), 0.98f)
            val bgEnd = Color.hsv((hue + 12f) % 360f, (sat * 0.10f).coerceIn(0.02f, 0.10f), 0.99f)
            val buttonColor = Color.hsv(hue, (sat * 1.1f).coerceIn(0.65f, 0.95f), 0.52f)
            val accentColor = Color.hsv(hue, sat, 0.60f)
            ExtractedThemeColors(bgStart, bgEnd, buttonColor, accentColor)
        }
    }
}

fun formatPlayCountText(count: Long?): String {
    if (count == null || count < 0) return ""
    return when {
        count >= 100_000_000 -> String.format(java.util.Locale.US, "%.1f亿", count / 100_000_000.0)
        count >= 10_000 -> String.format(java.util.Locale.US, "%.1f万", count / 10_000.0)
        else -> count.toString()
    }
}

fun platformLabel(p: String?): String = when (p) {
    "netease", "chksz" -> "网易"
    "kugou" -> "酷狗"
    "qq" -> "QQ"
    "kuwo" -> "酷我"
    null -> ""
    else -> p.take(3)
}

fun platformColor(p: String?): Color = when (p) {
    "netease", "chksz" -> AppColors.brandNeteaseDeep
    "kugou" -> AppColors.brandKugouAlt
    "qq" -> AppColors.brandQQ
    "kuwo" -> AppColors.brandKuwoAlt
    else -> AppColors.brandUnknownAlt
}

@Composable
fun PlatformTag(platform: String?, modifier: Modifier = Modifier) {
    if (platform.isNullOrBlank()) return
    val color = platformColor(platform)
    Text(
        text = platformLabel(platform),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
