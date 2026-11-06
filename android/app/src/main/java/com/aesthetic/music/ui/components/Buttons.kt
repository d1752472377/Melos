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
