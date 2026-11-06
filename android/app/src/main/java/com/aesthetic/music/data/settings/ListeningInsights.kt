package com.aesthetic.music.data.settings

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class RangeSummary(val durationMs: Long = 0, val plays: Long = 0) {
    val isEmpty: Boolean get() = durationMs <= 0 && plays <= 0
}

data class DayPoint(
    val day: String,
    val label: String,
    val durationMs: Long,
    val plays: Int,
    val isToday: Boolean,
) {
    val isActive: Boolean get() = durationMs > 0 || plays > 0
}

data class MonthPoint(
    val label: String,
    val year: Int,
    val month: Int,
    val durationMs: Long,
    val plays: Long,
)

data class SlotPoint(
    val label: String,
    val durationMs: Long,
    val percent: Float,
)

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

object ListeningInsights {

    const val TREND_DAYS = 30

    const val MONTH_COUNT = 3

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

    fun weekSummary(stats: ListeningStats, today: String = StatsStore.todayKey()): RangeSummary =
        summary(stats, startOfWeek(today), today)

    fun monthSummary(stats: ListeningStats, today: String = StatsStore.todayKey()): RangeSummary =
        summary(stats, today.substring(0, 7) + "-01", today)

    fun activeDayCount(stats: ListeningStats, fromDay: String, toDay: String): Int =
        stats.days.count { it.day in fromDay..toDay && it.isActive }

    fun spanDays(stats: ListeningStats, today: String = StatsStore.todayKey()): Int {
        val first = stats.firstDay.ifBlank { stats.days.firstOrNull()?.day ?: return 0 }
        return daysBetween(first, today) + 1
    }

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

    fun dominantSlot(slots: List<SlotPoint>): SlotPoint? =
        slots.filter { it.durationMs > 0 }.maxByOrNull { it.durationMs }

    fun songRanking(stats: ListeningStats, limit: Int = 5): List<SongStat> =
        stats.topSongs
            .filter { it.plays > 0 || it.listenMs > 0 }
            .sortedWith(compareByDescending<SongStat> { it.plays }.thenByDescending { it.listenMs })
            .take(limit)

    fun startOfWeek(today: String): String {
        val cal = calendarOf(today) ?: return today
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val offset = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
        return StatsStore.shiftDay(today, offset)
    }

    fun daysBetween(a: String, b: String): Int {
        val ca = calendarOf(a) ?: return 0
        val cb = calendarOf(b) ?: return 0
        val millis = cb.timeInMillis - ca.timeInMillis
        return Math.round(millis.toDouble() / 86_400_000.0).toInt()
    }

    fun dayLabel(day: String): String {
        val parts = day.split("-")
        if (parts.size != 3) return day
        return "${parts[1].trimStart('0').ifBlank { "0" }}/${parts[2].trimStart('0').ifBlank { "0" }}"
    }

    fun fullDayLabel(day: String): String {
        val cal = calendarOf(day) ?: return day
        val weekday = WEEKDAYS.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 1) { "" }
        return "${cal.get(Calendar.YEAR)} 年 ${cal.get(Calendar.MONTH) + 1} 月 ${cal.get(Calendar.DAY_OF_MONTH)} 日 $weekday"
    }

    fun weekdayOf(day: String): String {
        val cal = calendarOf(day) ?: return ""
        return WEEKDAYS.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 1) { "" }
    }

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

    fun formatDurationCompact(ms: Long): String = formatDuration(ms).replace(" ", "")

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

    fun formatClock(ms: Long): String {
        if (ms < 0) return "00:00"
        val total = ms / 1000
        val m = total / 60
        val s = total % 60
        return "%02d:%02d".format(m, s)
    }

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
