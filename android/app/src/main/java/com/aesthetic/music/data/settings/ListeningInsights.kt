package com.aesthetic.music.data.settings

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 时间区间汇总 */
data class RangeSummary(val durationMs: Long = 0, val plays: Long = 0) {
    val isEmpty: Boolean get() = durationMs <= 0 && plays <= 0
}

/** 30 天趋势图上的一根柱子 */
data class DayPoint(
    val day: String,
    val label: String,
    val durationMs: Long,
    val plays: Int,
    val isToday: Boolean,
) {
    val isActive: Boolean get() = durationMs > 0 || plays > 0
}

/** 月度对比里的一行 */
data class MonthPoint(
    val label: String,
    val year: Int,
    val month: Int,
    val durationMs: Long,
    val plays: Long,
)

/** 时段分布里的一段 */
data class SlotPoint(
    val label: String,
    val durationMs: Long,
    val percent: Float,
)

/** 时段划分（凌晨/上午/下午/晚上），与设置页文案一致 */
enum class DaySlot(val label: String, val startHour: Int, val endHour: Int) {
    DAWN("凌晨", 0, 5),
    MORNING("上午", 6, 11),
    AFTERNOON("下午", 12, 17),
    EVENING("晚上", 18, 23);

    fun contains(hour: Int): Boolean = hour in startHour..endHour

    companion object {
        fun of(hour: Int): DaySlot = entries.firstOrNull { it.contains(hour) } ?: EVENING
    }
}

/**
 * 听歌统计的纯计算层。
 *
 * 与 [StatsStore]（负责记录与落盘）分开：这里全是无副作用、只依赖入参的函数，
 * 因而可以在 JVM 单测里用构造出来的数据直接验证——
 * 统计页最怕"算错但看起来像对的"（百分比算反、跨月边界取错、时长格式化进位错），
 * 这些都必须有测试兜住。
 */
object ListeningInsights {

    /** 趋势图默认展示天数 */
    const val TREND_DAYS = 30

    /** 月度对比默认展示月数 */
    const val MONTH_COUNT = 3

    // ==================== 区间汇总 ====================

    /** [fromDay, toDay] 闭区间汇总（日期字符串直接比较，yyyy-MM-dd 字典序等于时间序） */
    fun summary(stats: ListeningStats, fromDay: String, toDay: String): RangeSummary {
        var ms = 0L
        var plays = 0L
        for (d in stats.days) {
            if (d.day < fromDay || d.day > toDay) continue
            ms += d.listenMs
            plays += d.plays
        }
        return RangeSummary(ms, plays)
    }

    fun todaySummary(stats: ListeningStats, today: String = StatsStore.todayKey()): RangeSummary =
        summary(stats, today, today)

    /** 本周（周一为起点）汇总 */
    fun weekSummary(stats: ListeningStats, today: String = StatsStore.todayKey()): RangeSummary =
        summary(stats, startOfWeek(today), today)

    /** 本月汇总 */
    fun monthSummary(stats: ListeningStats, today: String = StatsStore.todayKey()): RangeSummary =
        summary(stats, today.substring(0, 7) + "-01", today)

    /** 区间内有记录的天数（"活跃 N 天"） */
    fun activeDayCount(stats: ListeningStats, fromDay: String, toDay: String): Int =
        stats.days.count { it.day in fromDay..toDay && it.isActive }

    /** 跨越天数：从最早记录到今天（含首尾）；没有记录返回 0 */
    fun spanDays(stats: ListeningStats, today: String = StatsStore.todayKey()): Int {
        val first = stats.firstDay.ifBlank { stats.days.firstOrNull()?.day ?: return 0 }
        return daysBetween(first, today) + 1
    }

    // ==================== 趋势 ====================

    /**
     * 最近 [days] 天的逐日数据（含没有收听的空白天，柱状图需要空格子来体现"中断"）。
     */
    fun lastDays(
        stats: ListeningStats,
        days: Int = TREND_DAYS,
        today: String = StatsStore.todayKey(),
    ): List<DayPoint> {
        val byDay = stats.days.associateBy { it.day }
        return (days - 1 downTo 0).map { offset ->
            val day = StatsStore.shiftDay(today, -offset)
            val d = byDay[day]
            DayPoint(
                day = day,
                label = dayLabel(day),
                durationMs = d?.listenMs ?: 0L,
                plays = d?.plays ?: 0,
                isToday = day == today,
            )
        }
    }

    // ==================== 月度对比 ====================

