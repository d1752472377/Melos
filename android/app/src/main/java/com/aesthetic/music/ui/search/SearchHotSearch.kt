package com.aesthetic.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary

/** 热门搜索：卡片式带序号的排序列表 */
@Composable
fun SearchHotSearch(
    words: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (words.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "热门搜索",
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
            ),
            color = TextPrimary,
        )
        Spacer(Modifier.height(10.dp))
        HotSearchCard(words = words, onPick = onPick)
    }
}

/** 热门搜索卡片：展示为带序号的排序列表，前三名主色加粗 */
@Composable
private fun HotSearchCard(words: List<String>, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Surface),
    ) {
        words.forEachIndexed { idx, word ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(word) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${idx + 1}",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = if (idx < 3) PrimaryBrown else TextMuted,
                    fontWeight = if (idx < 3) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.width(26.dp),
                )
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (idx != words.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 14.dp),
                    thickness = 0.5.dp,
                    color = Stone200.copy(alpha = 0.6f),
                )
            }
        }
    }
}
