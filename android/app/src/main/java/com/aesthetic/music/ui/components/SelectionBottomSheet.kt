package com.aesthetic.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AppRadius
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

data class SelectionItem<T>(
    val id: T,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
)

@Composable
fun <T> SelectionBottomSheet(
    headerIcon: ImageVector,
    title: String,
    subtitle: String,
    items: List<SelectionItem<T>>,
    selectedId: T,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppBottomSheet(
        onDismissRequest = onDismissRequest,
        showDragHandle = false,
        containerColor = Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.page, vertical = AppSpacing.xxxl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(AppRadius.md))
                        .background(SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(AppSpacing.xl))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSoft
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismissRequest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                color = Stone200.copy(alpha = 0.5f),
                thickness = 0.8.dp,
                modifier = Modifier.padding(horizontal = AppSpacing.page)
            )

            Spacer(Modifier.height(AppSpacing.sm))

            items.forEach { item ->
                val isSelected = item.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(item.id)
                            onDismissRequest()
                        }
                        .padding(horizontal = AppSpacing.page, vertical = AppSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.icon != null) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isSelected) TextPrimary else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(AppSpacing.xxl))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = TextPrimary
                        )
                        if (!item.subtitle.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = TextSoft
                            )
                        }
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "已选择",
                            tint = AccentCoral,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
