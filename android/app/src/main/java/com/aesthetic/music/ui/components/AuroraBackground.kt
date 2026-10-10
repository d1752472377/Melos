package com.aesthetic.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.aesthetic.music.ui.theme.appIsDark

/**
 * GZ Music「极光流光」背景 —— 逐值精确还原。
 *
 * 还原来源（全部为反编译文件里的字面量，无任何推测值）：
 *  - `gz_decompiled/sources/com/gzmusic/app/ui/AuroraBackgroundKt.java:29-40`
 *      底色常量 a / b、4 个 blob 的 k13(色值, x, y, 半径)、渐变色标 float[]{0, 0.55, 1}
 *  - `gz_decompiled/sources/defpackage/p70.java:38-83`（case 0）
 *      实际几何算法：按宽度 390 缩放、两类分支（深色 2 个 blob / 浅色 4+3 个 blob）、
 *      每个 blob 的 (x, y, r) 与两级 alpha
 *  - `gz_decompiled/sources/defpackage/p0.java:104-126`（case 4）
 *      绘制方式：drawRect(底色或竖向渐变) → 逐个 drawCircle(brush=radialGradient, radius, center)
 *
 * 原实现的绘制契约（用于校对）：
 *   scale = size.width / 390f
 *   深色：drawRect(Brush.horizontalGradient(#15131A → b → #0A090D)) + 2 个 blob
 *   浅色：drawRect(color = a) + 4 个 blob + 3 个补光 blob
 *   blob 画刷 = Brush.radialGradient(
 *       colorStops = [0f → color.copy(alpha = a0), 0.55f → color.copy(alpha = a55), 1f → color.copy(alpha = 0f)],
 *       center = Offset(x, y), radius = r)
 */

/** AuroraBackgroundKt.java:29 —— `a = hp1.e(4294638332L)`，浅色底色（同时用作浅色分支的纯色填充） */
val AuroraLightBase = Color(0xFFFAFAFC)

/** AuroraBackgroundKt.java:30 —— `b = hp1.e(4279110930L)`，深色底色（同时用作深色竖向渐变的中间色） */
val AuroraDarkBase = Color(0xFF0E0D12)

/** p70.java:52 —— 几何缩放参考宽度，`width / 390.0f` */
private const val AURORA_SCALE_REFERENCE = 390f

/** p70.java:43 —— 深色分支底色竖向渐变的两个端点色 */
private val AuroraDarkTop = Color(0xFF15131A)
private val AuroraDarkBottom = Color(0xFF0A090D)

/**
 * AuroraBackgroundKt.java:39 —— 浅色分支的 4 个 blob：
 * `d = listOf(k13(4294961368L, -0.85f, -0.55f, 360f), k13(4292925951L, 0.85f, -0.35f, 340f),
 *             k13(4292147440L, -0.55f, 0.75f, 320f), k13(4294961368L, 0.6f, 0.85f, 300f))`
 */
private class AuroraBlob(
    val color: Color,
    val x: Float,
    val y: Float,
    val radius: Float,
)

private val AuroraLightBlobs = listOf(
    AuroraBlob(Color(0xFFFFE8D8), -0.85f, -0.55f, 360f),
    AuroraBlob(Color(0xFFE0D9FF), 0.85f, -0.35f, 340f),
    AuroraBlob(Color(0xFFD4F8F0), -0.55f, 0.75f, 320f),
    AuroraBlob(Color(0xFFFFE8D8), 0.60f, 0.85f, 300f),
)

/**
 * 单个 radial-gradient 光斑。
 *
 * 对应 `AuroraBackgroundKt.java:77-81` 的 `a(x, y, radius, color, a0, a55)`：
 * 色标固定为 `c = float[]{0.0f, 0.55f, 1.0f}`，第三档是 `g91.c(0.0f, color)` 即同色全透明。
 */
