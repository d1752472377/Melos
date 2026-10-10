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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * 每根跳动条动画的起始延迟（毫秒）。
 *
 * 来源：`PlayingBarsKt.java:39` —— `public static final int[] a = {0, 120, PsExtractor.VIDEO_STREAM_MASK}`；
 * `androidx/media3/extractor/ts/PsExtractor.java:34` 中 `VIDEO_STREAM_MASK = 240`。
 */
private val PlayingBarDelayMillis = intArrayOf(0, 120, 240) // PlayingBarsKt.java:39

/**
 * 未播放（静止）时三根条各自的高度占比。
 *
 * 来源：`PlayingBarsKt.java:40` —— `public static final float[] b = {0.45f, 0.7f, 0.35f}`。
 */
private val PlayingBarRestFractions = floatArrayOf(0.45f, 0.7f, 0.35f) // PlayingBarsKt.java:40

/**
 * 播放中的跳动波形条（逐值还原自 GZ `PlayingBarsKt.PlayingBars-FNF3uiM`）。
 *
 * 结构（全部来自反编译字节码）：
 * ```
 * Row(                                      // PlayingBarsKt.java:120-122
 *     modifier = modifier.height(16.dp),
 *     horizontalArrangement = Arrangement.spacedBy(2.dp),
 *     verticalAlignment = Alignment.Bottom,
 * ) {
 *     for (index in 0 until 3) {            // PlayingBarsKt.java:142
 *         Box(                              // PlayingBarsKt.java:154
 *             Modifier
 *                 .width(3.dp)
 *                 .fillMaxHeight(fraction)
 *                 .clip(RoundedCornerShape(1.5.dp))
 *                 .background(color, RectangleShape)
 *         )
 *     }
 * }
 * ```
 *
 * @param isPlaying 是否正在播放：true 时三根条做无限循环涨落，false 时取静止占比。
 *   GZ 原参数名已被混淆为 `z`（`PlayingBarsKt.java:63`），此处沿用调用方既有的 `isPlaying` 命名。
 * @param modifier 外层修饰符，GZ 默认值为 `Modifier`（`PlayingBarsKt.java:111-114` 的 `c05.a`）。
 * @param color 条的颜色，GZ 默认值为 `Color.White`（`PlayingBarsKt.java:116-118` 的 `g91.d`，
 *   即 `g91.java:11` 声明、`g91.java:23` 赋值 `hp1.e(4294967295L)` = `0xFFFFFFFF`）。
 */
@Composable
fun PlayingBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    // PlayingBarsKt.java:120 —— `new j10(2.0f, true, new c(9))`
    //   j10 = Arrangement.SpacedAligned(space=2.0f, rtlMirror=true, alignment=lambda)
    //   c(9) 的 lambda 体 = `Alignment.Start.align(0, size, layoutDirection)`
    //   （与 foundation-layout 1.7.4 `Arrangement.spacedBy(space: Dp)` 的定义逐字相同，
    //    见 Arrangement.kt）→ 1 参重载 spacedBy(2.dp) 与 spacedBy(2.dp, Alignment.Start)
    //    字节码完全等价，此处取惯用的 1 参写法。
    // PlayingBarsKt.java:121 —— `il0.g0` = `new wg0(1.0f)`（il0.java:62），即 Alignment.Bottom。
    // PlayingBarsKt.java:122 —— `ij7.i(f05Var2, 16.0f)` = SizeElement(minHeight=16.dp, maxHeight=16.dp)
    //   （Size.kt: `Modifier.height`），即 Modifier.height(16.dp)。
    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // PlayingBarsKt.java:142 —— `while (i7 < 3)`，固定 3 根条。
        for (index in 0 until 3) {
            val fraction = if (isPlaying) {
                // PlayingBarsKt.java:146 —— `yf2.H("playingBars", ...)` = rememberInfiniteTransition(label = "playingBars")
                val transition = rememberInfiniteTransition(label = "playingBars")
                // PlayingBarsKt.java:146 ——
                //   `yf2.g(transition, 0.35f, 1.0f, qk5.Z(new tf8(420, a[i7], e92.b), it6.d, 0L, 4), ku4.f(i7, "bar"), ...)`
                //   initialValue = 0.35f、targetValue = 1.0f（字面量）
                //   tf8 = TweenSpec(durationMillis = 420, delayMillis = a[index], easing = e92.b)
                //   e92.b = `new v02()`（e92.java:12），`v02.e(float f) { return f; }`（v02.java:185-187）
                //     = LinearEasing
                //   it6.d = RepeatMode.Reverse（it6.java:15）
                //   ku4.f(i, "bar") = `"bar" + i`（ku4.java:28-30）
                transition.animateFloat(
                    initialValue = 0.35f, // PlayingBarsKt.java:146
                    targetValue = 1.0f, // PlayingBarsKt.java:146
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 420, // PlayingBarsKt.java:146 (`new tf8(420, ...)`)
                            delayMillis = PlayingBarDelayMillis[index], // PlayingBarsKt.java:146 (`a[i7]`)
                            easing = LinearEasing, // PlayingBarsKt.java:146 (`e92.b`)
                        ),
                        repeatMode = RepeatMode.Reverse, // PlayingBarsKt.java:146 (`it6.d`)
                    ),
                    label = "bar$index", // PlayingBarsKt.java:146 (`ku4.f(i7, "bar")`)
                ).value
            } else {
                // PlayingBarsKt.java:152 —— `fFloatValue = b[i7]`
                PlayingBarRestFractions[index]
            }

            // PlayingBarsKt.java:154 ——
            //   jj0.a(hp1.s(hq3.g(ij7.c(ij7.r(c05Var, 3.0f), fFloatValue), by6.a(1.5f)), j5, hp1.n), ...)
            //   由内到外：
            //     ij7.r(Modifier, 3.0f)          = SizeElement(minWidth=3.dp, maxWidth=3.dp) = width(3.dp)
            //     ij7.c(..., fFloatValue)        = FillElement(Direction.Vertical, fraction)  = fillMaxHeight(fraction)
            //     hq3.g(..., by6.a(1.5f))        = graphicsLayer(shape = RoundedCornerShape(1.5.dp), clip = true) = clip(...)
            //                                      （by6.a(1.5f) = RoundedCornerShape(DpCornerSize(1.5.dp))，by6.java:13-16）
            //     hp1.s(..., j5, hp1.n)          = Modifier.background(color, RectangleShape)
            //                                      （hp1.java:3976-3978；hp1.n = `new ue3(2)`，hp1.java:50，
            //                                        ue3.java 的 toString() 返回 "RectangleShape"）
            //     jj0.a(...)                     = Box(modifier)（无 content 重载，Box.kt:237 + EmptyBoxMeasurePolicy:241）
            //   j5 = 上一个分支解析出的颜色，即本 composable 的 color 参数（PlayingBarsKt.java:145/149/157，
            //   默认值 g91.d = Color.White 见 PlayingBarsKt.java:116-118）。
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color, RectangleShape),
            )
        }
    }
}
