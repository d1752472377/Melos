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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.PriorityQueue

/**
 * GZ Music「封面主色（cover tone）」提取 —— 逐值还原自反编译产物。
 *
 * ## 真实调用链（与任务描述不同的重要更正）
 *
 * GZ 首页/卡片的 cover tone **不是** `CoverAmbientKt.e(Bitmap)`/`f(Bitmap)`/`b(long, Color)` 算出来的，
 * 而是：
 *
 * ```
 * rememberCoverTone(url, fallback)                     CoverToneKt.java:68-106
 *   → LaunchedEffect: CoverAmbientKt.h(Context, url)   CoverAmbientKt.java:6037-6085（LruCache d, 256）
 *       → kk1（协程体）                                 kk1.java:100-104 / :157-161
 *           → CoverAmbientKt.c(Context, url)            CoverAmbientKt.java:5761-5830（Coil 解码 128x128、allowHardware=false）
 *           → CoverAmbientKt.o(Bitmap)                  CoverAmbientKt.java:6213-6405  ← 主色提取（中位数切分调色板 + 加权选色）
 *           → CoverAmbientKt.d(long)                    CoverAmbientKt.java:5833-5840  ← HSV 后处理（钳制）
 * ```
 *
 * `e`/`f`/`b` 属于**播放页环境色（wk1 / CoverAmbient）**那条链路（`mk1.java:138-155`：
 * `e` = 全图调色板 + 底部 45% 条带调色板加权合并；`f` = 顶部 28% 区域调色板；
 * `b(tone, accent)` = 给环境色做色相/饱和度/明度调整），与封面主色无关，故本文件不实现，
 * 见文末 TODO(未实现) 的逐字段说明。
 *
 * `CoverAmbientKt.e(Bitmap)` / `f(Bitmap)` 的返回值是 `jq5`（Swatch），不是布尔；
 * 「是否亮色封面」由 `ok1`（`CoverAmbientKt.j` 的协程体）独立计算，见 [isCoverBright]。
 *
 * ## 本文件与 GZ 的已知差异
 * - GZ 用一把 `Mutex`（`CoverAmbientKt.b`，`o87`）串行化解码，见 `ok1.smali`/`kk1.java:68`；
 *   这里依赖 Coil 自己的请求去重，不额外加锁。
 * - GZ 解码前会把 URL 过一遍 CDN 变换 `yl1.a(128, str)`（`CoverAmbientKt.java:5780`，给网易云/酷我/酷狗加
 *   `param=WxH`、改写 http→https）；Melos 的封面 URL 不需要，直接用原始 URL。
 * - GZ 对视频封面（`VideoCoverKt.a(str)` 命中集合）走 MediaMetadataRetriever 抽帧
 *   （`CoverAmbientKt.java:5818-5830`、`kk1.java:130/152`）；Melos 无视频封面，未实现。
 * - GZ 缓存 key 是调用方传入的**原始 URL 字符串**（`CoverAmbientKt.m(String)`，`CoverAmbientKt.java:6197-6203`）。
 */

// ---------------------------------------------------------------------------------------------
// 常量：全部取自反编译字面量
// ---------------------------------------------------------------------------------------------

/**
 * 封面主色兜底值：GZ `CoverToneKt.a = hp1.e(4287532691L)` = `0xFF8E8E93`
 * —— `CoverToneKt.java:28`（`hp1.e(long)` = `Color(0x........)`，`hp1.java:3160-3164`）。
 *
 * 也是 `m299rememberCoverToneiJQMabo(..., j, ...)` 的默认实参：`(i2 & 2) != 0 ? a : j`（`CoverToneKt.java:71`）。
 * 注意与 [DefaultCoverTone]（`0xFFFA243C` = `CoverToneKt.b`，`CoverToneKt.java:29`）区分：
 * 后者是**选中态强调色**，不是封面主色兜底值。
 */
val CoverToneFallback: Color = Color(0xFF8E8E93) // CoverToneKt.java:28

/** 量化调色板的目标色数：GZ `iq5Var.c = 24`（`CoverAmbientKt.java:6217`，同 `:5857`/`:5862`）。 */
private const val QUANTIZE_MAX_COLORS = 24 // CoverAmbientKt.java:6217

/**
 * 量化前把位图缩小的最大像素数：`iq5.d = 12544`（`iq5.java:27`），
 * 使用点 `iq5.java:58-64`：`w*h > 12544` 时 `scale = sqrt(12544 / (w*h))`。
 */
private const val QUANTIZE_MAX_PIXEL_AREA = 12544 // iq5.java:27

/** 5bit/通道直方图大小：`new int[32768]`（`iq5.java:101`）= `1 shl (5*3)`。 */
private const val HISTOGRAM_SIZE = 32768 // iq5.java:101

/** 亮度判定的采样边长：`const/16 v1, 0x8` → `createScaledBitmap(bmp, 8, 8, true)`（`ok1.smali` line 165-170）。 */
private const val BRIGHT_SAMPLE_SIZE = 8 // ok1.smali:165-170

/**
 * 亮度判定的分母：`const-wide v0, 0x40cfe00000000000L # 16320.0`（`ok1.smali`:235）= `255.0 * 8 * 8`。
 */
private const val BRIGHT_LUMA_DIVISOR = 16320.0 // ok1.smali:235

/** 亮色阈值：`const-wide v0, 0x3fdccccccccccccdL # 0.45`，`cmpl-double` + `if-lez` → `> 0.45`（`ok1.smali`:241-248）。 */
private const val BRIGHT_THRESHOLD = 0.45 // ok1.smali:241-248

// 亮度权重：`0.299 / 0.587 / 0.114`（`ok1.smali`:191/202/215，BT.601 luma）。

// ---------------------------------------------------------------------------------------------
// 纯函数 1：主色提取
// ---------------------------------------------------------------------------------------------

