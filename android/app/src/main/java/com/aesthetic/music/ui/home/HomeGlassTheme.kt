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

// 注意：这两个修饰符原先由 Antigravity 自造（自定边框渐变、底色、阴影色、边框宽度），

@Composable
fun Modifier.iosGlassCard(
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 8.dp,
): Modifier = this.glassCard(shape, elevation)

@Composable
fun Modifier.iosGlassCapsule(
    shape: Shape = RoundedCornerShape(18.dp),
    elevation: Dp = 4.dp,
): Modifier = this.glassCard(shape, elevation)

@Composable
fun HomeAmbientGlowBackground(modifier: Modifier = Modifier) {
    AuroraBackground(modifier)
}

@Composable
fun GlobalStatusBarGlass(
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    glassProgress: Float = 1f,
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (statusBarHeight <= 0.dp) return
    val isDark = Palette.isDark

    val tintAlpha = if (isDark) 0.22f * glassProgress.coerceIn(0f, 1f)
    else 0.14f * glassProgress.coerceIn(0f, 1f)

    val tintBaseColor = if (isDark) AuroraDarkBase else Color.White

    val featherHeight = 32.dp
    val totalHeight = statusBarHeight + featherHeight

    val statusFraction = (statusBarHeight.value / totalHeight.value).coerceIn(0.2f, 0.85f)
    val startTaper = statusFraction * 0.55f

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .then(hazeModifier)
    )
}
