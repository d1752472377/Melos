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

private fun Color.isDarkContainer(): Boolean {
    fun linear(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    val luminance = 0.2126f * linear(red) + 0.7152f * linear(green) + 0.0722f * linear(blue)
    return luminance < 0.5f
}

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