/**
 * 提取封面主色，取不到返回 `null` —— 忠实还原 `CoverAmbientKt.o(Bitmap)` 返回 `null` 的语义
 * （`CoverAmbientKt.java:6224`、`:6404`，`kk1.java:101-102` 把 null 当作「本次不要更新状态」）。
 *
 * 调用方需保证 [bitmap] 未 recycle；与 GZ 一样，位图不应是 `Bitmap.Config.HARDWARE`
 * （GZ 在解码处就把 HARDWARE 拷成 ARGB_8888：`CoverAmbientKt.java:5816`）。
 */
fun extractCoverToneOrNull(bitmap: Bitmap): Color? {
    val swatch = dominantCoverSwatch(bitmap) ?: return null // CoverAmbientKt.java:6224 / :6404
    // kk1.java:104 / :161 —— hp1.d(swatch.d) 是 Color(Int)，再走 d(long) 后处理
    return adjustCoverTone(Color(swatch.rgb))
}

/**
 * 提取封面主色，取不到时返回 [fallback]（默认 = `CoverToneKt.a` = `0xFF8E8E93`）。
 *
 * 等价于 GZ：`j2 = (i2 & 2) != 0 ? a : j; ... if (g91Var != null) j2 = g91Var.a;`
 * （`CoverToneKt.java:71`、`:101-104`）。
 */
fun extractCoverTone(bitmap: Bitmap, fallback: Color = CoverToneFallback): Color =
    extractCoverToneOrNull(bitmap) ?: fallback // CoverToneKt.java:71 / :101-104

/**
 * HSV 后处理 —— 忠实还原 `CoverAmbientKt.d(long)`（`CoverAmbientKt.java:5833-5840`）。
 *
 * ```
 * Color.colorToHSV(toArgb(color), hsv)
 * hsv[1] = if (s < 0.06f) 0f else coerceIn(s * 1.05f + 0.02f, 0.16f, 0.8f)   // :5836-5837
 * hsv[2] = coerceIn(hsv[2], 0.38f, 0.72f)                                     // :5838
 * Color(HSVToColor(hsv))
 * ```
 * `coerceIn` = `ka7.v(f, min, max)`（`ka7.java:1090-1098`）。
 */
fun adjustCoverTone(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv) // CoverAmbientKt.java:5835
    val saturation = hsv[1]
    hsv[1] = if (saturation < 0.06f) { // CoverAmbientKt.java:5837
        0.0f
    } else {
        coerceIn(saturation * 1.05f + 0.02f, 0.16f, 0.8f) // CoverAmbientKt.java:5837
    }
    hsv[2] = coerceIn(hsv[2], 0.38f, 0.72f) // CoverAmbientKt.java:5838
    return Color(android.graphics.Color.HSVToColor(hsv)) // CoverAmbientKt.java:5839（hp1.d）
}

/** `ka7.v(f, min, max)`（`ka7.java:1090-1098`）。 */
private fun coerceIn(value: Float, min: Float, max: Float): Float =
    if (value < min) min else if (value > max) max else value

// ---------------------------------------------------------------------------------------------
// 纯函数 2：是否亮色封面
// ---------------------------------------------------------------------------------------------

/**
 * 是否亮色封面 —— 忠实还原 `ok1.invokeSuspend`（`CoverAmbientKt.j(Context, String)` 的协程体，
 * `ok1.smali` invokeSuspend:162-254；`CoverAmbientKt.java:6139-6187` 只负责 LruCache c 缓存）。
 *
 * ```
 * scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)      // ok1.smali:165-170（filter = true）
 * sum = Σ_{y<8} Σ_{x<8} (R*0.299 + G*0.587 + B*0.114)         // ok1.smali:174-229
 * if (scaled != bitmap) scaled.recycle()                       // ok1.smali:230-232
 * return sum / 16320.0 > 0.45                                  // ok1.smali:235-248
 * ```
 * 注意：GZ 的亮度判定**不走** `CoverAmbientKt.f(Bitmap)` 调色板，而是 8x8 缩放后取 BT.601 平均亮度。
 */
fun isCoverBright(bitmap: Bitmap): Boolean {
    val scaled = Bitmap.createScaledBitmap(bitmap, BRIGHT_SAMPLE_SIZE, BRIGHT_SAMPLE_SIZE, true) // ok1.smali:165-170
    var lumaSum = 0.0 // ok1.smali:174（const-wide/16 v7, 0x0）
    for (y in 0 until BRIGHT_SAMPLE_SIZE) { // ok1.smali:176-229
        for (x in 0 until BRIGHT_SAMPLE_SIZE) {
            val pixel = scaled.getPixel(x, y) // ok1.smali:182
            lumaSum += android.graphics.Color.red(pixel) * 0.299 + // ok1.smali:186-191
                android.graphics.Color.green(pixel) * 0.587 + // ok1.smali:197-202
                android.graphics.Color.blue(pixel) * 0.114 // ok1.smali:210-215
        }
    }
    if (scaled !== bitmap) scaled.recycle() // ok1.smali:230-232
    return lumaSum / BRIGHT_LUMA_DIVISOR > BRIGHT_THRESHOLD // ok1.smali:235-248
}

// ---------------------------------------------------------------------------------------------
// 调色板量化（iq5 + jq5 + i91 的逐值移植）
// ---------------------------------------------------------------------------------------------

/** `jq5`（androidx `Palette.Swatch`）：RGB888 + 像素数，HSL 用 `z91.a`（androidx ColorUtils.RGBToHSL）。 */
private class Swatch(val rgb: Int, val population: Int) { // jq5.java:19-25

    /** `jq5.b()`（`jq5.java:53-59`）→ `z91.a`（`z91.java:10-38`）。GZ 每次调用都重算，结果恒定。 */
    val hsl: FloatArray by lazy(LazyThreadSafetyMode.NONE) {
        val out = FloatArray(3)
        rgbToHsl(out, android.graphics.Color.red(rgb), android.graphics.Color.green(rgb), android.graphics.Color.blue(rgb))
        out
    }
}

