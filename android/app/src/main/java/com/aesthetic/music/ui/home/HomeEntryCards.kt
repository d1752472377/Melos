package com.aesthetic.music.ui.home

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.components.DefaultCoverTone
import com.aesthetic.music.ui.components.discoveryGradientCard
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.theme.appIsDark
import kotlin.math.min
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

@Composable
fun HomeEntryCards(
    onOpenRecommend: () -> Unit,
    modifier: Modifier = Modifier,
    coverTone: Color = DefaultCoverTone,
    coverToneAlpha: Float = 1f,
    isCompact: Boolean = true,
    horizontalPadding: Dp = 16.dp,
) {
    // 首页只保留「猜你喜欢」一张卡片，作为进入推荐页的入口
    HomeDiscoveryEntryCard(
        item = HomeEntryItem(
            label = "猜你喜欢",
            status = "每日精选",
            artwork = 0,
            live = false,
            onClick = onOpenRecommend,
        ),
        isCompact = isCompact,
        coverTone = coverTone,
        coverToneAlpha = coverToneAlpha,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .height(92.dp),
    )
}

private data class HomeEntryItem(
    val label: String,
    val status: String,
    val artwork: Int,
    val live: Boolean,
    val onClick: () -> Unit,
)

private val EntryGradientArtwork0 = listOf(
    AppColors.entryPink1,
    AppColors.entryPink2,
    AppColors.entryPink3,
)

private val EntryGradientArtwork1 = listOf(
    AppColors.entryBlue1,
    AppColors.entryBlue2,
    AppColors.entryGreen1,
)

private val EntryGradientArtwork2 = listOf(
    AppColors.entryCyan,
    AppColors.entryMint,
    AppColors.entryLime,
)

private fun entryHaloColor(artwork: Int): Color = when (artwork) {
    0 -> AppColors.entryPink3
    1 -> AppColors.entryCyan2
    else -> AppColors.entryLime2
}

private fun entryGradientColors(artwork: Int, tone: Color, toneAlpha: Float): List<Color> {
    val base = when (artwork) {
        0 -> EntryGradientArtwork0
        1 -> EntryGradientArtwork1
        else -> EntryGradientArtwork2
    }
    if (artwork < 2) return base
    val tint = lerp(tone, Color.White, 0.75f)
    return base.map { lerp(it, tint, toneAlpha * 0.1f) }
}

private val MotionStandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

@Composable
private fun HomeDiscoveryEntryCard(
    item: HomeEntryItem,
    isCompact: Boolean,
    coverTone: Color,
    coverToneAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val dark = appIsDark()

    val centerYFactor = if (isCompact) 0.75f else 0.73f

    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }

    val colors = entryGradientColors(item.artwork, coverTone, coverToneAlpha)
    val haloColor = entryHaloColor(item.artwork)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale: State<Float> = animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1.0f,
        animationSpec = tween(
            durationMillis = 150,
            easing = MotionStandardEasing,
        ),
        label = "pressScale",
    )

    Box(
        modifier = modifier
            .discoveryGradientCard(
                shape = RoundedCornerShape(20.dp),
                colors = colors,
                useSharedGradient = false,
            )
            .graphicsLayer {
                val s = scale.value
                scaleX = s
                scaleY = s
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = true,
                onClick = item.onClick,
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        if (!dark) {
            EntryHalo(
                color = haloColor,
                pulseEnabled = item.live && animatorsEnabled,
                centerYFactor = centerYFactor,
                modifier = Modifier.matchParentSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize(1f)
                .padding(AppSpacing.md),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = item.label,
                color = textHigh(),
                fontSize = 14.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = (-0.4).sp,
                lineHeight = 19.sp,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                maxLines = 1,
            )

            Spacer(Modifier.height(2.dp))

            val statusColor = if (dark) {
                textMedium()
            } else {
                textHigh().copy(alpha = 0.55f)
            }
            Text(
                text = item.status,
                color = statusColor,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                maxLines = 1,
            )

            Spacer(
                Modifier
                    .fillMaxWidth(1f)
                    .height(if (isCompact) 30.dp else 48.dp),
            )
        }

        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.TopStart,
        ) {
            // TODO(未确定): HomeScreenKt.java:7215 的 `r0` 在 JADX 输出里没有声明
            val icon: ImageVector = when (item.artwork) {
                0 -> Icons.Rounded.Favorite
                1 -> Icons.Rounded.BarChart
                else -> Icons.Rounded.PlayArrow
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(
                                x = (constraints.maxWidth * 0.66f - placeable.width / 2f).toInt(),
                                y = (constraints.maxHeight * centerYFactor - placeable.height / 2f).toInt(),
                            )
                        }
                    }
                    .requiredSize(if (dark) 26.dp else 22.dp)
                    .then(
                        if (dark) {
                            Modifier
                        } else {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawBehind {
                                    val brush = Brush.linearGradient(
                                        colorStops = arrayOf(
                                            0.00f to Color.White.copy(alpha = 0.75f),
                                            0.55f to Color.White.copy(alpha = 0.40f),
                                            1.00f to Color.White.copy(alpha = 0.08f),
                                        ),
                                        start = Offset.Zero,
                                        end = Offset(size.width, size.height),
                                    )
                                    drawRect(
                                        brush = brush,
                                        topLeft = Offset.Zero,
                                        alpha = 1f,
                                    )
                                }
                        },
                    ),
                tint = if (dark) textHigh().copy(alpha = 0.85f) else Color.White,
            )
        }
    }
}

@Composable
private fun EntryHalo(
    color: Color,
    pulseEnabled: Boolean,
    centerYFactor: Float,
    modifier: Modifier = Modifier,
) {
    val pulse: State<Float>? = if (pulseEnabled) {
        val transition = rememberInfiniteTransition(label = "entryHalo")
        transition.animateFloat(
            initialValue = 0.0f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 4000,
                    easing = MotionStandardEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "entryHaloPulse",
        )
    } else {
        null
    }

    Spacer(
        modifier = modifier.drawBehind {
            val p = pulse?.value ?: 0.5f

            val cx = size.width * 0.66f
            val cy = size.height * centerYFactor

            val base = (p * 0.02f + 0.99f) * min(size.width * 0.29f, size.height * 0.25f)
            val gradientRadius = base * 1.75f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.85f),
                        color.copy(alpha = 0.425f),
                        Color.Unspecified,
                    ),
                    center = Offset(cx, cy),
                    radius = gradientRadius,
                ),
                radius = gradientRadius,
                center = Offset(cx, cy),
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = 1.5f * base,
                center = Offset(cx, cy),
                style = Stroke(width = 0.8.dp.toPx()),
            )

            drawCircle(
                color = color.copy(alpha = 0.16f),
                radius = base,
                center = Offset(cx, cy),
            )

            drawRoundRect(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Unspecified,
                        Color.White.copy(alpha = 0.9f),
                        Color.Unspecified,
                    ),
                    center = Offset(cx, cy),
                ),
                topLeft = Offset(cx - base, cy - base),
                size = Size(base * 2f, base * 2f),
                cornerRadius = CornerRadius(
                    x = p * 3f + 10f,
                    y = 260f,
                ),
                style = Stroke(
                    width = base * 0.15f,
                    cap = StrokeCap.Round,
                ),
            )
        },
    )
}
