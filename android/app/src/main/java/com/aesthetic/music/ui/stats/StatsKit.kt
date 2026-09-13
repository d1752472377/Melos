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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.settings.DayPoint
import com.aesthetic.music.data.settings.ListeningInsights
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

/**
 * 听歌统计页的视觉组件。
 *
 * 设计基线（与其它页面刻意区分）：统计页是"数据展示"场景，采用毛玻璃卡片 + 柔和渐变背景，
 * 与设置页/列表页的"纯白卡片 + 精致描边"不是同一套语言，因此组件单独收在这里，
 * 不去改动全局组件库（避免把玻璃质感扩散到列表页）。
 */

/** 页面背景：顶部淡蓝紫 → 下方米白/浅灰（深色下为墨蓝 → 近黑） */
@Composable
fun StatsGradientBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to StatsGradientTop,
                    0.45f to StatsGradientMid,
                    1f to StatsGradientBottom,
                )
            )
    ) {
        content()
    }
}

/** 毛玻璃卡片：半透明底 + 淡边框 + 柔和阴影 */
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
            .clip(shape)
            .background(GlassSurface)
            .border(1.dp, Color.White, shape)
            .padding(padding.dp),
        content = content,
    )
}

/** 小标签胶囊（"记录中""本月""活跃 3 天"） */
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = contentColor,
        )
    }
}

/** 分区标题（可选右侧附加信息） */
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

/**
 * 超大号时长：数字大、单位小。
 *
 * "1小时8分" 这类中文时长整体放大后会又长又挤，拆成 [("1","小时"),("8","分")] 后
 * 数字视觉重量突出、单位退居其次，正是参考设计里"核心数据极具冲击力"的做法。
 */
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
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** 键值小统计（累计卡片下排：播放次数 / 跨越天数） */
@Composable
fun StatsMiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = TextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextSoft,
        )
    }
}

/**
 * 30 天趋势柱状图。
 *
 * - 柱子高度按窗口内最大值归一（不按固定比例，否则数据少时全是矮柱看不清起伏）；
 * - 有数据但很少时保证最小 3dp 高度，避免"明明听了歌却看不到柱子"；
 * - 点柱子回调该天日期，由页面弹单日抽屉（宏观趋势 → 单日细节的下钻）。
 */
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
                                    // 今日用暖色，一眼就能在 30 根柱子里定位"今天"
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
        Spacer(Modifier.height(8.dp))
        // 坐标轴：只标首、末与两三个中间点，30 个日期全标会糊成一片
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

/** 坐标轴刻度下标：首、末 + 均匀分布的若干点 */
private fun axisTicks(points: List<DayPoint>): Set<Int> {
    if (points.size <= 7) return points.indices.toSet()
    val step = (points.size - 1) / 3
    return setOf(0, step, step * 2, points.lastIndex)
}

/**
 * 横向占比进度条（月度对比、时段分布共用）。
 *
 * @param gradient 进度条渐变：月度对比用青蓝、时段分布用蓝紫（视觉上区分两类数据）
 */
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
                Spacer(Modifier.width(8.dp))
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

/** 排行行：名次（金银铜）+ 歌名歌手 + 次数/时长 */
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
        Spacer(Modifier.width(12.dp))
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
        Spacer(Modifier.width(10.dp))
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

/** 前三名金银铜，其余用中性色 */
fun rankColor(rank: Int): Color = when (rank) {
    1 -> RankGold
    2 -> RankSilver
    3 -> RankBronze
    else -> TextMuted
}

/** 设置开关行（统计设置） */
@Composable
fun StatsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(activeColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = activeColor, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = TextSoft,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = activeColor,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Stone100.copy(alpha = 0.9f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** 空数据占位（统计页首次打开、或用户关掉了记录） */
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