private fun DrawScope.drawAuroraBlob(
    x: Float,
    y: Float,
    radius: Float,
    color: Color,
    alpha0: Float,
    alpha55: Float,
) {
    val center = Offset(x, y)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = alpha0),
                0.55f to color.copy(alpha = alpha55),
                1f to color.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * 铺满可用区域的极光流光背景。对应 `AuroraBackgroundKt.AuroraBackground(modifier)`。
 *
 * 页面里作为最底层使用，例如 `AuroraBackground(Modifier.fillMaxSize())`。
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val dark = appIsDark()
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val scale = size.width / AURORA_SCALE_REFERENCE
                val width = size.width
                val height = size.height

                onDrawBehind {
                    if (dark) {
                        // p70.java:43 —— 深色底色 **横向** 渐变。
                        // 依据：defpackage/pr6.java:378 `e0(List,float,float,int)` 构造的是
                        // `new ob4(list, null, Offset(0f, startY), Offset(0f, endY))` —— x 恒为 0，
                        // 即 `Brush.horizontalGradient(colors)`（不是 verticalGradient）。
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(AuroraDarkTop, AuroraDarkBase, AuroraDarkBottom)
                            )
                        )

                        // p70.java:53 —— 左上暖紫光斑：x=(180-0.3)*s, y=(220-0.4)*s, r=320*s
                        drawAuroraBlob(
                            x = (180f - 0.3f) * scale,
                            y = (220f - 0.4f) * scale,
                            radius = 320f * scale,
                            color = Color(0xFF3A2A4A),
                            alpha0 = 0.085f,
                            alpha55 = 0.025f,
                        )
                        // p70.java:54 —— 右下青墨光斑：x=w-60*s, y=h-120*s, r=280*s
                        drawAuroraBlob(
                            x = width - 60f * scale,
                            y = height - 120f * scale,
                            radius = 280f * scale,
                            color = Color(0xFF1A2E36),
                            alpha0 = 0.075f,
                            alpha55 = 0.022f,
                        )
                    } else {
                        // p0.java:121 —— 浅色分支是纯色填充 petColor a
                        drawRect(color = AuroraLightBase)

                        // p70.java:61-67 —— 4 个 blob：半径先按 scale 缩放，再取一半作为实际半径；
                        // 圆心把归一化坐标 (x+1)/2 映射到 (宽-直径) 上，再加半径偏移
                        for (blob in AuroraLightBlobs) {
                            val diameter = blob.radius * scale
                            val half = diameter / 2f
                            drawAuroraBlob(
                                x = (width - diameter) * ((blob.x + 1f) / 2f) + half,
                                y = (height - diameter) * ((blob.y + 1f) / 2f) + half,
                                radius = half,
                                color = blob.color,
                                alpha0 = 0.45f,
                                alpha55 = 0.15f,
                            )
                        }

                        // p70.java:73 —— 左上珊瑚光斑：x=(-80+380/2)*s, y=(-120+380/2)*s, r=380/2*s
                        drawAuroraBlob(
                            x = (-80f * scale) + (380f * scale / 2f),
                            y = (-120f * scale) + (380f * scale / 2f),
                            radius = 380f * scale / 2f,
                            color = Color(0xFFFF9A76),
                            alpha0 = 0.094f,
                            alpha55 = 0.031f,
                        )
                        // p70.java:75 —— 右上暖黄光斑：x=(80+300/2)*s, y=(-100+300/2)*s, r=300/2*s
                        drawAuroraBlob(
                            x = (80f * scale) + (300f * scale / 2f),
                            y = (-100f * scale) + (300f * scale / 2f),
                            radius = 300f * scale / 2f,
                            color = Color(0xFFFFD175),
                            alpha0 = 0.078f,
                            alpha55 = 0.024f,
                        )
                        // p70.java:78 —— 右上粉光斑：x=(w-20*s-240*s)+120*s, y=(-80*s)+120*s, r=240/2*s
                        drawAuroraBlob(
                            x = (width - 20f * scale - 240f * scale) + (240f * scale / 2f),
                            y = (-80f * scale) + (240f * scale / 2f),
                            radius = 240f * scale / 2f,
                            color = Color(0xFFF8C0D0),
                            alpha0 = 0.063f,
                            alpha55 = 0.016f,
                        )
                    }
                }
            }
    )
}
