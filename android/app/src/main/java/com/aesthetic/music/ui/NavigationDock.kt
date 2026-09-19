package com.aesthetic.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aesthetic.music.player.PlaybackProgress
import com.aesthetic.music.ui.components.RotatingDisc
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextSoft
import kotlinx.coroutines.flow.StateFlow

/**
 * 底部导航 Dock：主页 / FM（全屏刷歌） / 中央旋转唱片（带进度环） / 我的歌单 / 个人
 *
 * @param containerColor 容器背景色，根据页面背景动态传入
 * @param isDarkBackground 是否为深色背景，决定文字和图标的颜色
 */
@Composable
fun NavigationDock(
    currentTab: AppTab,
    currentArtwork: String?,
    onHome: () -> Unit,
    onSheet: () -> Unit = {},
    onPlaylist: () -> Unit = {},
    onProfile: () -> Unit,
    onOpenPlayer: () -> Unit,
    isSpinning: Boolean = false,
    progressFlow: StateFlow<PlaybackProgress>? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,  // 这行的 Color 是 android.graphics.Color
    isDarkBackground: Boolean = false,
) {
    // 深色底（沉浸页或深色主题）走深色玻璃；浅色底才用白色玻璃
    val onDark = isDarkBackground || Palette.isDark
    val dockBackground = when {
        isDarkBackground -> containerColor.copy(alpha = 0.85f)
        Palette.isDark -> Surface.copy(alpha = 0.92f)
        else -> Color.White.copy(alpha = 0.97f)
    }

    val dockBorderColor = if (onDark) {
        // 深色背景下，边框使用白色半透明
        Color.White.copy(alpha = 0.15f)
    } else {
        // 浅色背景下，边框使用白色
        Color.White.copy(alpha = 0.85f)
    }

    val iconColor = if (isDarkBackground) {
        // 深色背景下，未选中的图标使用白色半透明
        Color.White.copy(alpha = 0.6f)
    } else {
        // 浅色背景下，未选中的图标使用灰色
        TextSoft
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 18.dp)
            .height(54.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(27.dp),
                ambientColor = if (onDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.4f),
                spotColor = if (onDark) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(27.dp))
            .background(dockBackground)
            .border(
                width = 1.5.dp,
                color = dockBorderColor,
                shape = RoundedCornerShape(27.dp)
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        DockIcon(
            icon = Icons.Filled.Home,
            label = "首页",
            selected = currentTab == AppTab.HOME,
            onClick = onHome,
            selectedColor = if (isDarkBackground) Color.White else PrimaryBrown,
            unselectedColor = iconColor
        )

        CenterDisc(
            currentArtwork = currentArtwork,
            isSpinning = isSpinning,
            progressFlow = progressFlow,
            onOpenPlayer = onOpenPlayer,
            isDarkBackground = isDarkBackground,
        )

        DockIcon(
            icon = Icons.Filled.Person,
            label = "我的",
            selected = currentTab == AppTab.PROFILE,
            onClick = onProfile,
            selectedColor = if (isDarkBackground) Color.White else PrimaryBrown,
            unselectedColor = iconColor
        )
    }
}

/**
 * 中央唱片与环形进度条组件：独立订阅 progressFlow，防止 500ms 进度跳动引起父组件整行重组
 */
@Composable
private fun CenterDisc(
    currentArtwork: String?,
    isSpinning: Boolean,
    progressFlow: StateFlow<PlaybackProgress>?,
    onOpenPlayer: () -> Unit,
    isDarkBackground: Boolean = false,
) {
    val progressState = progressFlow?.collectAsState()
    val progress = progressState?.value
    val fraction = if (progress != null && progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // 判断是否显示进度条：播放中或暂停时都显示
    val shouldShowProgress = progress != null && progress.durationMs > 0

    // 进度条颜色
    val progressColor = if (isDarkBackground) Color.White else PrimaryBrown
    // 底色：变淡、模糊、加灰度的效果
    val trackColor = if (isDarkBackground) {
        Color.White.copy(alpha = 0.15f)
    } else {
        PrimaryBrown.copy(alpha = 0.15f)
    }

    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { contentDescription = "打开播放器" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenPlayer
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 背景层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkBackground) Color.Black.copy(alpha = 0.3f) else Surface)
        )

        // Canvas 画环
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.5.dp.toPx()
            val halfStroke = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(halfStroke, halfStroke)

            // 只有在有播放进度信息时才绘制环形轨道
            if (shouldShowProgress) {
                // 静态底色轨道：变淡的棕色，作为进度条的背景
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )

                // 进度条：播放中或暂停时都显示
                if (fraction > 0f) {
                    drawArc(
                        color = progressColor,
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

        // 内层黑胶旋转唱片
        RotatingDisc(
            url = currentArtwork,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            isPlaying = isSpinning,
            durationMs = 5000
        )
    }
}

@Composable
private fun DockIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = PrimaryBrown,
    unselectedColor: Color = TextSoft,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) selectedColor else unselectedColor,
            modifier = Modifier.size(24.dp),
        )
    }
}