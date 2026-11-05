package com.aesthetic.music.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.AppRadius
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import kotlin.math.pow

/**
 * 全局弹窗 / 底部抽屉统一封装。
 *
 * 收敛前的问题：22 处 AlertDialog、6 处 ModalBottomSheet 各自手写容器色、圆角与按钮样式，导致
 * - 同为弹窗却并存三种外观：M3 默认容器、手搓 `Surface + 14.dp` 圆角、`Surface + 16.dp` 圆角；
 * - 「取消」按钮位置不统一：有的放 dismissButton，有的塞进 confirmButton；
 * - 确认按钮形态不统一：多数是 TextButton，个别用实底 Button；
 * - 抽屉中 2 处没有拖拽把手，4 处内容会被手势导航条遮挡（只有 1 处加了 navigationBarsPadding）。
 *
 * 这里把外观决策收进组件内部，调用方只描述语义：标题 / 正文 / 确认 / 取消 / 是否危险操作。
 * 容器为深色时（播放器页传入 PlayerBg）文字颜色自动切换为浅色，无需额外开关。
 */

/** 相对亮度判断：容器偏暗时正文需用浅色，避免"深底深字" */
private fun Color.isDarkContainer(): Boolean {
    fun linear(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    val luminance = 0.2126f * linear(red) + 0.7152f * linear(green) + 0.0722f * linear(blue)
    return luminance < 0.5f
}

/**
 * 统一样式的弹窗。
 *
 * @param confirmText null 表示不渲染确认按钮（纯列表选择类弹窗，只有取消）
 * @param cancelText null 表示不渲染取消按钮
 * @param onCancel 取消回调；为 null 时默认走 onDismissRequest（等价于关闭）
 * @param destructive 危险操作（删除 / 清空 / 退出登录），确认按钮改用 error 色
 * @param actions 逃生口：完全接管按钮行，仅用于需要"测试连接 + 保存"这类双主操作的弹窗
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    containerColor: Color = Surface,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    cancelText: String? = "取消",
    onCancel: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val dark = containerColor.isDarkContainer()
    val titleColor = if (dark) Color.White else TextPrimary
    val bodyColor = if (dark) Color.White.copy(alpha = 0.72f) else TextMuted
    val cancelColor = if (dark) Color.White.copy(alpha = 0.65f) else TextMuted
    val confirmColor = when {
        !confirmEnabled -> cancelColor
        destructive -> MaterialTheme.colorScheme.error
        else -> PrimaryBrown
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = containerColor,
        shape = RoundedCornerShape(AppRadius.xl),
        title = title?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = titleColor,
                )
            }
        },
        // 未指定颜色的正文 Text 会继承这里的 bodyColor，避免各调用点自己写 color
        text = {
            CompositionLocalProvider(LocalContentColor provides bodyColor) {
                Column { content() }
            }
        },
        confirmButton = {
            if (actions != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            } else if (confirmText != null) {
                TextButton(
                    onClick = { onConfirm?.invoke() ?: onDismissRequest() },
                    enabled = confirmEnabled,
                ) {
                    Text(
                        text = confirmText,
                        color = confirmColor,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        },
        dismissButton = if (actions == null && cancelText != null) {
            {
                TextButton(onClick = { onCancel?.invoke() ?: onDismissRequest() }) {
                    Text(
                        text = cancelText,
                        color = cancelColor,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        } else null,
    )
}

/**
 * 「标题 + 一句话说明 + 取消 + 确认」的确认弹窗，覆盖删除/清空/退出这类最常用场景。
 * 单独抽出是为了让这些高风险操作不会因为调用点不同而长出不同外观。
 */
@Composable
fun AppConfirmDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    destructive: Boolean = true,
) {
    AppDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        confirmText = confirmText,
        onConfirm = onConfirm,
        destructive = destructive,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 统一样式的底部抽屉。
 *
 * 与直接调用 ModalBottomSheet 的区别：统一圆角与容器色、默认带拖拽把手、
 * 自动为内容追加 navigationBarsPadding，避免最后一项被手势导航条遮挡。
 * 内容边距仍由各调用点自行控制（抽屉之间形态差异较大，强行统一会挤压列表类布局）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Surface,
    showDragHandle: Boolean = true,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val handleColor = if (containerColor.isDarkContainer()) {
        Color.White.copy(alpha = 0.35f)
    } else {
        TextSoft
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        shape = RoundedCornerShape(topStart = AppRadius.card, topEnd = AppRadius.card),
        dragHandle = if (showDragHandle) {
            { BottomSheetDefaults.DragHandle(color = handleColor) }
        } else {
            null
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            content = content,
        )
    }
}
