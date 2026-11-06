package com.aesthetic.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.AccentRose
import com.aesthetic.music.ui.theme.AppRadius
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextPrimary

/** 统一弹出菜单：白底、16dp 圆角、细描边、柔和阴影。 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(AppRadius.xl),
        containerColor = Surface,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, CardBorder),
        content = content,
    )
}

/** 菜单项：统一字号、内边距，可标记选中态与危险色。 */
@Composable
fun AppDropdownItem(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    danger: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val color = when {
        danger -> AccentRose
        selected -> PrimaryBrown
        else -> TextPrimary
    }
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = color,
            )
        },
        onClick = onClick,
        leadingIcon = leadingIcon,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** 菜单内分隔线 */
@Composable
fun AppDropdownDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        thickness = 0.6.dp,
        color = CardBorder,
    )
}
