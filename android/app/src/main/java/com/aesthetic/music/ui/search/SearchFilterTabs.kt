package com.aesthetic.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.TextMuted

/** 类型筛选行：单曲 / 歌手 / 歌单 / 专辑 / 歌词 */
@Composable
fun SearchFilterTabs(
    selected: MainViewModel.SearchType,
    onSelect: (MainViewModel.SearchType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MainViewModel.SearchType.entries.forEach { type ->
            TypeTag(
                label = type.label,
                selected = selected == type,
                onClick = { onSelect(type) },
            )
        }
    }
}

/**
 * 类型筛选 tag。
 *
 * 用户要求把「单曲/歌手/歌单/专辑/歌词」从实底按钮改成 tag：选中态用主色淡底 + 主色文字，
 * 未选中只留弱描边，视觉重量明显低于按钮，不再和结果行抢注意力。
 */
@Composable
private fun TypeTag(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PrimaryBrown.copy(alpha = 0.12f) else Color.Transparent)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, Stone200, RoundedCornerShape(8.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = if (selected) PrimaryBrown else TextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
