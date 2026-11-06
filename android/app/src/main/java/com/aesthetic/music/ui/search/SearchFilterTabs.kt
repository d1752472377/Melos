package com.aesthetic.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

/** 类型筛选行：单曲 / 歌手 / 歌单 / 专辑 / 歌词 */
@Composable
fun SearchFilterTabs(
    selected: SearchType,
    onSelect: (SearchType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SearchType.entries.forEach { type ->
            TypeTag(
                label = type.label,
                selected = selected == type,
                onClick = { onSelect(type) },
            )
        }
    }
}

/**
 * 类型筛选 tag（纯文字 + 底部短横线）：
 * - 选中态：主色加粗文字 + 下方主色短横线；
 * - 未选中：灰色普通文字，无横线，但保留同高透明占位，避免选中切换时整行跳动。
 */
@Composable
private fun TypeTag(label: String, selected: Boolean, onClick: () -> Unit) {
    val isDark = Palette.isDark
    val activeColor = if (isDark) AppColors.sky else PrimaryBrown
    val inactiveColor = textLow()

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = AppSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
            color = if (selected) activeColor else inactiveColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(AppSpacing.xxs))
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.5.dp)
                .background(
                    color = if (selected) activeColor else Color.Transparent,
                    shape = RoundedCornerShape(1.5.dp),
                )
        )
    }
}