/** `z91.a(fArr, r, g, b)` = androidx `ColorUtils.RGBToHSL`（`z91.java:10-38`，逐行）。 */
private fun rgbToHsl(out: FloatArray, r: Int, g: Int, b: Int) {
    val rf = r / 255.0f // z91.java:13
    val gf = g / 255.0f // z91.java:14
    val bf = b / 255.0f // z91.java:15
    val max = maxOf(rf, maxOf(gf, bf)) // z91.java:16
    val min = minOf(rf, minOf(gf, bf)) // z91.java:17
    val delta = max - min // z91.java:18
    val lightness = (max + min) / 2.0f // z91.java:19
    var hue: Float
    var saturation: Float
    if (max == min) { // z91.java:20-22
        hue = 0.0f
        saturation = 0.0f
    } else {
        hue = if (max == rf) { // z91.java:24-28
            ((gf - bf) / delta) % 6.0f
        } else if (max == gf) {
            ((bf - rf) / delta) + 2.0f
        } else {
            ((rf - gf) / delta) + 4.0f
        }
        saturation = delta / (1.0f - kotlin.math.abs(2.0f * lightness - 1.0f)) // z91.java:29
    }
    var h = (hue * 60.0f) % 360.0f // z91.java:31
    if (h < 0.0f) h += 360.0f // z91.java:32-34
    out[0] = if (h < 0.0f) 0.0f else minOf(h, 360.0f) // z91.java:35
    out[1] = if (saturation < 0.0f) 0.0f else minOf(saturation, 1.0f) // z91.java:36
    out[2] = if (lightness >= 0.0f) minOf(lightness, 1.0f) else 0.0f // z91.java:37
}

/** `i91.z(v, from, to)`（`i91.java:414-416`）= androidx `modifyWordWidth`。 */
private fun modifyWordWidth(value: Int, fromBits: Int, toBits: Int): Int { // i91.java:414-416
    val shifted = if (toBits > fromBits) value shl (toBits - fromBits) else value shr (fromBits - toBits)
    return shifted and ((1 shl toBits) - 1)
}

/** 5bit 量化值 → RGB888：`iq5.java:119`/`:150`/`:218` 的 `Color.rgb(z(...,5,8), ...)`。 */
private fun approximateToRgb888(quantized: Int): Int = android.graphics.Color.rgb( // iq5.java:150
    modifyWordWidth((quantized shr 10) and 31, 5, 8), // 红：5bit→8bit
    modifyWordWidth((quantized shr 5) and 31, 5, 8), // 绿
    modifyWordWidth(quantized and 31, 5, 8), // 蓝
)

/**
 * `h91`（androidx `ColorCutQuantizer.Vbox`）：量化数组上的一个区间 + 统计（`h91.java:7-73`）。
 * 排序轴常量：`-3` = 红、`-2` = 绿、`-1` = 蓝（`iq5.java:172` 的三元表达式解出）。
 */
private class Box(
    private val colors: IntArray, // i91Var.a
    private val histogram: IntArray, // i91Var.b
    var minIndex: Int, // h91.a
    var maxIndex: Int, // h91.b
) {
    var volume = 0 // h91.c
    private var minRed = 0; private var maxRed = 0 // h91.d / h91.e
    private var minGreen = 0; private var maxGreen = 0 // h91.f / h91.g
    private var minBlue = 0; private var maxBlue = 0 // h91.h / h91.i

    init {
        recompute() // h91.java:23
    }

    /** `h91.a()`（`h91.java:26-69`，`Log.LOG_LEVEL_OFF` = `Integer.MAX_VALUE`）。 */
    fun recompute() {
        var minR = Integer.MAX_VALUE // h91.java:30
        var maxG = Integer.MIN_VALUE // h91.java:31
        var maxB = Integer.MIN_VALUE // h91.java:32
        var total = 0 // h91.java:33
        var minG = Integer.MAX_VALUE // h91.java:34
        var minB = Integer.MAX_VALUE // h91.java:35
        var maxR = Integer.MIN_VALUE // h91.java:36
        for (i in minIndex..maxIndex) { // h91.java:37-61
            val q = colors[i] // h91.java:38
            total += histogram[q] // h91.java:39
            val r = (q shr 10) and 31 // h91.java:40
            val g = (q shr 5) and 31 // h91.java:41
            val b = q and 31 // h91.java:42
            if (r > maxR) maxR = r // h91.java:43-45
            if (r < minR) minR = r // h91.java:46-48
            if (g > maxG) maxG = g // h91.java:49-51
            if (g < minG) minG = g // h91.java:52-54
            if (b > maxB) maxB = b // h91.java:55-57
            if (b < minB) minB = b // h91.java:58-60
        }
        minRed = minR; this.maxRed = maxR // h91.java:62-63
        minGreen = minG; this.maxGreen = maxG // h91.java:64-65
        minBlue = minB; this.maxBlue = maxB // h91.java:66-67
        volume = total // h91.java:68
    }

    /** `h91.b()`（`h91.java:71-73`）= `(Δb+1) * (Δg+1) * (Δr+1)`，用于优先队列排序（`lp2.java:164-165`）。 */
    fun boxVolume(): Int =
        ((maxBlue - minBlue) + 1) * ((maxGreen - minGreen) + 1) * ((maxRed - minRed) + 1) // h91.java:72

    /** 红轴跨度：`iq5.java:169` 的 `h91Var.e - h91Var.d`。 */
    fun redLength(): Int = maxRed - minRed // iq5.java:169

    /** 绿轴跨度：`iq5.java:170` 的 `h91Var.g - h91Var.f`。 */
    fun greenLength(): Int = maxGreen - minGreen // iq5.java:170

    /** 蓝轴跨度：`iq5.java:171` 的 `h91Var.i - h91Var.h`。 */
    fun blueLength(): Int = maxBlue - minBlue // iq5.java:171
}

