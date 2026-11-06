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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.aesthetic.music.ui.theme.AppColors
import com.aesthetic.music.ui.theme.AppRadius
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextPrimary

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
            .padding(top = topInset + AppSpacing.md)
            .padding(horizontal = AppSpacing.page),
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
                isError -> AppColors.toastError
                isSuccess -> AppColors.toastSuccess
                else -> PrimaryBrown
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppRadius.pill))
                    .background(if (Palette.isDark) Surface else Color.White)
                    .border(1.dp, CardBorder, RoundedCornerShape(AppRadius.pill))
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(AppRadius.pill),
                        spotColor = AppColors.toastShadow,
                    )
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
                    .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.sm)
                    .widthIn(min = 120.dp, max = 360.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(AppRadius.pill))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
