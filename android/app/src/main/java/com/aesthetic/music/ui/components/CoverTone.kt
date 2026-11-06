package com.aesthetic.music.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil.Coil
import coil.request.ImageRequest
import com.aesthetic.music.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.PriorityQueue

val CoverToneFallback: Color = AppColors.coverToneFallback

private const val QUANTIZE_MAX_COLORS = 24

private const val QUANTIZE_MAX_PIXEL_AREA = 12544

private const val HISTOGRAM_SIZE = 32768

fun extractCoverToneOrNull(bitmap: Bitmap): Color? {
    val swatch = dominantCoverSwatch(bitmap) ?: return null
    return adjustCoverTone(Color(swatch.rgb))
}

fun adjustCoverTone(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    val saturation = hsv[1]
    hsv[1] = if (saturation < 0.06f) {
        0.0f
    } else {
        coerceIn(saturation * 1.05f + 0.02f, 0.16f, 0.8f)
    }
    hsv[2] = coerceIn(hsv[2], 0.38f, 0.72f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun coerceIn(value: Float, min: Float, max: Float): Float =
    if (value < min) min else if (value > max) max else value

private class Swatch(val rgb: Int, val population: Int) {

    val hsl: FloatArray by lazy(LazyThreadSafetyMode.NONE) {
        val out = FloatArray(3)
        rgbToHsl(out, android.graphics.Color.red(rgb), android.graphics.Color.green(rgb), android.graphics.Color.blue(rgb))
        out
    }
}

private fun rgbToHsl(out: FloatArray, r: Int, g: Int, b: Int) {
    val rf = r / 255.0f
    val gf = g / 255.0f
    val bf = b / 255.0f
    val max = maxOf(rf, maxOf(gf, bf))
    val min = minOf(rf, minOf(gf, bf))
    val delta = max - min
    val lightness = (max + min) / 2.0f
    var hue: Float
    var saturation: Float
    if (max == min) {
        hue = 0.0f
        saturation = 0.0f
    } else {
        hue = if (max == rf) {
            ((gf - bf) / delta) % 6.0f
        } else if (max == gf) {
            ((bf - rf) / delta) + 2.0f
        } else {
            ((rf - gf) / delta) + 4.0f
        }
        saturation = delta / (1.0f - kotlin.math.abs(2.0f * lightness - 1.0f))
    }
    var h = (hue * 60.0f) % 360.0f
    if (h < 0.0f) h += 360.0f
    out[0] = if (h < 0.0f) 0.0f else minOf(h, 360.0f)
    out[1] = if (saturation < 0.0f) 0.0f else minOf(saturation, 1.0f)
    out[2] = if (lightness >= 0.0f) minOf(lightness, 1.0f) else 0.0f
}

private fun modifyWordWidth(value: Int, fromBits: Int, toBits: Int): Int {
    val shifted = if (toBits > fromBits) value shl (toBits - fromBits) else value shr (fromBits - toBits)
    return shifted and ((1 shl toBits) - 1)
}

private fun approximateToRgb888(quantized: Int): Int = android.graphics.Color.rgb(
    modifyWordWidth((quantized shr 10) and 31, 5, 8),
    modifyWordWidth((quantized shr 5) and 31, 5, 8),
    modifyWordWidth(quantized and 31, 5, 8),
)

private class Box(
    private val colors: IntArray,
    private val histogram: IntArray,
    var minIndex: Int,
    var maxIndex: Int,
) {
    var volume = 0
    private var minRed = 0; private var maxRed = 0
    private var minGreen = 0; private var maxGreen = 0
    private var minBlue = 0; private var maxBlue = 0

    init {
        recompute()
    }

    fun recompute() {
        var minR = Integer.MAX_VALUE
        var maxG = Integer.MIN_VALUE
        var maxB = Integer.MIN_VALUE
        var total = 0
        var minG = Integer.MAX_VALUE
        var minB = Integer.MAX_VALUE
        var maxR = Integer.MIN_VALUE
        for (i in minIndex..maxIndex) {
            val q = colors[i]
            total += histogram[q]
            val r = (q shr 10) and 31
            val g = (q shr 5) and 31
            val b = q and 31
            if (r > maxR) maxR = r
            if (r < minR) minR = r
            if (g > maxG) maxG = g
            if (g < minG) minG = g
            if (b > maxB) maxB = b
            if (b < minB) minB = b
        }
        minRed = minR; this.maxRed = maxR
        minGreen = minG; this.maxGreen = maxG
        minBlue = minB; this.maxBlue = maxB
        volume = total
    }

    fun boxVolume(): Int =
        ((maxBlue - minBlue) + 1) * ((maxGreen - minGreen) + 1) * ((maxRed - minRed) + 1)

    fun redLength(): Int = maxRed - minRed

    fun greenLength(): Int = maxGreen - minGreen

    fun blueLength(): Int = maxBlue - minBlue
}

private fun modifyWordsForSort(colors: IntArray, axis: Int, from: Int, to: Int) {
    if (axis == -2) {
        for (i in from..to) {
            val c = colors[i]
            colors[i] = (c and 31) or (((c shr 5) and 31) shl 10) or (((c shr 10) and 31) shl 5)
        }
        return
    }
    if (axis != -1) return
    for (i in from..to) {
        val c = colors[i]
        colors[i] = ((c shr 10) and 31) or ((c and 31) shl 10) or (((c shr 5) and 31) shl 5)
    }
}

private fun quantizePalette(bitmap: Bitmap, maxColors: Int): List<Swatch> {
    val area = bitmap.width * bitmap.height
    val scale = if (area > QUANTIZE_MAX_PIXEL_AREA) {
        Math.sqrt(QUANTIZE_MAX_PIXEL_AREA.toDouble() / area)
    } else {
        -1.0
    }
    val scaled = if (scale <= 0.0) {
        bitmap
    } else {
        Bitmap.createScaledBitmap(
            bitmap,
            Math.ceil(bitmap.width * scale).toInt(),
            Math.ceil(bitmap.height * scale).toInt(),
            false,
        )
    }
    val width = scaled.width
    val height = scaled.height
    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)

    val histogram = IntArray(HISTOGRAM_SIZE)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val quantized = (modifyWordWidth(android.graphics.Color.green(pixel), 8, 5) shl 5) or
            (modifyWordWidth(android.graphics.Color.red(pixel), 8, 5) shl 10) or
            modifyWordWidth(android.graphics.Color.blue(pixel), 8, 5)
        pixels[i] = quantized
        histogram[quantized] = histogram[quantized] + 1
    }

    var uniqueCount = 0
    for (q in 0 until HISTOGRAM_SIZE) {
        if (histogram[q] > 0) uniqueCount++
    }
    val colors = IntArray(uniqueCount)
    var write = 0
    for (q in 0 until HISTOGRAM_SIZE) {
        if (histogram[q] > 0) colors[write++] = q
    }

    if (uniqueCount <= maxColors) {
        val swatches = ArrayList<Swatch>(uniqueCount)
        for (i in 0 until uniqueCount) {
            val q = colors[i]
            swatches.add(Swatch(approximateToRgb888(q), histogram[q]))
        }
        if (scaled !== bitmap) scaled.recycle()
        return swatches
    }

    val queue = PriorityQueue<Box>(
        maxColors,
        Comparator { a: Box, b: Box -> b.boxVolume() - a.boxVolume() },
    )
    queue.offer(Box(colors, histogram, 0, colors.size - 1))
    while (queue.size < maxColors) {
        val box = queue.poll() ?: break
        if ((box.maxIndex + 1) - box.minIndex <= 1) break
        val redLength = box.redLength()
        val greenLength = box.greenLength()
        val blueLength = box.blueLength()
        val axis = if (redLength < greenLength || redLength < blueLength) {
            if (greenLength < redLength || greenLength < blueLength) -1 else -2
        } else {
            -3
        }
        modifyWordsForSort(colors, axis, box.minIndex, box.maxIndex)
        java.util.Arrays.sort(colors, box.minIndex, box.maxIndex + 1)
        modifyWordsForSort(colors, axis, box.minIndex, box.maxIndex)
        val half = box.volume / 2
        var splitIndex = box.minIndex
        var accumulated = 0
        var i = box.minIndex
        while (i <= box.maxIndex) {
            accumulated += histogram[colors[i]]
            if (accumulated >= half) {
                splitIndex = minOf(box.maxIndex - 1, i)
                break
            }
            i++
        }
        val upper = Box(colors, histogram, splitIndex + 1, box.maxIndex)
        box.maxIndex = splitIndex
        box.recompute()
        queue.offer(upper)
        queue.offer(box)
    }

    val swatches = ArrayList<Swatch>(queue.size)
    for (box in queue) {
        var total = 0
        var sumRed = 0
        var sumGreen = 0
        var sumBlue = 0
        for (i in box.minIndex..box.maxIndex) {
            val q = colors[i]
            val count = histogram[q]
            total += count
            sumRed += ((q shr 10) and 31) * count
            sumGreen += ((q shr 5) and 31) * count
            sumBlue += (q and 31) * count
        }
        val totalFloat = total.toFloat()
        val rgb = android.graphics.Color.rgb(
            modifyWordWidth(Math.round(sumRed / totalFloat), 5, 8),
            modifyWordWidth(Math.round(sumGreen / totalFloat), 5, 8),
            modifyWordWidth(Math.round(sumBlue / totalFloat), 5, 8),
        )
        swatches.add(Swatch(rgb, total))
    }
    if (scaled !== bitmap) scaled.recycle()
    return swatches
}