/** `i91.y(colors, axis, from, to)`（`i91.java:395-412`）= androidx `Vbox.modifyWordsForSort`。 */
private fun modifyWordsForSort(colors: IntArray, axis: Int, from: Int, to: Int) { // i91.java:395-412
    if (axis == -2) { // 绿轴：把绿换到最高 5 位（i91.java:396-402）
        for (i in from..to) {
            val c = colors[i]
            colors[i] = (c and 31) or (((c shr 5) and 31) shl 10) or (((c shr 10) and 31) shl 5)
        }
        return
    }
    if (axis != -1) return // 红轴（-3）本就最高位，无需变换（i91.java:404-406）
    for (i in from..to) { // 蓝轴：红蓝互换（i91.java:407-411）
        val c = colors[i]
        colors[i] = ((c shr 10) and 31) or ((c and 31) shl 10) or (((c shr 5) and 31) shl 5)
    }
}

/**
 * 量化 + 中位数切分，返回调色板色表 —— 忠实移植 `iq5.a()`（`iq5.java:45-326`）在
 * 「`f.clear()` 清掉过滤器 + 未调用 `b(...)` 设定区域」这一确切调用形态下的路径
 * （`CoverAmbientKt.java:6216-6219`）。
 *
 * 过滤器被清空 → `iq5.java:97` 的 `hq5VarArr == null` → `i91.C(...)` 恒为 `false`
 * （`i91.java:447-462`），因此「忽略近黑/近白/近红」的默认过滤在本路径**不生效**，
 * 未使用 `i91` 的过滤逻辑（后续由 [dominantCoverSwatch] 的亮度区间自行筛除）。
 *
 * 返回顺序即 GZ 的顺序：色数 ≤ [maxColors] 时按量化值升序；否则按 `PriorityQueue` 的**内部堆数组顺序**
 * （`iq5.java:201` 直接 `for (h91 box : priorityQueue)`，非排序顺序），因此这里用 `java.util.PriorityQueue`
 * 逐一复刻插入顺序。
 */
private fun quantizePalette(bitmap: Bitmap, maxColors: Int): List<Swatch> {
    // iq5.java:58-71 —— 先按面积上限缩小
    val area = bitmap.width * bitmap.height // iq5.java:61
    val scale = if (area > QUANTIZE_MAX_PIXEL_AREA) { // iq5.java:62-64
        Math.sqrt(QUANTIZE_MAX_PIXEL_AREA.toDouble() / area)
    } else {
        -1.0 // iq5.java:58（初始 dSqrt = -1.0d）
    }
    val scaled = if (scale <= 0.0) { // iq5.java:71
        bitmap
    } else {
        Bitmap.createScaledBitmap(
            bitmap,
            Math.ceil(bitmap.width * scale).toInt(), // iq5.java:71（ceil）
            Math.ceil(bitmap.height * scale).toInt(),
            false, // iq5.java:71（filter = false）
        )
    }
    val width = scaled.width // iq5.java:80
    val height = scaled.height // iq5.java:81
    val pixels = IntArray(width * height) // iq5.java:82
    scaled.getPixels(pixels, 0, width, 0, 0, width, height) // iq5.java:83

    // iq5.java:104-114 —— 5bit/通道量化 + 直方图（alpha 完全忽略）
    val histogram = IntArray(HISTOGRAM_SIZE) // iq5.java:101
    for (i in pixels.indices) { // iq5.java:104-114
        val pixel = pixels[i]
        val quantized = (modifyWordWidth(android.graphics.Color.green(pixel), 8, 5) shl 5) or
            (modifyWordWidth(android.graphics.Color.red(pixel), 8, 5) shl 10) or
            modifyWordWidth(android.graphics.Color.blue(pixel), 8, 5) // iq5.java:110
        pixels[i] = quantized // iq5.java:111
        histogram[quantized] = histogram[quantized] + 1 // iq5.java:112
    }

    // iq5.java:115-145 —— 收集出现过的量化值（升序），过滤器为空所以不会被清零
    var uniqueCount = 0 // iq5.java:116（i10）
    for (q in 0 until HISTOGRAM_SIZE) { // iq5.java:117-135
        if (histogram[q] > 0) uniqueCount++
    }
    val colors = IntArray(uniqueCount) // iq5.java:137
    var write = 0
    for (q in 0 until HISTOGRAM_SIZE) { // iq5.java:140-145
        if (histogram[q] > 0) colors[write++] = q
    }

    if (uniqueCount <= maxColors) { // iq5.java:146-152
        val swatches = ArrayList<Swatch>(uniqueCount)
        for (i in 0 until uniqueCount) {
            val q = colors[i]
            swatches.add(Swatch(approximateToRgb888(q), histogram[q])) // iq5.java:150
        }
        if (scaled !== bitmap) scaled.recycle() // iq5.java:225-227
        return swatches
    }

    // iq5.java:155-199 —— 中位数切分；比较器 = 体积降序（lp2.java:164-165）
    val queue = PriorityQueue<Box>(
        maxColors,
        Comparator { a: Box, b: Box -> b.boxVolume() - a.boxVolume() },
    ) // iq5.java:155
    queue.offer(Box(colors, histogram, 0, colors.size - 1)) // iq5.java:156
    while (queue.size < maxColors) { // iq5.java:157
        val box = queue.poll() ?: break
        if ((box.maxIndex + 1) - box.minIndex <= 1) break // iq5.java:161-163（该 box 被丢弃，与 GZ 一致）
        val redLength = box.redLength() // iq5.java:169
        val greenLength = box.greenLength() // iq5.java:170
        val blueLength = box.blueLength() // iq5.java:171
        val axis = if (redLength < greenLength || redLength < blueLength) { // iq5.java:172
            if (greenLength < redLength || greenLength < blueLength) -1 else -2
        } else {
            -3
        }
        modifyWordsForSort(colors, axis, box.minIndex, box.maxIndex) // iq5.java:175
        java.util.Arrays.sort(colors, box.minIndex, box.maxIndex + 1) // iq5.java:176
        modifyWordsForSort(colors, axis, box.minIndex, box.maxIndex) // iq5.java:177
        val half = box.volume / 2 // iq5.java:178
        var splitIndex = box.minIndex // iq5.java:179（iMin 复用）
        var accumulated = 0 // iq5.java:180
        var i = box.minIndex
        while (i <= box.maxIndex) { // iq5.java:181-192
            accumulated += histogram[colors[i]] // iq5.java:186
            if (accumulated >= half) { // iq5.java:187-190
                splitIndex = minOf(box.maxIndex - 1, i)
                break
            }
            i++
        }
        val upper = Box(colors, histogram, splitIndex + 1, box.maxIndex) // iq5.java:193
        box.maxIndex = splitIndex // iq5.java:194
        box.recompute() // iq5.java:195（h91.a()）
        queue.offer(upper) // iq5.java:196
        queue.offer(box) // iq5.java:197
    }

    // iq5.java:200-222 —— 每个 box 求加权平均色（5bit 空间累加，再放大到 8bit）
    val swatches = ArrayList<Swatch>(queue.size) // iq5.java:200
    for (box in queue) { // iq5.java:201（迭代顺序 = 堆数组顺序）
        var total = 0 // iq5.java:205
        var sumRed = 0 // iq5.java:206
        var sumGreen = 0 // iq5.java:207
        var sumBlue = 0 // iq5.java:208
        for (i in box.minIndex..box.maxIndex) { // iq5.java:209-216
            val q = colors[i] // iq5.java:210
            val count = histogram[q] // iq5.java:211
            total += count
            sumRed += ((q shr 10) and 31) * count // iq5.java:213
            sumGreen += ((q shr 5) and 31) * count // iq5.java:214
            sumBlue += (q and 31) * count // iq5.java:215
        }
        val totalFloat = total.toFloat() // iq5.java:217
        val rgb = android.graphics.Color.rgb( // iq5.java:218
            modifyWordWidth(Math.round(sumRed / totalFloat), 5, 8),
            modifyWordWidth(Math.round(sumGreen / totalFloat), 5, 8),
            modifyWordWidth(Math.round(sumBlue / totalFloat), 5, 8),
        )
        // iq5.java:219 —— i91Var.C(hsl) 恒 false（过滤器为空），所以无条件加入
        swatches.add(Swatch(rgb, total)) // iq5.java:220
    }
    if (scaled !== bitmap) scaled.recycle() // iq5.java:225-227
    return swatches
}

