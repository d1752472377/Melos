package com.aesthetic.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private data class ParticleSpec(
    val angleRad: Double,
    val maxTravel: Float,
    val sizeDp: Float,
    val rotation: Float,
)

/**
 * 质感红心微动效按钮（移植自瓜子音乐 BurstHeart 实现）
 * 点赞时触发主心弹性缩放 (1.0 -> 1.35 -> 0.95 -> 1.0) 及 8 颗环形粒子散射淡出爆炸动效。
 */
@Composable
fun FavoriteBurstButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    activeColor: Color = Color(0xFFFF3366),
    inactiveColor: Color = Color.White.copy(alpha = 0.75f),
) {
    val heartScale = remember { Animatable(1.0f) }
    val burstProgress = remember { Animatable(0.0f) }
    var lastFavorite by remember { mutableStateOf(isFavorite) }

    val particles = remember {
        val count = 8
        List(count) { i ->
            val baseAngle = (i * 2 * Math.PI) / count
            val jitterAngle = baseAngle + ((i % 3 - 1) * 0.15)
            ParticleSpec(
                angleRad = jitterAngle,
                maxTravel = 32f + (i % 4) * 5f,
                sizeDp = 5f + (i % 3) * 2f,
                rotation = ((i * 45) % 90 - 45).toFloat()
            )
        }
    }

    LaunchedEffect(isFavorite) {
        if (isFavorite && !lastFavorite) {
            // 触发点赞爆炸微动效
            burstProgress.snapTo(0f)
            heartScale.snapTo(1f)

            // 心脏弹性回弹
            heartScale.animateTo(
                targetValue = 1.35f,
                animationSpec = tween(120, easing = FastOutSlowInEasing)
            )
            heartScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
            )
            // 粒子：0 → 1 跑一遍（旧实现把这段放在另一个 LaunchedEffect 里，
            // 且从不归零，于是取消收藏再收藏时粒子已经停在 1.0f，第二次点击"没有动效"）
            burstProgress.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(450, easing = FastOutSlowInEasing)
            )
        } else if (!isFavorite) {
            // 取消收藏：把粒子归零，下次点赞才能再炸一次
            burstProgress.snapTo(0f)
        }
        lastFavorite = isFavorite
    }

    Box(
        modifier = modifier
            .size(iconSize * 1.8f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        // 环形散开的 8 颗小粒子
        if (burstProgress.value > 0f && burstProgress.value < 1.0f) {
            val progress = burstProgress.value
            val alpha = (1.0f - progress).coerceIn(0f, 1f)

            particles.forEach { p ->
                val dist = p.maxTravel * progress
                val offsetX = (dist * cos(p.angleRad)).dp
                val offsetY = (dist * sin(p.angleRad)).dp
                val pScale = (1.0f - progress * 0.4f).coerceAtLeast(0.3f)

                Box(
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .size(p.sizeDp.dp)
                        .graphicsLayer {
                            scaleX = pScale
                            scaleY = pScale
                            rotationZ = p.rotation * progress
                            this.alpha = alpha
                        }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = activeColor.copy(alpha = alpha),
                        modifier = Modifier.size(p.sizeDp.dp)
                    )
                }
            }
        }

        // 主心图标
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isFavorite) "已收藏" else "未收藏",
            tint = if (isFavorite) activeColor else inactiveColor,
            modifier = Modifier
                .size(iconSize)
                .scale(heartScale.value)
        )
    }
}
