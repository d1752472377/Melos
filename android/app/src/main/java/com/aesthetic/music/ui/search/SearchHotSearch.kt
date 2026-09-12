package com.aesthetic.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary

/** 热搜词条：可选热度值与副标题 */
data class HotSearchWord(
    val keyword: String,
    val hotValue: String? = null,   // 如 "8.5万"
    val subtitle: String? = null,   // 如 "盘旋在我脑海里面"
)

/** 前三名序号用的红色 */
private val HotRankRed = Color(0xFFE53935)

/** 热门搜索：卡片式带序号的排序列表 */
@Composable
fun SearchHotSearch(
    words: List<HotSearchWord>,
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

/** 热门搜索卡片：带序号列表，前三名序号红色加粗，可带热度值与副标题 */
@Composable
private fun HotSearchCard(words: List<HotSearchWord>, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface),
    ) {
        words.forEachIndexed { idx, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPick(item.keyword) },
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 序号：前三名红色加粗
                Text(
                    text = "${idx + 1}",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = if (idx < 3) HotRankRed else TextMuted,
                    fontWeight = if (idx < 3) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.width(26.dp),
                )

                // 关键词 + 可选副标题
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.keyword,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.subtitle?.let { sub ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 可选热度值
                item.hotValue?.let { hot ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = hot,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = TextMuted,
                    )
                }
            }

            if (idx != words.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 14.dp),
                    thickness = 0.5.dp,
                    color = Stone200.copy(alpha = 0.5f),
                )
            }
        }
    }
}