// ---------------------------------------------------------------------------------------------
// `CoverAmbientKt.o(Bitmap)`：从调色板里挑主色
// ---------------------------------------------------------------------------------------------

/**
 * 主色挑选 —— 忠实还原 `CoverAmbientKt.o(Bitmap)`（`CoverAmbientKt.java:6213-6405`）。
 *
 * 步骤（每步的行号都在下方注释里）：
 * 1. 量化 24 色（`:6216-6219`），`dominant` = 像素数最多的色（`:6220` / `iq5.java:233-244`，先到先得）。
 * 2. 色表为空 → `null`（`:6223-6225`）。
 * 3. 只保留明度 `0.05..0.95` 的色（`:6230-6233`）。
 * 4. 若其中「饱和度 ≥ 0.06」的色像素和 ≥ 总像素 `12%`（`:6254`、`:6266`），
 *    按 `hueBucket(h) = ((h+15)/30 mod 12 + 12) mod 12`（`:6289`、`:6193-6195`/`l(float)`）
 *    累加权重 `population * ((min(s,0.6)*0.53)+0.68 上限 1.0) * (命中 12..58° / 0.1..0.58 / 0.16..0.84 → 0.78，否则 1.0)`
 *    （`:6281-6288`），取权重最大的桶（`:6294-6310`，HashMap 迭代序 + 先到先得）。
 * 5. 在该桶的候选里取 `((min(s,1)*0.12)+0.88) * population * 明度权重`
 *    最大者（`:6330-6362`；明度权重：`0.22..0.78`→1.0，`0.12..0.88`→0.82，其余 0.55）。
 * 6. 任何一步取不到，都退回第 3 步集合里像素数最多的色（`:6312-6328`、`:6367-6382`、`:6384-6402`）。
 */
private fun dominantCoverSwatch(bitmap: Bitmap): Swatch? {
    val swatches = quantizePalette(bitmap, QUANTIZE_MAX_COLORS) // CoverAmbientKt.java:6216-6219
    val dominant = maxByPopulation(swatches) // CoverAmbientKt.java:6220（ug3VarA.g）
    if (swatches.isEmpty()) return null // CoverAmbientKt.java:6223-6225

    val kept = swatches.filter { swatch -> // CoverAmbientKt.java:6229-6234
        val lightness = swatch.hsl[2]
        lightness >= 0.05f && lightness <= 0.95f // CoverAmbientKt.java:6231
    }
    if (kept.isEmpty()) return dominant // CoverAmbientKt.java:6404

    var keptPopulation = 0 // CoverAmbientKt.java:6237-6243
    for (swatch in kept) keptPopulation += swatch.population
    val minPopulation = if (keptPopulation < 1) 1 else keptPopulation // CoverAmbientKt.java:6244-6247

    val saturated = kept.filter { it.hsl[1] >= 0.06f } // CoverAmbientKt.java:6248-6257
    var saturatedPopulation = 0 // CoverAmbientKt.java:6258-6265
    for (swatch in saturated) saturatedPopulation += swatch.population
    if (saturated.isEmpty() || saturatedPopulation < minPopulation * 0.12f) { // CoverAmbientKt.java:6266
        return maxByPopulation(kept) // CoverAmbientKt.java:6384-6402
    }

    // CoverAmbientKt.java:6267-6293 —— 色相桶加权；用 java.util.HashMap 保持与 GZ 相同的迭代顺序
    val buckets = java.util.HashMap<Int, Float>(saturated.size)
    for (swatch in saturated) { // CoverAmbientKt.java:6270-6293
        val hsl = swatch.hsl
        val hue = hsl[0] // CoverAmbientKt.java:6278
        val saturation = hsl[1] // CoverAmbientKt.java:6279
        val lightness = hsl[2] // CoverAmbientKt.java:6280
        var saturationBoost = (if (saturation <= 0.6f) saturation else 0.6f) * 0.53f + 0.68f // CoverAmbientKt.java:6281
        if (saturationBoost > 1.0f) saturationBoost = 1.0f // CoverAmbientKt.java:6282-6284
        val rangeWeight = if (hue < 12.0f || hue > 58.0f || saturation < 0.1f ||
            saturation > 0.58f || lightness < 0.16f || lightness > 0.84f
        ) {
            1.0f // CoverAmbientKt.java:6285-6287
        } else {
            0.78f // CoverAmbientKt.java:6271
        }
        val weight = swatch.population * saturationBoost * rangeWeight // CoverAmbientKt.java:6288
        val key = hueBucket(hue) // CoverAmbientKt.java:6289（l(float)）
        val previous = buckets[key] // CoverAmbientKt.java:6292（rk1 = Float::plus，见 rk1.java:6-11）
        buckets[key] = if (previous == null) weight else previous + weight
    }

    val bestBucket = maxEntry(buckets) // CoverAmbientKt.java:6294-6310
        ?: return maxByPopulation(kept) // CoverAmbientKt.java:6312-6328

    // CoverAmbientKt.java:6330-6362 —— 在命中该桶的候选里取分数最高者
    val candidates = saturated.filter { hueBucket(it.hsl[0]) == bestBucket.key } // CoverAmbientKt.java:6330（f91 case 1）
    val best = maxByScore(candidates)
    return best ?: maxByPopulation(kept) // CoverAmbientKt.java:6363-6382
}

