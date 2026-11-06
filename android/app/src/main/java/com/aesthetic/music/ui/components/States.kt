package com.aesthetic.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aesthetic.music.ui.theme.AppRadius
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextSoft

val BottomContentSpacer: Dp = 88.dp

val BottomContentPadding: PaddingValues = PaddingValues(bottom = BottomContentSpacer)

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
                Spacer(Modifier.height(AppSpacing.md))
            }
            Text(
                text = message,
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun ErrorBox(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = AppSpacing.section),
        ) {
            Text(
                text = message,
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(AppSpacing.lg))
                Text(
                    text = "重试",
                    color = PrimaryBrown,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.xs))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = AppSpacing.xxxl, vertical = AppSpacing.sm),
                )
            }
        }
    }
}

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
            .clip(RoundedCornerShape(AppRadius.xs))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AppSpacing.xl, vertical = 7.dp),
    )
}

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
            .clip(RoundedCornerShape(AppRadius.sm))
            .clickable(onClick = onClick)
            .border(1.dp, error.copy(alpha = 0.6f))
            .padding(horizontal = AppSpacing.lg, vertical = 7.dp),
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