private fun dominantCoverSwatch(bitmap: Bitmap): Swatch? {
    val swatches = quantizePalette(bitmap, QUANTIZE_MAX_COLORS)
    val dominant = maxByPopulation(swatches)
    if (swatches.isEmpty()) return null

    val kept = swatches.filter { swatch ->
        val lightness = swatch.hsl[2]
        lightness >= 0.05f && lightness <= 0.95f
    }
    if (kept.isEmpty()) return dominant

    var keptPopulation = 0
    for (swatch in kept) keptPopulation += swatch.population
    val minPopulation = if (keptPopulation < 1) 1 else keptPopulation

    val saturated = kept.filter { it.hsl[1] >= 0.06f }
    var saturatedPopulation = 0
    for (swatch in saturated) saturatedPopulation += swatch.population
    if (saturated.isEmpty() || saturatedPopulation < minPopulation * 0.12f) {
        return maxByPopulation(kept)
    }

    val buckets = java.util.HashMap<Int, Float>(saturated.size)
    for (swatch in saturated) {
        val hsl = swatch.hsl
        val hue = hsl[0]
        val saturation = hsl[1]
        val lightness = hsl[2]
        var saturationBoost = (if (saturation <= 0.6f) saturation else 0.6f) * 0.53f + 0.68f
        if (saturationBoost > 1.0f) saturationBoost = 1.0f
        val rangeWeight = if (hue < 12.0f || hue > 58.0f || saturation < 0.1f ||
            saturation > 0.58f || lightness < 0.16f || lightness > 0.84f
        ) {
            1.0f
        } else {
            0.78f
        }
        val weight = swatch.population * saturationBoost * rangeWeight
        val key = hueBucket(hue)
        val previous = buckets[key]
        buckets[key] = if (previous == null) weight else previous + weight
    }

    val bestBucket = maxEntry(buckets)
        ?: return maxByPopulation(kept)

    val candidates = saturated.filter { hueBucket(it.hsl[0]) == bestBucket.key }
    val best = maxByScore(candidates)
    return best ?: maxByPopulation(kept)
}