/** `CoverAmbientKt.l(float)`（`CoverAmbientKt.java:6193-6195`）：`((((int)((h+15)/30)) % 12) + 12) % 12`。 */
private fun hueBucket(hue: Float): Int = ((((hue + 15.0f) / 30.0f).toInt() % 12) + 12) % 12 // CoverAmbientKt.java:6194

/** `maxBy { it.e }`（先出现者胜出）：`CoverAmbientKt.java:5964-5980`、`:6313-6327`、`:6367-6382`、`iq5.java:233-244`。 */
private fun maxByPopulation(swatches: List<Swatch>): Swatch? {
    var best: Swatch? = null
    var bestPopulation = Integer.MIN_VALUE // iq5.java:234 / CoverAmbientKt.java:6317
    for (swatch in swatches) {
        if (swatch.population > bestPopulation) { // Float.compare/`<` 严格大于才替换
            best = swatch
            bestPopulation = swatch.population
        }
    }
    return best
}

/** `HashMap<Integer, Float>` 取最大值的 entry（先出现者胜出）：`CoverAmbientKt.java:6294-6310`。 */
private fun maxEntry(map: java.util.HashMap<Int, Float>): Map.Entry<Int, Float>? {
    val iterator = map.entries.iterator()
    if (!iterator.hasNext()) return null // CoverAmbientKt.java:6308-6310
    var best = iterator.next()
    while (iterator.hasNext()) { // CoverAmbientKt.java:6299-6306
        val candidate = iterator.next()
        if (best.value < candidate.value) best = candidate
    }
    return best
}

/** `CoverAmbientKt.java:6331-6362` 的评分：`((min(s,1)*0.12)+0.88) * population * 明度权重`。 */
private fun maxByScore(candidates: List<Swatch>): Swatch? {
    var best: Swatch? = null
    var bestScore = 0.0f
    for (swatch in candidates) {
        val hsl = swatch.hsl
        val saturation = hsl[1] // CoverAmbientKt.java:6335
        val lightness = hsl[2] // CoverAmbientKt.java:6336
        val lightnessWeight = if (lightness < 0.22f || lightness > 0.78f) { // CoverAmbientKt.java:6337
            if (lightness < 0.12f || lightness > 0.88f) 0.55f else 0.82f
        } else {
            1.0f
        }
        val clampedSaturation = if (saturation > 1.0f) 1.0f else saturation // CoverAmbientKt.java:6339-6341
        val score = (clampedSaturation * 0.12f + 0.88f) * swatch.population * lightnessWeight // CoverAmbientKt.java:6342
        if (best == null || bestScore < score) { // CoverAmbientKt.java:6354（Float.compare(f13, f18) < 0）
            best = swatch
            bestScore = score
        }
    }
    return best // CoverAmbientKt.java:6363-6366
}

// ---------------------------------------------------------------------------------------------
// 缓存
// ---------------------------------------------------------------------------------------------

/**
 * GZ `CoverAmbientKt.c`（`LruCache(256)`，`CoverAmbientKt.java:123`）：URL → 是否亮色。
 * GZ 在 `CoverAmbientKt.j(...)`（`:6183-6185`）与 `rememberCoverIsBright`（`CoverToneKt.java:46`）读写它。
 */
private val coverBrightCache = LruCache<String, Boolean>(256) // CoverAmbientKt.java:123

/**
 * GZ `CoverAmbientKt.d`（`LruCache(256)`，`CoverAmbientKt.java:124`）：URL → 主色（`g91`）。
 * GZ 在 `CoverAmbientKt.h(...)`（`:6081-6083`）与 `CoverAmbientKt.m(String)`（`:6197-6203`）读写它。
 */
private val coverToneCache = LruCache<String, Color>(256) // CoverAmbientKt.java:124

/** 缓存命中查询：GZ `CoverAmbientKt.m(String)`（`CoverAmbientKt.java:6197-6203`，空串返回 null）。 */
private fun cachedCoverTone(url: String?): Color? =
    if (url.isNullOrBlank()) null else coverToneCache.get(url) // CoverAmbientKt.java:6199-6202

/** 缓存命中查询：GZ `CoverToneKt.java:46` / `CoverAmbientKt.j(...)` 的 `lruCache.get(str)`。 */
private fun cachedCoverBright(url: String?): Boolean? =
    if (url.isNullOrBlank()) null else coverBrightCache.get(url) // CoverToneKt.java:46

// ---------------------------------------------------------------------------------------------
// 解码
// ---------------------------------------------------------------------------------------------

