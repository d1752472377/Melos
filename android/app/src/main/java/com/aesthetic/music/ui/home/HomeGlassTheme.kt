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
import androidx.compose.runtime.Composable
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
import com.aesthetic.music.ui.theme.Palette
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.util.Calendar

// ==================== 全局 iOS 拟态样式规范 ====================

/**
 * iOS 风格玻璃拟态卡片修饰符：
 * 1. 柔和扩散阴影（仿 iOS 浮层质感）
 * 2. 边缘物理高光反光边框（上亮下透，呈现玻璃边缘折射）
 * 3. 半透明磨砂底色（透出底层环境光晕）
 */
fun Modifier.iosGlassCard(
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 8.dp,
): Modifier {
    val isDark = Palette.isDark

    // 高光反光边框渐变
    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.24f),
                Color.White.copy(alpha = 0.08f),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.90f),
                Color.White.copy(alpha = 0.35f),
            )
        )
    }

    // 毛玻璃底色（浅色 74% 纯白，深色 68% 曜石黑）
    val glassBg = if (isDark) {
        Color(0xFF1E1F27).copy(alpha = 0.70f)
    } else {
        Color.White.copy(alpha = 0.74f)
    }

    // 环境光与投射阴影颜色
    val ambientShadow = if (isDark) Color.Black.copy(alpha = 0.55f) else Color(0xFF7A8699).copy(alpha = 0.12f)
    val spotShadow = if (isDark) Color.Black.copy(alpha = 0.65f) else Color(0xFF5A687D).copy(alpha = 0.14f)

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = ambientShadow,
            spotColor = spotShadow,
        )
        .border(
            width = if (isDark) 1.dp else 1.2.dp,
            brush = borderBrush,
            shape = shape,
        )
        .clip(shape)
        .background(glassBg)
}

/**
 * iOS 风格玻璃胶囊（用于搜索栏或紧凑容器）
 */
fun Modifier.iosGlassCapsule(
    shape: Shape = RoundedCornerShape(18.dp),
    elevation: Dp = 4.dp,
): Modifier {
    val isDark = Palette.isDark

    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.06f),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.92f),
                Color.White.copy(alpha = 0.40f),
            )
        )
    }

    val glassBg = if (isDark) {
        Color(0xFF22232D).copy(alpha = 0.72f)
    } else {
        Color.White.copy(alpha = 0.82f)
    }

    val ambientShadow = if (isDark) Color.Black.copy(alpha = 0.45f) else Color(0xFF7A8699).copy(alpha = 0.10f)
    val spotShadow = if (isDark) Color.Black.copy(alpha = 0.55f) else Color(0xFF5A687D).copy(alpha = 0.12f)

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = ambientShadow,
            spotColor = spotShadow,
        )
        .border(
            width = 1.dp,
            brush = borderBrush,
            shape = shape,
        )
        .clip(shape)
        .background(glassBg)
}

// ==================== 全局 iOS 环境光画布背景 ====================

/**
 * 首页底层环境光背景：
 * 在全屏绘制柔和漫射的多色光斑（Ambient Glow Mesh），
 * 当上层半透明玻璃卡片覆盖其上时，能透出逼真的 iOS 磨砂光晕感。
 */
@Composable
fun HomeAmbientGlowBackground(modifier: Modifier = Modifier) {
    val isDark = Palette.isDark

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 底色铺底
        drawRect(if (isDark) Color(0xFF0F1014) else Color(0xFFF4F6F9))

        if (isDark) {
            // 深色模式：深邃夜空下的微醺紫罗兰、酒红与暗夜蓝光晕
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2E2356).copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(w * 0.15f, h * 0.10f),
                    radius = w * 0.85f,
                ),
                center = Offset(w * 0.15f, h * 0.10f),
                radius = w * 0.85f,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3F1D35).copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(w * 0.85f, h * 0.25f),
                    radius = w * 0.75f,
                ),
                center = Offset(w * 0.85f, h * 0.25f),
                radius = w * 0.75f,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF14273E).copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(w * 0.30f, h * 0.55f),
                    radius = w * 0.70f,
                ),
                center = Offset(w * 0.30f, h * 0.55f),
                radius = w * 0.70f,
            )
        } else {
            // 浅色模式：淡雅马卡龙微光晕（丁香紫、晨曦粉、温暖琥珀、天青蓝）
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE2E6FF).copy(alpha = 0.80f), Color.Transparent),
                    center = Offset(w * 0.15f, h * 0.08f),
                    radius = w * 0.85f,
                ),
                center = Offset(w * 0.15f, h * 0.08f),
                radius = w * 0.85f,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFECE6).copy(alpha = 0.70f), Color.Transparent),
                    center = Offset(w * 0.88f, h * 0.22f),
                    radius = w * 0.75f,
                ),
                center = Offset(w * 0.88f, h * 0.22f),
                radius = w * 0.75f,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF7E2).copy(alpha = 0.65f), Color.Transparent),
                    center = Offset(w * 0.15f, h * 0.45f),
                    radius = w * 0.65f,
                ),
                center = Offset(w * 0.15f, h * 0.45f),
                radius = w * 0.65f,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE5F5FD).copy(alpha = 0.60f), Color.Transparent),
                    center = Offset(w * 0.82f, h * 0.70f),
                    radius = w * 0.70f,
                ),
                center = Offset(w * 0.82f, h * 0.70f),
                radius = w * 0.70f,
            )
        }
    }
}

// ==================== 时段与场景辅助工具 ====================

data class HomeTimeScene(
    val title: String,
    val subtitle: String,
    val timeTag: String,
    val icon: ImageVector,
    val accentColor: Color,
)

/**
 * 根据当前小时数动态匹配场景与文案（完美契合图 2 中的“晚风散步 | 把步调放轻，让音乐陪你走走 | 18:00-23:00”）
 */
@Composable
fun rememberTimeScene(): HomeTimeScene {
    return remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            // 傍晚散步时段 (18:00 - 23:00)
            hour in 18..22 -> HomeTimeScene(
                title = "晚风散步",
                subtitle = "把步调放轻，让音乐陪你走走",
                timeTag = "18:00-23:00",
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                accentColor = Color(0xFFFFA000), // 温暖琥珀橙
            )
            // 深夜静谧时段 (23:00 - 06:00)
            hour >= 23 || hour < 6 -> HomeTimeScene(
                title = "深夜私语",
                subtitle = "让温柔的旋律陪伴安静的夜",
                timeTag = "23:00-06:00",
                icon = Icons.Filled.Bedtime,
                accentColor = Color(0xFF7B61FF), // 治愈紫罗兰
            )
            // 清晨活力时段 (06:00 - 11:00)
            hour in 6..10 -> HomeTimeScene(
                title = "清晨漫步",
                subtitle = "用清澈音乐唤醒活力一天",
                timeTag = "06:00-11:00",
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                accentColor = Color(0xFF10B981), // 清新薄荷绿
            )
            // 午后专注与漫游 (11:00 - 18:00)
            else -> HomeTimeScene(
                title = "午后漫游",
                subtitle = "在温暖阳光下享受惬意时光",
                timeTag = "11:00-18:00",
                icon = Icons.Filled.WbSunny,
                accentColor = Color(0xFFFF9F43), // 暖阳金
            )
        }
    }
}

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

    val tintBaseColor = if (isDark) Color(0xFF0F1014) else Color.White

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
