package com.aesthetic.music.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
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
import com.aesthetic.music.data.settings.SongStat
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.AccentTeal
import com.aesthetic.music.ui.theme.AccentViolet
import com.aesthetic.music.ui.theme.GlassSurfaceSoft
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 听歌统计页（重写版）。
 *
 * 页面结构：累计概览 → 今日/本周/本月切片 → 30 天趋势（可点柱子下钻单日）
 * → 近三月对比 → 歌曲排行 → 时段分布 → 统计设置。
 *
 * 所有数字都来自 [StatsStore] 的真实记录（旧版页面里的柱状图是写死的假数据，
 * 那种"看起来有数据、其实是装饰"的实现会误导用户，已删除）。
 * 数据在本地记录，未登录也不影响统计。
 */
@Composable
fun ListeningStatsScreen(
    stats: ListeningStats,
    recording: Boolean,
    cloudSync: Boolean,
    onToggleRecording: (Boolean) -> Unit,
    onToggleCloudSync: (Boolean) -> Unit,
    onClearStats: () -> Unit,
    onBack: () -> Unit,
) {
    val today = remember { StatsStore.todayKey() }

    // 全部派生数据一次算好：统计页是纯只读展示，避免滚动时反复排序/求和
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
    // 全期"有记录的天数"：与 hero 里的跨度天数、趋势卡的 30 天活跃天数是三个不同口径
    val recordedDays = remember(stats, today) {
        if (stats.firstDay.isBlank()) 0
        else ListeningInsights.activeDayCount(stats, stats.firstDay, today)
    }

    var sheetDay by remember { mutableStateOf<String?>(null) }
    var showAllSongs by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    StatsGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = LocalBottomBarPadding.current),
        ) {
            StatsHeader(
                recording = recording,
                onBack = onBack,
            )

            // ===== 累计概览 =====
            StatsHeroCard(
                stats = stats,
                spanDays = spanDays,
                recordedDays = recordedDays,
            )

            Spacer(Modifier.height(12.dp))

            // ===== 今日 / 本周 / 本月 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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

            Spacer(Modifier.height(12.dp))

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
                Spacer(Modifier.height(16.dp))
                if (stats.isEmpty) {
                    StatsEmptyHint("还没有听歌记录，播放一首歌就会开始统计")
                } else {
                    StatsBarChart(points = trend, onDayClick = { sheetDay = it })
                    Spacer(Modifier.height(12.dp))
                    TrendConclusion(activeDays = activeDays, trend = trend, today = today)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===== 近三月对比 =====
            StatsGlassCard {
                val maxMonth = months.maxOfOrNull { it.durationMs } ?: 0L
                StatsSectionTitle(
                    title = "月度对比",
                    subtitle = "最近 ${ListeningInsights.MONTH_COUNT} 个月",
                )
                Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(12.dp))

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
                Spacer(Modifier.height(6.dp))
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

            Spacer(Modifier.height(12.dp))

            // ===== 时段分布 =====
            StatsGlassCard {
                StatsSectionTitle(
                    title = "时段分布",
                    subtitle = "近 ${StatsStore.HOUR_WINDOW_DAYS} 天的收听时间分布",
                )
                Spacer(Modifier.height(8.dp))
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = slotPersona(dominant.label, (dominant.percent * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                            color = TextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===== 统计设置 =====
            StatsGlassCard {
                StatsSectionTitle(
                    title = "统计设置",
                    subtitle = "数据存在本机，随时可以停止记录",
                )
                Spacer(Modifier.height(4.dp))
                StatsToggleRow(
                    icon = Icons.Filled.Mic,
                    title = "记录听歌数据",
                    subtitle = if (recording) "正在记录播放时长与次数" else "已停止记录，历史数据保留",
                    checked = recording,
                    onCheckedChange = onToggleRecording,
                    activeColor = AccentMint,
                )
                StatsToggleRow(
                    icon = Icons.Filled.CloudOff,
                    title = "统计云同步",
                    subtitle = if (cloudSync) "听歌事件会随同步上传到你的私有云端" else "只在本机记录，不上传云端",
                    checked = cloudSync,
                    onCheckedChange = onToggleCloudSync,
                    activeColor = AccentViolet,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentCoral.copy(alpha = 0.08f))
                        .clickable { showClearConfirm = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            tint = AccentCoral,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "清空统计记录",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = AccentCoral,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "删除本机全部时长、次数与排行数据，不可恢复",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextSoft,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
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

        if (showClearConfirm) {
            AppConfirmDialog(
                onDismissRequest = { showClearConfirm = false },
                title = "清空统计记录？",
                message = "将删除本机保存的全部听歌时长、播放次数与歌曲排行数据，操作不可恢复。",
                confirmText = "清空",
                onConfirm = {
                    showClearConfirm = false
                    onClearStats()
                },
            )
        }
    }
}

// ==================== 顶部 ====================

@Composable
private fun StatsHeader(recording: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GlassSurfaceSoft)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "听歌统计",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                color = TextPrimary,
            )
            Text(
                text = "本机记录 · 未登录也统计",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
            )
        }
        StatRecordingBadge(recording = recording)
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
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (recording) "记录中" else "已暂停",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

// ==================== 累计概览 ====================

@Composable
private fun StatsHeroCard(stats: ListeningStats, spanDays: Int, recordedDays: Int) {
    StatsGlassCard {
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
            Spacer(Modifier.width(10.dp))
            Text(
                text = "累计聆听",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(14.dp))
        StatsBigDuration(ms = stats.totalListenMs)
        Spacer(Modifier.height(8.dp))
        Text(
            // 有记录但不足 1 分钟时，大数字会显示 "0 分"，这里给一句真实说明，避免看起来像没统计上
            text = when {
                stats.isEmpty -> "播放一首歌就会开始统计，数据只存在本机"
                stats.totalListenMs in 1..59_999 -> "已经有记录了，攒够 1 分钟就会按分钟显示"
                else -> "从 ${stats.firstDay} 开始统计"
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextSoft,
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatsMiniMetric(
                label = "总播放次数",
                value = "${stats.totalPlays} 次",
                modifier = Modifier.weight(1f),
            )
            StatsMiniMetric(
                label = "跨越听歌日数",
                value = if (spanDays > 0) "$spanDays 天" else "—",
                modifier = Modifier.weight(1f),
            )
            StatsMiniMetric(
                label = "累计记录天数",
                value = if (recordedDays > 0) "$recordedDays 天" else "—",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ==================== 今日/本周/本月 ====================

@Composable
private fun QuickSliceCard(
    label: String,
    durationMs: Long,
    plays: Long,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (highlight) AccentAmber.copy(alpha = 0.12f) else GlassSurfaceSoft)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = if (highlight) AccentAmber else TextSoft,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = ListeningInsights.formatDurationCompact(durationMs),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            color = TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (plays > 0) "${plays} 次" else "暂无",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = TextSoft,
        )
    }
}

// ==================== 趋势结论 ====================

@Composable
private fun TrendConclusion(activeDays: Int, trend: List<com.aesthetic.music.data.settings.DayPoint>, today: String) {
    val total = trend.sumOf { it.durationMs }
    val average = if (activeDays > 0) total / activeDays else 0L
    val best = trend.maxByOrNull { it.durationMs }
    val conclusion = buildString {
        if (activeDays <= 0) {
            append("这 ${ListeningInsights.TREND_DAYS} 天还没有听歌记录。")
        } else {
            append("这 ${ListeningInsights.TREND_DAYS} 天里有 $activeDays 天听了歌")
            if (average > 0) append("，活跃日均 ${ListeningInsights.formatDuration(average)}")
            if (best != null && best.durationMs > 0 && best.day != today) {
                append("；听最多的是 ${ListeningInsights.relativeLabel(best.day, today)}（${ListeningInsights.formatDuration(best.durationMs)}）")
            }
            append("。")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AccentTeal.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.AccessTime,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = conclusion,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            color = TextMuted,
        )
    }
}

// ==================== 完整排行抽屉 ====================

/**
 * 完整歌曲排行（最多 [StatsStore] 保留的 N 首）。
 *
 * 页面上只展示前 5 首，剩下的走抽屉：统计页首屏不该被长列表占满，
 * 但"排到第 20 名的歌"这类数据用户偶尔确实想看，丢掉又可惜。
 */
@Composable
private fun SongRankingSheet(songs: List<SongStat>, onDismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
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
            Spacer(Modifier.height(10.dp))
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

/**
 * 单日听歌日记：总时长 / 总次数 / 平均单次 + 当天最常听的歌（含"最后 00:49"这样的播放点）。
 */
@Composable
private fun DayDetailSheet(day: String, stat: DayStat?, onDismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
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

            Spacer(Modifier.height(18.dp))

            val listenMs = stat?.listenMs ?: 0L
            val plays = stat?.plays ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Stone100)
                    .padding(vertical = 14.dp),
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

            Spacer(Modifier.height(20.dp))

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
                Spacer(Modifier.height(4.dp))
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
        // 平均单次是"算出来的派生值"，用暖色小标签和两个原始值区分开
        if (highlight) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AccentAmber.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
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

/** 单日歌曲行：歌名 + 听 N 次 + "最后 00:49"（当天最后一次播放到的曲内位置） */
@Composable
private fun DaySongRow(rank: Int, song: SongStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
        Spacer(Modifier.width(10.dp))
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
        Spacer(Modifier.width(10.dp))
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

/** 时段画像：把占比最高的时段翻译成一句人话 */
private fun slotPersona(slotLabel: String, percent: Int): String = when (slotLabel) {
    "凌晨" -> "你常在凌晨听歌——占比 $percent%，是标准的深夜听众。"
    "上午" -> "你偏爱上午听歌——占比 $percent%，用音乐开启一天。"
    "下午" -> "你的主力时段在下午——占比 $percent%，午后时光多半有歌相伴。"
    else -> "你的主力时段在晚上——占比 $percent%，夜里是听歌的好时候。"
}
