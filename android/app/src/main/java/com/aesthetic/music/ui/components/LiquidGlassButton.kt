package com.aesthetic.music.ui.components

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer

enum class LiquidGlassButtonStyle {
    Standard,

    Container,

    Primary,

    Navigation,
}

interface LiquidGlassBackdrop {
    fun isAvailable(): Boolean

    /** 对应 `db0.b(...)` 的回放绘制部分。GZ 原签名见类注释 TODO(未确定)。 */
    fun drawBackdrop(drawScope: DrawScope)
}

@Composable
fun rememberLiquidGlassButtonBackdrop(enabled: Boolean = true): LiquidGlassBackdrop? {
    val flag = if (enabled) LocalLiquidGlassButtonsEnabled.current else false
    if (!flag || Build.VERSION.SDK_INT < 33) return null
    // TODO(未确定): :929 `ws4.B(composer)` 返回 `px3` —— 需要把当前界面内容录制进 RenderNode/画布缓存
    return null
}

@Composable
fun Modifier.liquidGlassButtonSurface(
    backdrop: LiquidGlassBackdrop? = null,
    shape: Shape = RoundedCornerShape(percent = 50),
    style: LiquidGlassButtonStyle = LiquidGlassButtonStyle.Standard,
    enabled: Boolean = true,
    color: Color = Color.White.copy(alpha = 0.1f),
    highlightEnabled: Boolean = true,
): Modifier {
    if (!usesLiquidGlassButtons(settingsAvailable = backdrop != null)) {
        return this.background(color, shape).clip(shape)
    }

    val isPrimary = style == LiquidGlassButtonStyle.Primary
    val isContainer = style == LiquidGlassButtonStyle.Container
    val isNavigation = style == LiquidGlassButtonStyle.Navigation

    val tint = if (!enabled || isNavigation) Color.Black else Color.White

    val tintAlpha = when {
        !enabled -> 0.08f
        isNavigation -> 0.16f
        isPrimary -> 0.24f
        isContainer -> 0.14f
        else -> 0.1f
    }

    @Suppress("UNUSED_VARIABLE")
    val refractionWidthFactor = if (isPrimary || isContainer) 5.0f else 4.0f
    @Suppress("UNUSED_VARIABLE")
    val refractionHeightFactor = when {
        !enabled -> 2.0f
        isPrimary || isContainer -> 4.0f
        else -> 3.0f
    }
    @Suppress("UNUSED_VARIABLE")
    val refractionStrength = 6.0f

    // TODO(未确定): GZ 的玻璃层由 `ig2.g` → `y65.d0` → 节点 `v62(db0, qd7, rw2, wc0(7,…), y65.b, c02)`
    // （`enabled ? 1.0f : 0.0f`）。保留在签名里，接入真折射层时必须传进去。

    return this
        .graphicsLayer {
            scaleX = 1f
            scaleY = 1f
        }
        .drawBehind { backdrop?.drawBackdrop(this) }
        .background(tint.copy(alpha = tintAlpha), shape)
        .clip(shape)
}

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    backdrop: LiquidGlassBackdrop? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = 50),
    style: LiquidGlassButtonStyle = LiquidGlassButtonStyle.Standard,
    enabled: Boolean = true,
    color: Color = Color.White.copy(alpha = 0.1f),
    pressedScale: Float = 0.96f,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1.0f,
        animationSpec = tween(
            durationMillis = 150,
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
        ),
        label = "pressScale",
    )

    Box(
        modifier = modifier
            .liquidGlassButtonSurface(
                backdrop = backdrop,
                shape = shape,
                style = style,
                enabled = enabled,
                color = color,
                highlightEnabled = true,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
