package com.aesthetic.music.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.settings.DayPoint
import com.aesthetic.music.data.settings.ListeningInsights
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.AccentTeal
import com.aesthetic.music.ui.theme.GlassBorder
import com.aesthetic.music.ui.theme.GlassSurface
import com.aesthetic.music.ui.theme.RankBronze
import com.aesthetic.music.ui.theme.RankGold
import com.aesthetic.music.ui.theme.RankSilver
import com.aesthetic.music.ui.theme.StatsGradientBottom
import com.aesthetic.music.ui.theme.StatsGradientMid
import com.aesthetic.music.ui.theme.StatsGradientTop
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing

@Composable
fun StatsGradientBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        content()
    }
}

@Composable
fun StatsGlassCard(
    modifier: Modifier = Modifier,
    padding: Int = 18,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .iosGlassCard(shape = shape, elevation = 4.dp)
            .padding(padding.dp),
        content = content,
    )
}

@Composable
fun StatsPill(
    text: String,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = contentColor,
        )
    }
}

@Composable
fun StatsSectionTitle(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = TextPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun StatsBigDuration(
    ms: Long,
    numberColor: Color = TextPrimary,
    unitColor: Color = TextSoft,
    numberSize: Int = 40,
    unitSize: Int = 14,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        ListeningInsights.durationParts(ms).forEach { (number, unit) ->
            Text(
                text = number,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = numberSize.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = numberColor,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = unitSize.sp),
                color = unitColor,
                modifier = Modifier.padding(bottom = AppSpacing.xs),
            )
            Spacer(Modifier.width(AppSpacing.sm))
        }
    }
}

@Composable
fun StatsBarChart(
    points: List<DayPoint>,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Int = 104,
) {
    val maxMs = points.maxOfOrNull { it.durationMs } ?: 0L
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
                val fraction = if (maxMs <= 0) 0f else (point.durationMs.toFloat() / maxMs.toFloat())
                val barHeight = when {
                    fraction <= 0f -> 3f
                    else -> (fraction * chartHeight).coerceAtLeast(6f)
                }
                val isToday = point.isToday
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onDayClick(point.day) },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    fraction <= 0f -> Brush.verticalGradient(listOf(Stone100, Stone100))
                                    isToday -> Brush.verticalGradient(
                                        listOf(AccentAmber, AccentAmber.copy(alpha = 0.55f))
                                    )
                                    else -> Brush.verticalGradient(listOf(AccentTeal, AccentCyan))
                                }
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Row(modifier = Modifier.fillMaxWidth()) {
            val ticks = axisTicks(points)
            points.forEachIndexed { index, point ->
                Box(modifier = Modifier.weight(1f)) {
                    if (index in ticks) {
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                            color = if (point.isToday) AccentAmber else TextMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun axisTicks(points: List<DayPoint>): Set<Int> {
    if (points.size <= 7) return points.indices.toSet()
    val step = (points.size - 1) / 3
    return setOf(0, step, step * 2, points.lastIndex)
}

@Composable
fun StatsProgressRow(
    label: String,
    valueText: String,
    fraction: Float,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    labelColor: Color = TextPrimary,
    barHeight: Int = 8,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = labelColor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            if (!trailingText.isNullOrBlank()) {
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight.dp)
                .clip(RoundedCornerShape(barHeight.dp / 2))
                .background(Stone100)
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(barHeight.dp / 2))
                        .background(Brush.horizontalGradient(gradient))
                )
            }
        }
    }
}

@Composable
fun StatsRankRow(
    rank: Int,
    title: String,
    artist: String,
    trailingTop: String,
    trailingBottom: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(rankColor(rank).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = rankColor(rank),
            )
        }
        Spacer(Modifier.width(AppSpacing.lg))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(AppSpacing.md))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = trailingTop,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = rankColor(rank),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = trailingBottom,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextSoft,
            )
        }
    }
}

fun rankColor(rank: Int): Color = when (rank) {
    1 -> RankGold
    2 -> RankSilver
    3 -> RankBronze
    else -> TextMuted
}

@Composable
fun StatsEmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = TextSoft,
        )
    }
}
