package com.aesthetic.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.AccentRose
import com.aesthetic.music.ui.theme.AppRadius
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.TextMuted

/**
 * 实心主按钮：主色底 + 白字，可选前置图标与危险色。
 *
 * 之前插件管理页的 `PrimaryWideButton` / `DangerWideButton` 与歌单编辑页的 `PillButton`
 * 是同一形态的三份私有实现（同圆角、同底色、同字号），这里收敛成一处。
 * 只负责这一种视觉；FM 的圆形实心按钮、播放器圆形按钮等差异较大的控件仍保留各自实现。
 */
@Composable
fun AppSolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = AppSpacing.xl,
        vertical = 11.dp,
    ),
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val background = when {
        !enabled -> Stone200
        destructive -> AccentRose
        else -> PrimaryBrown
    }
    val contentColor = if (enabled) Color.White else TextMuted

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.md))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(AppSpacing.xxs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = fontSize,
                fontWeight = fontWeight,
            ),
            color = contentColor,
        )
    }
}
