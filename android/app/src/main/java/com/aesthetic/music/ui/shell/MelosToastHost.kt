package com.aesthetic.music.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 顶部悬浮灵动胶囊通知（Top Floating Capsule Toast）。
 *
 * 替代原生底部粗重、容易被底部播放胶囊与导航 Dock 遮挡的 Snackbar：
 * 1. 位于顶部状态栏下方（自动避让 statusBars）；
 * 2. 具有柔和毛玻璃与轻微阴影的圆角胶囊；
 * 3. 智能识别提示类型并展示对应精致图标（成功/错误/信息/播放）；
 * 4. 2.6 秒自动平滑淡出，支持上滑/点击立即关闭。
 */
@Composable
fun MelosToastHost(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            currentMsg = message
            delay(2600)
            currentMsg = null
            onDismiss()
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topInset + 10.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = currentMsg != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            val text = currentMsg.orEmpty()
            val isError = text.contains("失败") || text.contains("错误") || text.contains("异常")
            val isSuccess = text.contains("成功") || text.contains("已完成") || text.contains("已加入") || text.contains("已保存") || text.contains("已添加")

            val icon = when {
                isError -> Icons.Filled.Error
                isSuccess -> Icons.Filled.CheckCircle
                text.contains("播放") || text.contains("歌") -> Icons.Filled.MusicNote
                else -> Icons.Filled.Info
            }

            val iconTint = when {
                isError -> Color(0xFFF87171)
                isSuccess -> Color(0xFF4ADE80)
                else -> Color(0xFFFDE68A)
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xE6241E1C), Color(0xE638302C))
                        )
                    )
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(9999.dp))
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(9999.dp), spotColor = Color(0x66000000))
                    .clickable {
                        currentMsg = null
                        onDismiss()
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -10) {
                                currentMsg = null
                                onDismiss()
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .widthIn(min = 120.dp, max = 360.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color(0xFFF7F6F2),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