    /** 最近 [count] 个月（含本月）的汇总，返回顺序为旧 → 新 */
    fun months(
        stats: ListeningStats,
        count: Int = MONTH_COUNT,
        today: String = StatsStore.todayKey(),
    ): List<MonthPoint> {
        val todayCal = calendarOf(today) ?: return emptyList()
        val result = ArrayList<MonthPoint>(count)
        for (offset in count - 1 downTo 0) {
            val cal = (todayCal.clone() as Calendar).apply {
                add(Calendar.MONTH, -offset)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val from = "%04d-%02d-01".format(year, month)
            val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val to = "%04d-%02d-%02d".format(year, month, lastDay)
            val sum = summary(stats, from, to)
            result.add(
                MonthPoint(
                    label = "${month}月",
                    year = year,
                    month = month,
                    durationMs = sum.durationMs,
                    plays = sum.plays,
                )
            )
        }
        return result
    }

    // ==================== 时段分布 ====================

    /**
     * 时段分布（凌晨/上午/下午/晚上）。
     *
     * 只统计保留了小时明细的天数窗口（见 [StatsStore.HOUR_WINDOW_DAYS]），
     * 界面文案会写明窗口，避免用户以为这是"有史以来"的分布。
     */
    fun timeSlots(
        stats: ListeningStats,
        days: Int = StatsStore.HOUR_WINDOW_DAYS,
        today: String = StatsStore.todayKey(),
    ): List<SlotPoint> {
        val from = StatsStore.shiftDay(today, -(days - 1))
        val totals = LongArray(DaySlot.entries.size)
        for (d in stats.days) {
            if (d.day < from || d.day > today) continue
            if (d.hourMs.size != StatsStore.HOURS_PER_DAY) continue
            d.hourMs.forEachIndexed { hour, ms ->
                if (ms > 0) totals[DaySlot.of(hour).ordinal] += ms
            }
        }
        val all = totals.sum()
        return DaySlot.entries.mapIndexed { idx, slot ->
            SlotPoint(
                label = slot.label,
                durationMs = totals[idx],
                percent = if (all <= 0) 0f else totals[idx].toFloat() / all.toFloat(),
            )
        }
    }

    /** 占比最高的时段（用于"夜猫子"这类一句话画像）；没有数据返回 null */
    fun dominantSlot(slots: List<SlotPoint>): SlotPoint? =
        slots.filter { it.durationMs > 0 }.maxByOrNull { it.durationMs }

    // ==================== 歌曲排行 ====================

    /** 按播放次数排行（同次数按时长），取前 [limit] 首 */
    fun songRanking(stats: ListeningStats, limit: Int = 5): List<SongStat> =
        stats.topSongs
            .filter { it.plays > 0 || it.listenMs > 0 }
            .sortedWith(compareByDescending<SongStat> { it.plays }.thenByDescending { it.listenMs })
            .take(limit)

    // ==================== 日期与格式化 ====================

    /** 本周一（周一为一周起点，符合国内习惯） */
    fun startOfWeek(today: String): String {
        val cal = calendarOf(today) ?: return today
        val dow = cal.get(Calendar.DAY_OF_WEEK) // SUNDAY=1
        val offset = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
        return StatsStore.shiftDay(today, offset)
    }

    /** 两个 yyyy-MM-dd 之间相差的自然日数（b - a，负值表示 b 在 a 之前） */
    fun daysBetween(a: String, b: String): Int {
        val ca = calendarOf(a) ?: return 0
        val cb = calendarOf(b) ?: return 0
        val millis = cb.timeInMillis - ca.timeInMillis
        return Math.round(millis.toDouble() / 86_400_000.0).toInt()
    }

    /** 柱状图坐标轴用：9/13 */
    fun dayLabel(day: String): String {
        val parts = day.split("-")
        if (parts.size != 3) return day
        return "${parts[1].trimStart('0').ifBlank { "0" }}/${parts[2].trimStart('0').ifBlank { "0" }}"
    }

    /** 抽屉标题用：2026 年 9 月 13 日 周六 */
    fun fullDayLabel(day: String): String {
        val cal = calendarOf(day) ?: return day
        val weekday = WEEKDAYS.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 1) { "" }
        return "${cal.get(Calendar.YEAR)} 年 ${cal.get(Calendar.MONTH) + 1} 月 ${cal.get(Calendar.DAY_OF_MONTH)} 日 $weekday"
    }

    /** 短星期：周六 */
    fun weekdayOf(day: String): String {
        val cal = calendarOf(day) ?: return ""
        return WEEKDAYS.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 1) { "" }
    }

    /** 相对今天的口语标签：今天 / 昨天 / 9月13日 */
    fun relativeLabel(day: String, today: String = StatsStore.todayKey()): String = when (day) {
        today -> "今天"
        StatsStore.shiftDay(today, -1) -> "昨天"
        else -> {
            val parts = day.split("-")
            if (parts.size == 3) {
                "${parts[1].trimStart('0').ifBlank { "0" }}月${parts[2].trimStart('0').ifBlank { "0" }}日"
            } else {
                day
            }
        }
    }

    /**
     * 时长文案：秒 → 分 → 小时。
     * 例：19 秒 / 44 分 / 1 小时 8 分（超过 10 小时不显示分钟，避免"23 小时 59 分"这种噪音）
     */
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0 分"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val hours = minutes / 60
        return when {
            totalSeconds < 60 -> "$totalSeconds 秒"
            hours <= 0 -> "$minutes 分"
            hours >= 10 -> "$hours 小时"
            else -> {
                val rest = minutes % 60
                if (rest == 0L) "$hours 小时" else "$hours 小时 $rest 分"
            }
        }
    }

    /** 超大数字旁的紧凑写法（卡片用）：1小时8分 / 44分 / 19秒 */
    fun formatDurationCompact(ms: Long): String = formatDuration(ms).replace(" ", "")

    /** 大数字与单位拆开（UI 需要"数字大、单位小"的排版） */
    fun durationParts(ms: Long): List<Pair<String, String>> {
        if (ms <= 0) return listOf("0" to "分")
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val hours = minutes / 60
        return when {
            totalSeconds < 60 -> listOf("$totalSeconds" to "秒")
            hours <= 0 -> listOf("$minutes" to "分")
            hours >= 10 -> listOf("$hours" to "小时")
            else -> {
                val rest = minutes % 60
                if (rest == 0L) listOf("$hours" to "小时")
                else listOf("$hours" to "小时", "$rest" to "分")
            }
        }
    }

    /** 曲内位置/时间点：00:49 */
    fun formatClock(ms: Long): String {
        if (ms < 0) return "00:00"
        val total = ms / 1000
        val m = total / 60
        val s = total % 60
        return "%02d:%02d".format(m, s)
    }

    // ==================== 内部 ====================

    private val WEEKDAYS = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    private val dayFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    private fun calendarOf(day: String): Calendar? = try {
        val date: Date = dayFormat.get()?.parse(day) ?: return null
        Calendar.getInstance().apply { time = date }
    } catch (_: Exception) {
        null
    }
}
