package com.aesthetic.music.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.components.textHigh
import java.util.Calendar
import com.aesthetic.music.ui.theme.AppSpacing

data class HomeTimeScene(
    val key: String,
    val category: String,
    val title: String,
)

fun resolveTimeScene(hour: Int): HomeTimeScene = when {
    hour in 6..10 -> HomeTimeScene(key = "morning", category = "清晨", title = "清晨时光")
    hour in 11..13 -> HomeTimeScene(key = "noon", category = "午休", title = "午休小憩")
    hour in 18..22 -> HomeTimeScene(key = "evening", category = "散步", title = "晚风散步")
    hour < 6 || hour >= 23 -> HomeTimeScene(key = "night", category = "夜晚", title = "夜晚好梦")
    else -> HomeTimeScene(key = "afternoon", category = "下午茶", title = "下午茶歇")
}

fun resolveTimeSlotLabel(hour: Int): String = when {
    hour in 5..8 -> "清晨"
    hour in 9..11 -> "上午"
    hour in 12..13 -> "午间"
    hour in 18..22 -> "傍晚"
    hour < 5 || hour >= 23 -> "深夜"
    else -> "午后"
}

fun currentHourOfDay(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

@Composable
fun HomeHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth(1.0f)
            .padding(start = AppSpacing.xxl, top = AppSpacing.lg, end = AppSpacing.xxl, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.weight(1.0f, fill = true),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "发现好音乐",
                color = textHigh(),
                fontSize = 28.sp,
                fontWeight = FontWeight(800),
                letterSpacing = (-0.8).sp,
            )
        }
    }
}

// TODO(未确定): 任务书要求还原的「时段问候语」在 GZ 中不存在。
