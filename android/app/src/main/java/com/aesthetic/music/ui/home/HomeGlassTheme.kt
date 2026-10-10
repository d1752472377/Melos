package com.aesthetic.music.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.components.AuroraBackground
import com.aesthetic.music.ui.components.AuroraDarkBase
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.ui.theme.Palette
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.util.Calendar

// ==================== 全局玻璃卡片（委托 GZ 真值 token） ====================
//
// 注意：这两个修饰符原先由 Antigravity 自造（自定边框渐变、底色、阴影色、边框宽度），
// 其数值在 GZ 反编译源里并不存在。现已改为直接委托 GZ 的真值 token：
//   com.aesthetic.music.ui.components.glassCard(shape, elevation)
//     ← 逐值还原自 gz_decompiled/.../ui/GlassTokensKt.java:1247-1266
//        （深色且 elevation>0 时叠黑色 4%/8% 阴影，然后
//          border(1dp, glassBorder(), shape) → background(glassBg(), shape) → clip(shape)）
// 保留函数名与签名，是因为全工程有 46 处调用它们，重命名会牵连大量文件。

/**
 * 玻璃拟态卡片修饰符。现为 GZ 真值 `Modifier.glassCard` 的别名。
 */
@Composable
fun Modifier.iosGlassCard(
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 8.dp,
): Modifier = this.glassCard(shape, elevation)

/**
 * 玻璃胶囊（搜索栏 / 紧凑容器）。同 [iosGlassCard]，也是 GZ 真值 token 的别名，
 * 胶囊形状由调用方通过 `shape` 传入。
 */
@Composable
fun Modifier.iosGlassCapsule(
    shape: Shape = RoundedCornerShape(18.dp),
    elevation: Dp = 4.dp,
): Modifier = this.glassCard(shape, elevation)

// ==================== 全局极光流光背景（GZ 真值） ====================

/**
 * 页面底层极光流光背景。现在是 GZ 真值实现 [AuroraBackground] 的别名。
 *
 * 全工程有 20 处调用它（AppShell / 各页面），保留函数名避免牵连调用方。
 */
@Composable
fun HomeAmbientGlowBackground(modifier: Modifier = Modifier) {
    // [已替换] 原 Antigravity 自造的环境光画布已删除：
    //   深/浅色共 10 个色值（0xFF2E2356 / 0xFF3F1D35 / 0xFF14273E / 0xFF2E2112 /
    //   0xFFE2E6FF / 0xFFFFECE6 / 0xFFFFF7E2 / 0xFFE5F5FD / 0xFF0F1014 / 0xFFF4F6F9）
    //   与 18000 / 24000 / 12000 ms 三个动画时长，在 GZ 反编译源里全都不存在。
    // 现改为委托 GZ Music 的真值实现：ui/components/AuroraBackground.kt
    // （底色 + 4 个 radial blob + 2/3 个补光，坐标/半径/色值/透明度逐值抠自
    //   AuroraBackgroundKt.java:29-40、defpackage/p70.java:38-83、defpackage/p0.java:104-126）
    AuroraBackground(modifier)
}

// ==================== 时段与场景辅助工具 ====================
// [已删除] Antigravity 自造的 HomeTimeScene / rememberTimeScene 已移除：
// 其中的 4 个场景强调色（0xFFFFA000 / 0xFF7B61FF / 0xFF10B981 / 0xFFFF9F43）与
// 全部场景文案在 GZ 反编译源里都不存在，属凭空编造。
// GZ 的真实「时段问候 / 场景」逻辑在 HomeScreenKt.java 的 HomeTopBar 里
// （反编译源行 9571 起），由 HomeHeader.kt 承载。

// ==================== 全局通知栏/状态栏毛玻璃条 ====================

/**
 * 全局状态栏/通知栏毛玻璃悬浮条：
 * 1. 正常静止时：高透磨砂玻璃底色，与下方底层环境光背景完全融为一体（无任何割裂或黑边）；
 * 2. 页面上滑时：文字、图片、卡片从其下方滑过，透过半透明毛玻璃呈现出柔化、磨砂的玻璃折射质感；
 * 3. 底部带有微弱高光反光边缘与轻微渐变羽化，呈现纯正 iOS 玻璃质感；
 * 4. 保证状态栏系统图标（时钟、电量、通知图标）在任何滑动内容经过时都能清晰阅读。
 */
@Composable
fun GlobalStatusBarGlass(
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    glassProgress: Float = 1f,
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (statusBarHeight <= 0.dp) return
    val isDark = Palette.isDark

    // 适度调低遮罩底色不透明度，保留底层丰富色彩饱和度，避免形成泛白的厚重色块
    val tintAlpha = if (isDark) 0.22f * glassProgress.coerceIn(0f, 1f)
    else 0.14f * glassProgress.coerceIn(0f, 1f)

    val tintBaseColor = if (isDark) AuroraDarkBase else Color.White // [GZ 真值] DarkBase = AuroraBackgroundKt.java:30 hp1.e(4279110930L)

    // 将羽化延展区扩展至 32.dp，让高斯模糊与底色自然消融到正文中，彻底消除任何割裂线条
    val featherHeight = 32.dp
    val totalHeight = statusBarHeight + featherHeight

    // 计算状态栏标称边界在整体高度中的比例
    val statusFraction = (statusBarHeight.value / totalHeight.value).coerceIn(0.2f, 0.85f)
    val startTaper = statusFraction * 0.55f

    // 采用三次平滑 S 曲线（Smoothstep）构建无阶跃、无硬边缘的连续透明度梯度
    val maskStops = arrayOf(
        0.0f to Color.Black,
        startTaper to Color.Black,
        (startTaper + (statusFraction - startTaper) * 0.5f) to Color.Black.copy(alpha = 0.95f),
        statusFraction to Color.Black.copy(alpha = 0.82f),
        (statusFraction + (1f - statusFraction) * 0.25f) to Color.Black.copy(alpha = 0.55f),
        (statusFraction + (1f - statusFraction) * 0.50f) to Color.Black.copy(alpha = 0.30f),
        (statusFraction + (1f - statusFraction) * 0.75f) to Color.Black.copy(alpha = 0.10f),
        (statusFraction + (1f - statusFraction) * 0.90f) to Color.Black.copy(alpha = 0.03f),
        1.0f to Color.Transparent,
    )

    val hazeModifier = if (hazeState != null) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeDefaults.style(
                backgroundColor = tintBaseColor.copy(alpha = tintAlpha),
                blurRadius = (26.dp * glassProgress.coerceIn(0.1f, 1f)),
                noiseFactor = 0.02f,
            )
        ) {
            mask = Brush.verticalGradient(colorStops = maskStops)
        }
    } else {
        Modifier.background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to tintBaseColor.copy(alpha = tintAlpha),
                    startTaper to tintBaseColor.copy(alpha = tintAlpha),
                    statusFraction to tintBaseColor.copy(alpha = tintAlpha * 0.82f),
                    (statusFraction + (1f - statusFraction) * 0.50f) to tintBaseColor.copy(alpha = tintAlpha * 0.30f),
                    1.0f to Color.Transparent,
                )
            )
        )
    }

    // 单个连贯容器：使整个区域（含状态栏与延展羽化区）统一置于高斯模糊与连续遮罩下，完全消除边界与色块切痕
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .then(hazeModifier)
    )
}
