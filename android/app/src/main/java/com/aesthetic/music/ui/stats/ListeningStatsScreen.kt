package com.aesthetic.music.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.settings.DayStat
import com.aesthetic.music.data.settings.ListeningInsights
import com.aesthetic.music.data.settings.ListeningStats
import com.aesthetic.music.data.settings.RangeSummary
import com.aesthetic.music.data.settings.SongStat
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.AccentTeal
import com.aesthetic.music.ui.theme.AccentViolet
import com.aesthetic.music.ui.theme.GlassBorder
import com.aesthetic.music.ui.theme.GlassSurfaceSoft
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing

/**
 * 听歌统计页（重写版）。
 *
 * 页面结构：累计概览（含今日/本周/本月）→ 30 天趋势（可点柱子下钻单日）
 * → 近三月对比 → 歌曲排行 → 时段分布。
 *
 * 所有数字都来自 [StatsStore] 的真实记录。
 */
@Composable
fun ListeningStatsScreen(
    stats: ListeningStats,
    recording: Boolean,
    onBack: () -> Unit,
) {
    val today = remember { StatsStore.todayKey() }

    val todaySum = remember(stats, today) { ListeningInsights.todaySummary(stats, today) }
    val weekSum = remember(stats, today) { ListeningInsights.weekSummary(stats, today) }
    val monthSum = remember(stats, today) { ListeningInsights.monthSummary(stats, today) }
    val trend = remember(stats, today) {
        ListeningInsights.lastDays(stats, ListeningInsights.TREND_DAYS, today)
    }
    val activeDays = remember(stats, today) {
        ListeningInsights.activeDayCount(
            stats,
            StatsStore.shiftDay(today, -(ListeningInsights.TREND_DAYS - 1)),
            today,
        )
    }
    val months = remember(stats, today) { ListeningInsights.months(stats, ListeningInsights.MONTH_COUNT, today) }
    val ranking = remember(stats) { ListeningInsights.songRanking(stats, 5) }
    val slots = remember(stats, today) { ListeningInsights.timeSlots(stats, StatsStore.HOUR_WINDOW_DAYS, today) }
    val dominant = remember(slots) { ListeningInsights.dominantSlot(slots) }
    val spanDays = remember(stats, today) { ListeningInsights.spanDays(stats, today) }
    val recordedDays = remember(stats, today) {
        if (stats.firstDay.isBlank()) 0
        else ListeningInsights.activeDayCount(stats, stats.firstDay, today)
    }

    var sheetDay by remember { mutableStateOf<String?>(null) }
    var showAllSongs by remember { mutableStateOf(false) }

    StatsGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = BottomContentSpacer)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.page)
        ) {
            StatsHeader(onBack = onBack)

            // ===== 累计概览（含今日/本周/本月） =====
            StatsHeroCard(
                stats = stats,
                spanDays = spanDays,
                recording = recording,
                todaySum = todaySum,
                weekSum = weekSum,
                monthSum = monthSum,
            )

            Spacer(Modifier.height(AppSpacing.lg))

            // ===== 30 天趋势 =====
            StatsGlassCard {
                StatsSectionTitle(
                    title = "近 ${ListeningInsights.TREND_DAYS} 天收听趋势",
                    subtitle = "点击柱子查看那天的听歌日记",
                    trailing = {
                        StatsPill(
                            text = if (activeDays > 0) "活跃 $activeDays 天" else "暂无记录",
                            contentColor = AccentMint,
                            containerColor = AccentMint.copy(alpha = 0.12f),
                        )
                    },
                )
                Spacer(Modifier.height(AppSpacing.xxl))
                if (stats.isEmpty) {
                    StatsEmptyHint("还没有听歌记录，播放一首歌就会开始统计")
                } else {
                    StatsBarChart(points = trend, onDayClick = { sheetDay = it })
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            // ===== 近三月对比 =====
            StatsGlassCard {
                val maxMonth = months.maxOfOrNull { it.durationMs } ?: 0L
                StatsSectionTitle(
                    title = "月度对比",
                    subtitle = "最近 ${ListeningInsights.MONTH_COUNT} 个月",
                )
                Spacer(Modifier.height(AppSpacing.sm))
                if (maxMonth <= 0) {
                    StatsEmptyHint("还没有可对比的月度数据")
                } else {
                    months.asReversed().forEachIndexed { index, m ->
                        val fraction = if (maxMonth <= 0) 0f else m.durationMs.toFloat() / maxMonth
                        val isCurrent = index == 0
                        StatsProgressRow(
                            label = if (isCurrent) "本月（${m.month} 月）" else "${m.year} 年 ${m.month} 月",
                            valueText = ListeningInsights.formatDuration(m.durationMs),
                            fraction = fraction,
                            gradient = listOf(AccentTeal, AccentCyan),
                            trailingText = "${m.plays} 次",
                            labelColor = if (isCurrent) AccentAmber else TextPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            // ===== 歌曲排行 =====
            StatsGlassCard {
                StatsSectionTitle(
                    title = "最常听的歌",
                    subtitle = "按播放次数排行",
                    trailing = if (stats.topSongs.isNotEmpty()) {
                        {
                            StatsPill(
                                text = "全部 ${stats.topSongs.size} 首",
                                contentColor = AccentCyan,
                                containerColor = AccentCyan.copy(alpha = 0.12f),
                                modifier = Modifier.clickable { showAllSongs = true },
                            )
                        }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(AppSpacing.xs))
                if (ranking.isEmpty()) {
                    StatsEmptyHint("还没有排行数据")
                } else {
                    ranking.forEachIndexed { index, song ->
                        StatsRankRow(
                            rank = index + 1,
                            title = song.displayTitle,
                            artist = song.displayArtist,
                            trailingTop = "听 ${song.plays} 次",
                            trailingBottom = ListeningInsights.formatDuration(song.listenMs),
                        )
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            // ===== 时段分布 =====
            StatsGlassCard {
                StatsSectionTitle(
                    title = "时段分布",
                    subtitle = "近 ${StatsStore.HOUR_WINDOW_DAYS} 天的收听时间分布",
                )
                Spacer(Modifier.height(AppSpacing.sm))
                val hasSlot = slots.any { it.durationMs > 0 }
                if (!hasSlot) {
                    StatsEmptyHint("还没有足够的时段数据")
                } else {
                    slots.forEach { slot ->
                        StatsProgressRow(
                            label = slot.label,
                            valueText = "${(slot.percent * 100).toInt()}%",
                            fraction = slot.percent,
                            gradient = listOf(AccentViolet, AccentCyan),
                            trailingText = ListeningInsights.formatDuration(slot.durationMs),
                        )
                    }
                    if (dominant != null) {
                        Spacer(Modifier.height(AppSpacing.sm))
                        Text(
                            text = slotPersona(dominant.label, (dominant.percent * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                            color = TextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.section))
        }

        sheetDay?.let { day ->
            DayDetailSheet(
                day = day,
                stat = stats.day(day),
                onDismiss = { sheetDay = null },
            )
        }

        if (showAllSongs) {
            SongRankingSheet(
                songs = stats.topSongs,
                onDismiss = { showAllSongs = false },
            )
        }
    }
}

// ==================== 顶部 ====================

@Composable
private fun StatsHeader(onBack: () -> Unit) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = statusBarTop + AppSpacing.xl, bottom = AppSpacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(AppSpacing.xxs))

        Text(
            text = "听歌统计",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = TextPrimary,
        )
    }
}

/** 右上角状态：记录中（薄荷绿）/ 已暂停（珊瑚红） */
@Composable
private fun StatRecordingBadge(recording: Boolean) {
    val color = if (recording) AccentMint else AccentCoral
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = AppSpacing.md, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            text = if (recording) "记录中" else "已暂停",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

// ==================== 累计概览（含今日/本周/本月） ====================

@Composable
private fun StatsHeroCard(
    stats: ListeningStats,
    spanDays: Int,
    recording: Boolean,
    todaySum: RangeSummary,
    weekSum: RangeSummary,
    monthSum: RangeSummary,
) {
    StatsGlassCard {
        // 顶部：图标 + 标题/副标题 + 记录状态
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(AccentTeal, AccentCyan))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Headphones,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "累计聆听",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (stats.isEmpty) "播放一首歌就会开始统计" else "正在持续记录你的音乐足迹",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                )
            }
            StatRecordingBadge(recording = recording)
        }

        Spacer(Modifier.height(AppSpacing.xxl))

        // 大数字
        StatsBigDuration(ms = stats.totalListenMs)

        Spacer(Modifier.height(AppSpacing.sm))

        // 一行汇总
        Text(
            text = if (stats.isEmpty) {
                "数据只存在本机，未登录也不影响统计"
            } else {
                "共播放 ${stats.totalPlays} 次 · 跨越 $spanDays 个听歌日"
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextSoft,
        )

        Spacer(Modifier.height(AppSpacing.xxxl))

        // 今日 / 本周 / 本月
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            QuickSliceCard(
                label = "今日",
                durationMs = todaySum.durationMs,
                plays = todaySum.plays,
                highlight = false,
                modifier = Modifier.weight(1f),
            )
            QuickSliceCard(
                label = "本周",
                durationMs = weekSum.durationMs,
                plays = weekSum.plays,
                highlight = false,
                modifier = Modifier.weight(1f),
            )
            QuickSliceCard(
                label = "本月",
                durationMs = monthSum.durationMs,
                plays = monthSum.plays,
                highlight = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ==================== 今日/本周/本月小卡 ====================

@Composable
private fun QuickSliceCard(
    label: String,
    durationMs: Long,
    plays: Long,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (highlight) AccentAmber.copy(alpha = 0.10f) else GlassSurfaceSoft)
            .border(1.dp, GlassBorder, shape)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = if (highlight) AccentAmber else TextSoft,
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            text = ListeningInsights.formatDurationCompact(durationMs),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
            color = TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(AppSpacing.xxs))
        Text(
            text = if (plays > 0) "播放 ${plays} 次" else "暂无记录",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextSoft,
        )
    }
}

// ==================== 完整排行抽屉 ====================

@Composable
private fun SongRankingSheet(songs: List<SongStat>, onDismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.page)
                .padding(bottom = AppSpacing.section),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "歌曲排行",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "共 ${songs.size} 首 · 按播放次数排序",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.md))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                songs.forEachIndexed { index, song ->
                    StatsRankRow(
                        rank = index + 1,
                        title = song.displayTitle,
                        artist = song.displayArtist,
                        trailingTop = "听 ${song.plays} 次",
                        trailingBottom = ListeningInsights.formatDuration(song.listenMs),
                    )
                }
            }
        }
    }
}

// ==================== 单日日记抽屉 ====================

@Composable
private fun DayDetailSheet(day: String, stat: DayStat?, onDismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.page)
                .padding(bottom = AppSpacing.section),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = ListeningInsights.fullDayLabel(day),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "听歌日记",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                    )
                }
                val isToday = day == StatsStore.todayKey()
                StatsPill(
                    text = if (isToday) "今天" else ListeningInsights.weekdayOf(day),
                    contentColor = if (isToday) AccentAmber else AccentCyan,
                    containerColor = (if (isToday) AccentAmber else AccentCyan).copy(alpha = 0.12f),
                )
            }

            Spacer(Modifier.height(AppSpacing.xxxl))

            val listenMs = stat?.listenMs ?: 0L
            val plays = stat?.plays ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Stone100)
                    .padding(vertical = AppSpacing.xl),
            ) {
                DayMetricCell("收听时长", ListeningInsights.formatDurationCompact(listenMs), Modifier.weight(1f))
                DayMetricCell("播放次数", if (plays > 0) "$plays 次" else "0 次", Modifier.weight(1f))
                DayMetricCell(
                    label = "平均单次",
                    value = if (plays > 0 && listenMs > 0) ListeningInsights.formatDurationCompact(listenMs / plays) else "—",
                    modifier = Modifier.weight(1f),
                    highlight = true,
                )
            }

            Spacer(Modifier.height(AppSpacing.page))

            val songs = stat?.songs?.take(8).orEmpty()
            if (songs.isEmpty()) {
                StatsEmptyHint(
                    if (listenMs > 0 || plays > 0) {
                        "这天的歌曲明细已超出保留窗口（最近 ${StatsStore.DAY_SONG_WINDOW_DAYS} 天）"
                    } else {
                        "这天没有听歌记录"
                    }
                )
            } else {
                StatsSectionTitle(title = "当天最常听", subtitle = "按播放次数排序")
                Spacer(Modifier.height(AppSpacing.xxs))
                songs.forEachIndexed { index, song ->
                    DaySongRow(rank = index + 1, song = song)
                }
            }
        }
    }
}

@Composable
private fun DayMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(5.dp))
        if (highlight) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AccentAmber.copy(alpha = 0.16f))
                    .padding(horizontal = AppSpacing.sm, vertical = 2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = AccentAmber,
                )
            }
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
            )
        }
    }
}

@Composable
private fun DaySongRow(rank: Int, song: SongStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Stone100),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = TextMuted,
            )
        }
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.displayTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(AppSpacing.md))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (song.plays > 0) "听 ${song.plays} 次" else ListeningInsights.formatDuration(song.listenMs),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = AccentCyan,
            )
            if (song.lastPositionMs > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "最后 ${ListeningInsights.formatClock(song.lastPositionMs)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = TextSoft,
                )
            }
        }
    }
}

// ==================== 文案 ====================

private fun slotPersona(slotLabel: String, percent: Int): String = when (slotLabel) {
    "凌晨" -> "你常在凌晨听歌——占比 $percent%，是标准的深夜听众。"
    "上午" -> "你偏爱上午听歌——占比 $percent%，用音乐开启一天。"
    "下午" -> "你的主力时段在下午——占比 $percent%，午后时光多半有歌相伴。"
    else -> "你的主力时段在晚上——占比 $percent%，夜里是听歌的好时候。"
}