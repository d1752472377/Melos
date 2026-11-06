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

/**
 * Melos 侧的液态玻璃控件封装。
 *
 * ## ⚠️ 关于本文件的历史（必读）
 * 本文件此前由 Antigravity 编写，头部注释声称「复刻 GZ Music LiquidGlassComponents &
 * LiquidGlassButton 规范」，**但这是不成立的**：它用到的数值在 GZ 反编译源里全部不存在，
 * 属于凭空编造 —— 其中包括 Tailwind 色板
 * （`0xFF3B82F6`、`0xFF94A3B8`、`0xFF64748B`、`0xFF475569`、`0xFF1E293B`、`0xFFE2E8F0`、
 * `0xFF262835`、`0xFF1E212B`、`0xFF232530`）、以及凭空的 `spring(dampingRatio = 0.72f,
 * stiffness = 420f)` 等动画参数。
 *
 * 现已全部改写为对 **GZ 真值**的薄封装：
 *  - [LiquidGhostButton]  → GZ 的 [LiquidGlassButton] 的 `Container` 档
 *    （tint 白 14%，见 `LiquidGlassButtonKt.java:884`）
 *  - [LiquidBentoCard]    → GZ 的 [glassCard]（`GlassTokensKt.java:1247-1266`）
 *  - [LiquidCapsuleChip]  → GZ 的 [glassBg] / [glassBorder] / [DefaultCoverTone] 组合
 *
 * GZ 并没有与后三者一一对应的组件，它们是 Melos 侧的组合封装，因此这里只做
 * 「用真值 token 拼装」，不再自定任何颜色与动画数值。
 *
 * 原先那个签名含 `text/icon/accentColor/height` 的编造版 `LiquidGlassButton` 已删除，
 * 真正的 GZ 还原版在 `LiquidGlassButton.kt`（逐值还原自 `LiquidGlassButtonKt.java`）。
 */

/**
 * 次级「幽灵」玻璃按钮。语义对应 GZ `LiquidGlassButton` 的 `Container` 档：
 * `LiquidGlassButtonKt.java:884` 的 tint alpha = 0.14f。
 *
 * 参数签名与原编造版保持一致，避免牵连既有调用方。
 */
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

/**
 * Bento 网格卡片容器。改为直接使用 GZ 的 [glassCard] 真值 token，
 * 按压反馈也换成 GZ 的动画参数（`LiquidGlassButtonKt.java:255` 的 0.96f
 * 与 `ct.java:8` 的 `tween(150ms, CubicBezierEasing(0.2, 0, 0, 1))`），
 * 不再是原先凭空的 `spring(0.75f, 400f)`。
 *
 * 参数签名与原编造版保持一致。
 */
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
        targetValue = if (isPressed && onClick != null) 0.96f else 1.0f, // LiquidGlassButtonKt.java:255
        animationSpec = tween(
            durationMillis = 150, // ct.java:8
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f), // bs4.java:7
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

/**
 * 维度胶囊筛选 Chip。GZ 没有对应组件，这里用真值 token 拼装：
 *  - 选中：底色 = [accentColor] 的 28%/16%（与原实现同结构），默认色改为 GZ 真值 [DefaultCoverTone]
 *  - 未选中：底色 = [glassBg]，描边 = [glassBorder]
 *  - 文字未选中色 = GZ 的 [textLow]
 *
 * 参数签名与原编造版保持一致，仅把 `accentColor` 的默认值从编造的
 * `0xFF3B82F6`（Tailwind blue-500）换成 GZ 真值 [DefaultCoverTone]。
 */
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
