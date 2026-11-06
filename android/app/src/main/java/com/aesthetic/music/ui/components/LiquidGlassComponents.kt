package com.aesthetic.music.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.appIsDark
import com.aesthetic.music.ui.theme.AppSpacing

@Composable
fun LiquidGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    height: Dp = 48.dp,
    tint: Color? = null,
) {
    val contentColor = tint ?: textHigh()
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = shape,
        style = LiquidGlassButtonStyle.Container,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(19.dp),
                    )
                    if (text.isNotBlank()) Spacer(Modifier.width(AppSpacing.xs))
                }
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                }
            }
        },
    )
}

@Composable
fun LiquidBentoCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.96f else 1.0f,
        animationSpec = tween(
            durationMillis = 150,
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
        ),
        label = "bento_scale",
    )

    val clickableMod = if (onClick != null) {
        Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .scale(scale)
            .glassCard(shape, elevation)
            .then(clickableMod),
        content = content,
    )
}

@Composable
fun LiquidCapsuleChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = DefaultCoverTone,
) {
    val dark = appIsDark()
    val shape = RoundedCornerShape(22.dp)

    val bg = if (selected) {
        accentColor.copy(alpha = if (dark) 0.28f else 0.16f)
    } else {
        glassBg()
    }

    val contentColor = if (selected) accentColor else textLow()

    Row(
        modifier = modifier
            .glassCard(shape, elevation = 0.dp)
            .background(bg, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xl, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 13.5.sp,
            fontWeight = if (selected) {
                androidx.compose.ui.text.font.FontWeight.Bold
            } else {
                androidx.compose.ui.text.font.FontWeight.Medium
            },
        )
    }
}
