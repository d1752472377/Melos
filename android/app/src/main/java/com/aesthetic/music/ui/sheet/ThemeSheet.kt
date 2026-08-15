package com.aesthetic.music.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aesthetic.music.data.settings.PlayerTheme
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 播放页主题选择 Sheet（真实生效）：
 * 经典 / 封面卡片 / Apple Music 三套背景，点击即应用到全屏播放器并持久化。
 */
@Composable
fun ThemeSheet(
    current: PlayerTheme,
    onSelect: (PlayerTheme) -> Unit,
    onClose: () -> Unit,
) {
    val options: List<Pair<PlayerTheme, Brush>> = listOf(
        PlayerTheme.CLASSIC to Brush.verticalGradient(listOf(Color(0xFF534332), Color(0xFF3A2E24))),
        PlayerTheme.COVER to Brush.verticalGradient(listOf(Color(0xFF61271D), Color(0xFF3A1E17))),
        PlayerTheme.APPLE to Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF6B21A8), Color(0xFF9D174D))),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = TextSoft, modifier = Modifier.size(20.dp))
            Text(
                "播放页主题",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Icon(
                Icons.Filled.Close, contentDescription = "关闭", tint = TextSoft,
                modifier = Modifier.size(20.dp).clickable(onClick = onClose),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "选择播放详情页的媒体布局，选择后立即生效。",
            style = MaterialTheme.typography.bodySmall,
            color = TextSoft,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { (theme, brush) ->
                ThemeOptionItem(
                    name = theme.label,
                    selected = theme == current,
                    background = brush,
                    onClick = { onSelect(theme) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionItem(
    name: String,
    selected: Boolean,
    background: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(0.dp))
                .background(background)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, PrimaryBrown, RoundedCornerShape(0.dp))
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick)
                .padding(10.dp),
        ) {
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(PrimaryBrown),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            // 预览占位元素
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.12f)))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.6f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.4f)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) PrimaryBrown else Stone100)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color.White else TextMuted,
            )
        }
    }
}