private fun hueBucket(hue: Float): Int = ((((hue + 15.0f) / 30.0f).toInt() % 12) + 12) % 12

private fun maxByPopulation(swatches: List<Swatch>): Swatch? {
    var best: Swatch? = null
    var bestPopulation = Integer.MIN_VALUE
    for (swatch in swatches) {
        if (swatch.population > bestPopulation) {
            best = swatch
            bestPopulation = swatch.population
        }
    }
    return best
}

private fun maxEntry(map: java.util.HashMap<Int, Float>): Map.Entry<Int, Float>? {
    val iterator = map.entries.iterator()
    if (!iterator.hasNext()) return null
    var best = iterator.next()
    while (iterator.hasNext()) {
        val candidate = iterator.next()
        if (best.value < candidate.value) best = candidate
    }
    return best
}

private fun maxByScore(candidates: List<Swatch>): Swatch? {
    var best: Swatch? = null
    var bestScore = 0.0f
    for (swatch in candidates) {
        val hsl = swatch.hsl
        val saturation = hsl[1]
        val lightness = hsl[2]
        val lightnessWeight = if (lightness < 0.22f || lightness > 0.78f) {
            if (lightness < 0.12f || lightness > 0.88f) 0.55f else 0.82f
        } else {
            1.0f
        }
        val clampedSaturation = if (saturation > 1.0f) 1.0f else saturation
        val score = (clampedSaturation * 0.12f + 0.88f) * swatch.population * lightnessWeight
        if (best == null || bestScore < score) {
            best = swatch
            bestScore = score
        }
    }
    return best
}

