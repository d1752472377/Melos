package com.aesthetic.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 加载态（居中 spinner + 文案）。
 * 全 App 统一：替代此前"搜索页 spinner / 发现页纯文本 / 歌手页无 spinner / 首页无加载态"的多种表现。
 */
@Composable
fun LoadingBox(
    message: String = "加载中…",
    modifier: Modifier = Modifier,
    withSpinner: Boolean = true,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (withSpinner) {
                CircularProgressIndicator(
                    color = PrimaryBrown,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                text = message,
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * 错误态（居中提示 + 可选重试按钮）。
 * 此前歌单/榜单/专辑详情失败只渲染空白列表、导入歌单失败静默无反馈，统一走这里。
 */
@Composable
fun ErrorBox(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(
                text = message,
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "重试",
                    color = PrimaryBrown,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * 空态（居中一行提示文案）。
 */
@Composable
fun EmptyBox(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 危险操作文字按钮（清空历史 / 清空缓存等统一样式）。
 * 此前"清空"在各页有纯文本与描边胶囊两种形态、红色也是三种硬编码值，统一收敛到这里，
 * 颜色引用 MaterialTheme.colorScheme.error（深/浅两套主题各自适配）。
 */
@Composable
fun DangerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val error = MaterialTheme.colorScheme.error
    Text(
        text = text,
        color = if (enabled) error else TextSoft.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/**
 * 危险操作描边胶囊（"清空"类按钮统一样式）。
 */
@Composable
fun DangerCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    val error = MaterialTheme.colorScheme.error
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .border(1.dp, error.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            color = error,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
