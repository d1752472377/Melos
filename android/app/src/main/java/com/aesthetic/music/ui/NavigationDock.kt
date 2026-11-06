package com.aesthetic.music.ui

import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aesthetic.music.player.PlaybackProgress
import com.aesthetic.music.ui.components.RotatingDisc
import com.aesthetic.music.ui.components.glassBg
import com.aesthetic.music.ui.components.rememberCoverTone
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.theme.Palette
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.min
import com.aesthetic.music.ui.theme.AppColors

private val BarHeight = 60.dp
private val BarHorizontalPadding = 24.dp
private val BarBottomPadding = 14.dp
private val BarElevation = 20.dp
private val BarInnerPadding = 6.dp
// TODO(未确定): 条体描边线宽 GZ 未抠出（spec:18 只给了颜色）→ 取项目玻璃卡通用 1.dp
private val BarBorderWidth = 1.dp
private val BarShape = RoundedCornerShape(BarHeight / 2)

private val BarBlurRadius = 24.dp
private const val BarNoiseFactor = 0.02f
private const val BarGlassAlphaDark = 0.72f
private const val BarGlassAlphaLight = 0.75f

private val TabIconSize = 23.dp
private const val TabPressedScale = 0.94f
private const val TabSelectedScale = 1.12f
private const val TabUnselectedScale = 1.0f

private val DiscContainerSize = 64.dp
private val DiscRingSize = 48.dp
private val DiscSize = 44.dp
private val DiscElevation = 10.dp
private val DiscCenterPlateSize = 10.dp
private val DiscSpindleSize = 3.dp
private const val DiscPressedScale = 0.92f
// TODO(未确定): 第二色描边的线宽 GZ 未抠出（spec:51 只给了颜色公式）→ 取 1.dp
private val DiscOutlineWidth = 1.dp
// TODO(未确定): 进度环线宽 GZ 未抠出 → 沿用本组件既有值
private val DiscRingStroke = 2.5.dp
private val DiscCenterPlateColor = AppColors.ink
private val DiscSpindleColor = Color.White.copy(alpha = 0.75f)

private val GzEmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val GzMotionBEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
private val GzMotionA: TweenSpec<Float> = tween(150, 2, GzEmphasizedEasing)
private val GzMotionB: TweenSpec<Float> = tween(250, 2, GzMotionBEasing)
// TODO(未确定): 该处 mask 无法确定 → delayMillis 取 0，只保留确定的 250ms + bs4.a
private val GzMotionTint: TweenSpec<Color> = tween(250, 0, GzEmphasizedEasing)

@Composable
fun NavigationDock(
    currentTab: AppTab,
    currentArtwork: String?,
    onHome: () -> Unit,
    onProfile: () -> Unit,
    onOpenPlayer: () -> Unit,
    isSpinning: Boolean = false,
    progressFlow: StateFlow<PlaybackProgress>? = null,
    modifier: Modifier = Modifier,
    isDarkBackground: Boolean = false,
    hazeState: HazeState? = null,
) {
    val onDark = isDarkBackground || Palette.isDark

    val barBackground = if (onDark) {
        AppColors.ink.copy(alpha = BarGlassAlphaDark)
    } else {
        Color.White.copy(alpha = BarGlassAlphaLight)
    }
    // TODO(未确定): 换成 GZ 的双色标白色线性渐变（色标 alpha 未能从 smali 确定）。
    val barBorder = Color.White.copy(alpha = if (onDark) 0.30f else 0.55f)
    val barAmbient = Color.Black.copy(alpha = if (onDark) 0.24f else 0.16f)
    val barSpot = Color.Black.copy(alpha = if (onDark) 0.36f else 0.26f)

    // TODO(未确定): GZ 条体外还有一层无参 WindowInsets padding（spec:14，成员名未抠出）；
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = BarBottomPadding)
            .padding(horizontal = BarHorizontalPadding)
            .height(BarHeight)
            .shadow(
                elevation = BarElevation,
                shape = BarShape,
                clip = false,
                ambientColor = barAmbient,
                spotColor = barSpot,
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(BarShape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeDefaults.style(
                                backgroundColor = barBackground,
                                blurRadius = BarBlurRadius,
                                noiseFactor = BarNoiseFactor,
                            ),
                        )
                    } else {
                        Modifier.background(barBackground, BarShape)
                    }
                )
                .border(BarBorderWidth, barBorder, BarShape),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = BarInnerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        DockIcon(
            modifier = Modifier.weight(1f, fill = true),
            icon = Icons.Filled.Home,
            label = "首页",
            selected = currentTab == AppTab.HOME,
            onDark = onDark,
            onClick = onHome,
        )

        CenterDisc(
            currentArtwork = currentArtwork,
            isSpinning = isSpinning,
            progressFlow = progressFlow,
            onOpenPlayer = onOpenPlayer,
            onDark = onDark,
        )

        DockIcon(
            modifier = Modifier.weight(1f, fill = true),
            icon = Icons.Filled.Person,
            label = "我的",
            selected = currentTab == AppTab.PROFILE,
            onDark = onDark,
            onClick = onProfile,
            )
        }
    }
}

