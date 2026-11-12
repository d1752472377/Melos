package com.aesthetic.music.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.AppColors
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/** 提示类型：只决定是否带图标，普通提示不带图标 */
private enum class ToastKind { Plain, Success, Error }

private val SUCCESS_HINTS = listOf(
    "成功", "已完成", "已保存", "已加入", "已添加", "已复制", "已更新",
    "已删除", "已同步", "已下载", "已在本地", "已提交", "已切换", "已恢复",
)

private val ERROR_HINTS = listOf(
    "失败", "错误", "异常", "无法", "不可用", "超时", "未成功", "未能",
)

private fun toastKindOf(text: String): ToastKind = when {
    ERROR_HINTS.any { text.contains(it) } -> ToastKind.Error
    SUCCESS_HINTS.any { text.contains(it) } -> ToastKind.Success
    else -> ToastKind.Plain
}

/**
 * 全应用统一的轻量提示：固定在状态栏下方，居中、胶囊形、900ms。
 *
 * 所有提示都走这里（ViewModels 的 showToast / UserNotice.post），
 * 不再有页面自己画在底部的提示。
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
            delay(900)
            currentMsg = null
            onDismiss()
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 状态栏下方 12dp，全应用同一位置
            .padding(top = topInset + 12.dp)
            .padding(horizontal = AppSpacing.page),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = currentMsg != null,
            enter = fadeIn(animationSpec = tween(160)) +
                slideInVertically(
                    initialOffsetY = { -it / 5 },
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                ),
            exit = fadeOut(animationSpec = tween(150)) +
                slideOutVertically(
                    targetOffsetY = { -it / 5 },
                    animationSpec = tween(160),
                ),
        ) {
            val text = currentMsg.orEmpty()
            val kind = toastKindOf(text)

            Row(
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(max = 340.dp)
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(22.dp),
                        spotColor = AppColors.toastShadow.copy(alpha = 0.35f),
                        ambientColor = AppColors.toastShadow.copy(alpha = 0.2f),
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (Palette.isDark) Surface else Color.White)
                    .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                    .clickable {
                        currentMsg = null
                        onDismiss()
                    }
                    .padding(horizontal = AppSpacing.xxl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (kind) {
                    ToastKind.Success -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.toastSuccess,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(AppSpacing.sm))
                    }

                    ToastKind.Error -> {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            tint = AppColors.toastError,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(AppSpacing.sm))
                    }

                    ToastKind.Plain -> Unit
                }

                Text(
                    text = text,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
