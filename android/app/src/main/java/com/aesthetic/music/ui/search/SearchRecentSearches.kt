package com.aesthetic.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.components.DangerTextButton
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.AppSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchRecentSearches(
    words: List<String>,
    onPick: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (words.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "最近搜索",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            DangerTextButton(
                text = "清空",
                onClick = onClearAll,
            )
        }
        Spacer(Modifier.height(AppSpacing.md))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            words.forEach { word ->
                WordChip(word = word) { onPick(word) }
            }
        }
        Spacer(Modifier.height(AppSpacing.xxxl))
    }
}

@Composable
private fun WordChip(word: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .glassCard(shape = RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xs),
    ) {
        Text(
            word,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = textMedium(),
            maxLines = 1,
        )
    }
}