/**
 * 解码封面为可安全读取像素的位图。
 *
 * 对应 GZ `CoverAmbientKt.c(Context, String, ...)`（`CoverAmbientKt.java:5761-5830`）：
 * - `hk3Var.d(128, 128)` → `size(128, 128)`（`:5789`）
 * - `hk3Var.k = Boolean.FALSE` → `allowHardware(false)`（`:5790`）
 * - `hk3Var.c = g(strA)` → `file://` 前缀转成 [File]（`:5788`，`g(String)` 见 `:6031-6034`）
 * - HARDWARE 位图拷成 ARGB_8888（`:5816`）；异常/解码失败 → `null`（`:5824-5829`）
 * - 视频封面（`VideoCoverKt.a/b`）未实现，见文件头差异说明
 */
private suspend fun decodeCoverBitmap(context: Context, url: String): Bitmap? = try {
    val data: Any = if (url.startsWith("file://")) { // CoverAmbientKt.java:6033（kw7.B）
        File(url.removePrefix("file://")) // CoverAmbientKt.java:6033（dw7.a0）
    } else {
        url
    }
    val request = ImageRequest.Builder(context) // CoverAmbientKt.java:5787（hk3）
        .data(data) // CoverAmbientKt.java:5788
        .size(128, 128) // CoverAmbientKt.java:5789
        .allowHardware(false) // CoverAmbientKt.java:5790
        .build()
    val drawable = Coil.imageLoader(context).execute(request).drawable // CoverAmbientKt.java:5792-5795（p81.r + tn6.c）
    val bitmap = (drawable as? BitmapDrawable)?.bitmap // CoverAmbientKt.java:5810-5811
    if (bitmap == null) {
        null // CoverAmbientKt.java:5812-5819（GZ 还支持 Drawable.toBitmap 与 file:// 直解，见差异说明）
    } else if (bitmap.config == Bitmap.Config.HARDWARE) { // CoverAmbientKt.java:5816
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        bitmap
    }
} catch (throwable: Throwable) { // CoverAmbientKt.java:5824-5829
    null
}

/** GZ `CoverAmbientKt.h(Context, String)`（`:6037-6085`）：查缓存 → 解码 → 提取 → 回填缓存。 */
private suspend fun loadCoverTone(context: Context, url: String): Color? {
    coverToneCache.get(url)?.let { return it } // CoverAmbientKt.java:6058-6061
    return withContext(Dispatchers.IO) { // CoverAmbientKt.java:6062-6067（or1.f = Dispatchers.IO，见 or1.java:8、:47）
        val bitmap = decodeCoverBitmap(context, url) // CoverAmbientKt.java:6064（kk1）
        val color = bitmap?.let { extractCoverToneOrNull(it) } // CoverAmbientKt.java:6220-6404（o + d）
        if (color != null) coverToneCache.put(url, color) // CoverAmbientKt.java:6081-6083
        color
    }
}

/** GZ `CoverAmbientKt.j(Context, String)`（`:6139-6187`）：查缓存 → 解码 → [isCoverBright] → 回填缓存。 */
private suspend fun loadCoverBright(context: Context, url: String): Boolean? {
    coverBrightCache.get(url)?.let { return it } // CoverAmbientKt.java:6160-6163
    return withContext(Dispatchers.IO) {
        val bitmap = decodeCoverBitmap(context, url) // ok1.smali:148（CoverAmbientKt.c）
        val bright = bitmap?.let { isCoverBright(it) } // ok1.smali:162-254
        if (bright != null) coverBrightCache.put(url, bright) // CoverAmbientKt.java:6183-6185
        bright
    }
}

// ---------------------------------------------------------------------------------------------
// Composable 封装
// ---------------------------------------------------------------------------------------------

/**
 * GZ `CoverToneKt.c`：`new if1(new xk1(3))`（`CoverToneKt.java:30`），默认值来自 `xk1.java:54-56`
 * （`case 3: return Boolean.FALSE`）→ `staticCompositionLocalOf { false }`（`if1` 的 case 0 无 mutation policy，
 * 见 `if1.java:14-22`）。
 *
 * 语义：**为 true 时跳过封面主色提取**。GZ 只在 `rememberCoverTone` 里读它（`CoverToneKt.java:74`），
 * 并在卡片行外层用 `CompositionLocalProvider(CoverToneKt.c provides (!local || forceShow))` 提供
 * （`NewAlbumSectionKt.java:514-520`）。Melos 侧由调用方决定是否提供；不提供就是默认 `false`（照常提取）。
 */
val LocalCoverToneDisabled = staticCompositionLocalOf { false } // CoverToneKt.java:30 / xk1.java:54-56

/**
 * `CoverToneKt.m299rememberCoverToneiJQMabo(url, fallback)`（`CoverToneKt.java:68-106`）。
 *
 * 还原要点：
 * - 初始值 = 主色缓存（`:79-84`，`CoverAmbientKt.m(url)`），缓存未命中则是 `null` → 动画目标 = [fallback]（`:101-104`）。
 * - `LaunchedEffect(url, disabled)`（`:86-100`；两个 key：`zf8.i` 见 `zf8.java:2537-2546`）。
 * - 协程体（`wl1.invokeSuspend`，`wl1.smali` case 0）：`disabled` → 返回；已有值 → 返回；
 *   `url == null` → 返回；`url.isBlank()` → 返回；`delay(80)`；`state = CoverAmbientKt.h(context, url)`。
 * - 最终 `animateColorAsState(target, tween(500), label = "coverTone")`（`:105`；
 *   `qk5.r0(500, 6, null)` = `tween(durationMillis = 500, delayMillis = 0, easing = FastOutSlowInEasing)`
 *   —— `qk5.java:1654-1663` + `e92.java:6`（`wm1(0.4f, 0f, 0.2f, 1f)`），第三个实参默认值 = `e92.a`）。
 */