private val coverToneCache = LruCache<String, Color>(256)

private fun cachedCoverTone(url: String?): Color? =
    if (url.isNullOrBlank()) null else coverToneCache.get(url)

private suspend fun decodeCoverBitmap(context: Context, url: String): Bitmap? = try {
    val data: Any = if (url.startsWith("file://")) {
        File(url.removePrefix("file://"))
    } else {
        url
    }
    val request = ImageRequest.Builder(context)
        .data(data)
        .size(128, 128)
        .allowHardware(false)
        .build()
    val drawable = Coil.imageLoader(context).execute(request).drawable
    val bitmap = (drawable as? BitmapDrawable)?.bitmap
    if (bitmap == null) {
        null
    } else if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        bitmap
    }
} catch (throwable: Throwable) {
    null
}

private suspend fun loadCoverTone(context: Context, url: String): Color? {
    coverToneCache.get(url)?.let { return it }
    return withContext(Dispatchers.IO) {
        val bitmap = decodeCoverBitmap(context, url)
        val color = bitmap?.let { extractCoverToneOrNull(it) }
        if (color != null) coverToneCache.put(url, color)
        color
    }
}

val LocalCoverToneDisabled = staticCompositionLocalOf { false }

@Composable
fun rememberCoverTone(url: String?, fallback: Color = CoverToneFallback): Color {
    val context = LocalContext.current
    val disabled = LocalCoverToneDisabled.current
    var tone by remember(url) {
        mutableStateOf(cachedCoverTone(url))
    }
    LaunchedEffect(url, disabled) {
        if (disabled) return@LaunchedEffect
        if (tone != null) return@LaunchedEffect
        if (url == null) return@LaunchedEffect
        if (url.isBlank()) return@LaunchedEffect
        delay(80)
        tone = loadCoverTone(context, url)
    }
    val target = tone ?: fallback
    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 500),
        label = "coverTone",
    ).value
}

// TODO

// TODO(未实现): 播放页环境色（与封面主色无关，故本文件不含）——
// TODO(差异): GZ 解码对 `VideoCoverKt.a(str)` 命中的视频封面走 `VideoCoverKt.b(context, str)`
// TODO(差异): GZ 解码前用 `yl1.a(128, str)` 做 CDN URL 变换（CoverAmbientKt.java:5780-5786，yl1.java:24-45），
// TODO(差异): GZ 用 `CoverAmbientKt.b`（Mutex）串行化三处解码（kk1/ok1/mk1），本实现依赖 Coil 的请求去重。
// TODO(未确定): `CoverAmbientKt.g(String)` 命中 `file://` 时，GZ 在 Coil 失败后还会 `BitmapFactory.decodeFile`