@Composable
private fun CenterDisc(
    currentArtwork: String?,
    isSpinning: Boolean,
    progressFlow: StateFlow<PlaybackProgress>?,
    onOpenPlayer: () -> Unit,
    onDark: Boolean = false,
) {
    val progressState = progressFlow?.collectAsState()
    val progress = progressState?.value
    val fraction = if (progress != null && progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val shouldShowProgress = progress != null && progress.durationMs > 0

    val tone = rememberCoverTone(currentArtwork)
    val coverHsv = remember(tone) {
        FloatArray(3).also { AndroidColor.colorToHSV(tone.toArgb(), it) }
    }
    val discColor = remember(coverHsv) {
        Color.hsv(coverHsv[0], min(coverHsv[1] * 1.2f, 0.85f), max(coverHsv[2], 0.55f))
    }
    // TODO(未确定): spec 只写「第二色（描边/内层）」，GZ 里的确切用途未抠出 → 这里按唱片外圈描边落地
    val discEdgeColor = remember(coverHsv) {
        discColor.copy(alpha = (1f - (1f - coverHsv[2]) * coverHsv[1] * 0.7f).coerceIn(0f, 1f))
    }

    val ringTrack = if (onDark) Color.White.copy(alpha = 0.14f) else textLow().copy(alpha = 0.12f)
    val ringProgress = tone

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) DiscPressedScale else 1f,
        animationSpec = GzMotionA,
        label = "discPressScale",
    )

    Box(
        Modifier
            .requiredSize(DiscContainerSize)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .semantics { contentDescription = "打开播放器" }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpenPlayer,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(DiscRingSize)) {
            val strokeWidth = DiscRingStroke.toPx()
            val halfStroke = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(halfStroke, halfStroke)

            if (shouldShowProgress) {
                drawArc(
                    color = ringTrack,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
                if (fraction > 0f) {
                    drawArc(
                        color = ringProgress,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(DiscSize)
                .shadow(
                    elevation = DiscElevation,
                    shape = CircleShape,
                    clip = true,
                    ambientColor = discColor.copy(alpha = 0.30f),
                    spotColor = discColor.copy(alpha = 0.35f),
                )
                .clip(CircleShape)
                .background(discColor),
        ) {
            RotatingDisc(
                url = currentArtwork,
                modifier = Modifier.fillMaxSize(),
                isPlaying = isSpinning,
                durationMs = 5000,
            )

            // 第二色描边：必须叠在封面之上，否则被铺满 44.dp 的封面图盖住
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = DiscOutlineWidth.toPx()
                drawCircle(
                    color = discEdgeColor,
                    radius = (size.minDimension - w) / 2f,
                    style = Stroke(width = w),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(DiscCenterPlateSize)
                    .clip(CircleShape)
                    .background(DiscCenterPlateColor),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(DiscSpindleSize)
                    .clip(CircleShape)
                    .background(DiscSpindleColor),
            )
        }
    }
}

@Composable
private fun DockIcon(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onDark: Boolean,
    onClick: () -> Unit,
) {
    val targetTint = if (onDark) {
        Color.White.copy(alpha = if (selected) 0.85f else 0.55f)
    } else {
        if (selected) textHigh() else textLow()
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) TabPressedScale else 1f,
        animationSpec = GzMotionA,
        label = "pressScale",
    )
    val selectScale by animateFloatAsState(
        targetValue = if (selected) TabSelectedScale else TabUnselectedScale,
        animationSpec = GzMotionB,
        label = "pillTabScale",
    )
    val tint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = GzMotionTint,
        label = "pillTabTint",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(TabIconSize)
                .graphicsLayer {
                    scaleX = selectScale
                    scaleY = selectScale
                },
        )
    }
}