@Composable
fun rememberCoverTone(url: String?, fallback: Color = CoverToneFallback): Color {
    val context = LocalContext.current // CoverToneKt.java:73
    val disabled = LocalCoverToneDisabled.current // CoverToneKt.java:74
    var tone by remember(url) { // CoverToneKt.java:79-84（remember 的 key 只有 url）
        mutableStateOf(cachedCoverTone(url))
    }
    LaunchedEffect(url, disabled) { // CoverToneKt.java:86-100（LaunchedEffect(url, disabled)）
        if (disabled) return@LaunchedEffect // wl1.smali case 0（if (r4 != 0) return）
        if (tone != null) return@LaunchedEffect // wl1.smali（state.value != null → return）
        if (url == null) return@LaunchedEffect // wl1.smali（url == null → return）
        if (url.isBlank()) return@LaunchedEffect // wl1.smali（dw7.S(url) → return）
        delay(80) // wl1.smali（zf8.C(80, ...) = delay(80)）
        tone = loadCoverTone(context, url) // wl1.smali（CoverAmbientKt.h）→ CoverToneKt.java:100
    }
    val target = tone ?: fallback // CoverToneKt.java:101-104
    return animateColorAsState( // CoverToneKt.java:105
        targetValue = target,
        animationSpec = tween(durationMillis = 500), // qk5.java:1654-1663 + e92.java:6（默认 easing）
        label = "coverTone", // CoverToneKt.java:105（label 字面量）
    ).value
}

/**
 * `CoverToneKt.rememberCoverIsBright(url, default)`（`CoverToneKt.java:32-66`）。
 *
 * 还原要点：
 * - 初始值 = 亮色缓存（`:45-48`，空串 → `null`）。
 * - `LaunchedEffect(url)`（`:62`；单 key，`zf8.h` 见 `zf8.java:2423-2432`）。
 * - 协程体（`gk1.invokeSuspend` 的 packed-switch case 1，`gk1.smali` `:pswitch_9f`）：
 *   已有值 → 返回；`url == null` → 返回；`url.isBlank()` → 返回；`delay(80)`；`state = CoverAmbientKt.j(context, url)`。
 *   （该 case **没有** `LocalCoverToneDisabled` 守卫；`gk1.smali` 里那两条 `sget CoverToneKt.c` 是死代码。）
 * - 返回 `state ?: default`（`:63-64`），**没有**动画。
 */
@Composable
fun rememberCoverIsBright(url: String?, default: Boolean): Boolean {
    val context = LocalContext.current // CoverToneKt.java:36
    var bright by remember(url) { // CoverToneKt.java:39-48（remember 的 key 只有 url）
        mutableStateOf(cachedCoverBright(url))
    }
    LaunchedEffect(url) { // CoverToneKt.java:62（LaunchedEffect(url)）
        if (bright != null) return@LaunchedEffect // gk1.smali:192（if-nez → 非空即返回）
        if (url == null) return@LaunchedEffect // gk1.smali:196
        if (url.isBlank()) return@LaunchedEffect // gk1.smali:198-204
        delay(80) // gk1.smali:206-208（zf8.C(0x50, ...)）
        bright = loadCoverBright(context, url) // gk1.smali:217-218（CoverAmbientKt.j）
    }
    return bright ?: default // CoverToneKt.java:63-64
}

// ---------------------------------------------------------------------------------------------
// TODO
// ---------------------------------------------------------------------------------------------

// TODO(未实现): 播放页环境色（与封面主色无关，故本文件不含）——
//   `CoverAmbientKt.e(Bitmap)`（CoverAmbientKt.java:5843-5981）：24 色量化两次（全图 + `b(0.45h, w, h)` 底部条带，
//       iq5.java:328-340），用 `p(...)`（:6407-6415）按权重 1.0 / 1.35 合并到 LinkedHashMap，
//       再按明度 0.06..0.92、饱和度 ≥ 0.1 过滤（:5879-5893），
//       按权重 `b * (sat>=0.12 ? 1.0 : 0.6) * (hue∈[5,50] 且 sat∈[0.12,0.72] 且 li∈[0.25,0.82] ? 0.3 : 1.0)`
//       分到 12 个色相桶（:5898-5914，桶函数 :6193-6195），取最大桶（:5915-5932），
//       桶内按 `t(au8)`（:6463-6482）取分数最高者；全部失败则退回明度最大者，再退回 `ug3VarA.g`（:5961-5980）。
//   `CoverAmbientKt.f(Bitmap)`（:5983-6029）：顶部 `[0, 0.28h)`（`ka7.w((int)(h*0.28f), 1, h)`，:5993）
//       16 色量化一次（:5994-5998），依次取 `ug3VarA.g` → 目标 `p48.e`（vibrant）→ `p48.h`（muted）→ 像素数最多者
//       （:5999-6028）。目标色表 p48 的字面量见 p48.java:12-67。
//   `CoverAmbientKt.b(long, g91)`（:5721-5758）：环境色二次调整（饱和度 0.1/0.15 换色相、明度分档 0.4/0.48/0.58/0.78 等）。
//   三者都只在 `mk1.java:138-155`（wk1 / 播放页 ambient）被调用。
//
// TODO(差异): GZ 解码对 `VideoCoverKt.a(str)` 命中的视频封面走 `VideoCoverKt.b(context, str)`
//   （MediaMetadataRetriever 抽帧，kk1.java:130-152 / ok1.smali:118-124）；Melos 无视频封面，未实现。
// TODO(差异): GZ 解码前用 `yl1.a(128, str)` 做 CDN URL 变换（CoverAmbientKt.java:5780-5786，yl1.java:24-45），
//   本实现直接使用原始 URL。
// TODO(差异): GZ 用 `CoverAmbientKt.b`（Mutex）串行化三处解码（kk1/ok1/mk1），本实现依赖 Coil 的请求去重。
// TODO(未确定): `CoverAmbientKt.g(String)` 命中 `file://` 时，GZ 在 Coil 失败后还会 `BitmapFactory.decodeFile`
//   兜底（CoverAmbientKt.java:5818-5830）；本实现只走 Coil。Melos 未使用 file:// 封面，未复刻。
