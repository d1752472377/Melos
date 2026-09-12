package com.aesthetic.music.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ListeningInsights] 纯计算层测试。
 *
 * 用固定日期（2026-09-13 是周日）构造数据，避免依赖"今天"导致测试随时间漂移：
 * 所有涉及"今天"的函数都接受显式日期参数。
 */
class ListeningInsightsTest {

    private fun day(d: String, ms: Long, plays: Int, hours: List<Long>? = null, songs: List<SongStat> = emptyList()) =
        DayStat(day = d, listenMs = ms, plays = plays, hourMs = hours ?: emptyList(), songs = songs)

    private fun song(key: String, plays: Int, ms: Long) = SongStat(
        platform = "netease",
        id = key,
        title = "歌$key",
        artist = "歌手",
        plays = plays,
        listenMs = ms,
        lastAtMs = 0L,
        lastPositionMs = 0L,
    )

    // ===== 区间汇总 =====

    @Test
    fun `今日汇总只统计当天`() {
        val stats = ListeningStats(
            days = listOf(
                day("2026-09-12", 60_000, 1),
                day("2026-09-13", 120_000, 2),
            )
        )
        val sum = ListeningInsights.todaySummary(stats, "2026-09-13")
        assertEquals(120_000L, sum.durationMs)
        assertEquals(2L, sum.plays)
    }

    @Test
    fun `本周从周一开始`() {
        // 2026-09-13 是周日，本周一是 2026-09-07
        assertEquals("2026-09-07", ListeningInsights.startOfWeek("2026-09-13"))
        // 周一当天，起点就是自己
        assertEquals("2026-09-07", ListeningInsights.startOfWeek("2026-09-07"))
        // 跨月：2026-03-01 是周日，本周一落在 2026-02-23
        assertEquals("2026-02-23", ListeningInsights.startOfWeek("2026-03-01"))
    }

    @Test
    fun `本周汇总不包含上周`() {
        val stats = ListeningStats(
            days = listOf(
                day("2026-09-06", 999_000, 9), // 上周日
                day("2026-09-07", 60_000, 1),
                day("2026-09-13", 30_000, 1),
            )
        )
        val sum = ListeningInsights.weekSummary(stats, "2026-09-13")
        assertEquals(90_000L, sum.durationMs)
        assertEquals(2L, sum.plays)
    }

    @Test
    fun `本月汇总跨月边界正确`() {
        val stats = ListeningStats(
            days = listOf(
                day("2026-08-31", 500_000, 5),
                day("2026-09-01", 10_000, 1),
                day("2026-09-13", 20_000, 2),
            )
        )
        val sum = ListeningInsights.monthSummary(stats, "2026-09-13")
        assertEquals(30_000L, sum.durationMs)
        assertEquals(3L, sum.plays)
    }

    @Test
    fun `活跃天数只算有记录的天`() {
        val stats = ListeningStats(
            days = listOf(
                day("2026-09-11", 0, 0),
                day("2026-09-12", 1_000, 0),
                day("2026-09-13", 0, 2),
                day("2026-08-01", 5_000, 1), // 窗口外
            )
        )
        assertEquals(2, ListeningInsights.activeDayCount(stats, "2026-09-01", "2026-09-13"))
    }

    @Test
    fun `跨越天数含首尾`() {
        val stats = ListeningStats(firstDay = "2026-09-01", days = listOf(day("2026-09-01", 1_000, 1)))
        assertEquals(13, ListeningInsights.spanDays(stats, "2026-09-13"))
        assertEquals(0, ListeningInsights.spanDays(ListeningStats(), "2026-09-13"))
    }

    // ===== 趋势 =====

    @Test
    fun `趋势补全空白天并标记今天`() {
        val stats = ListeningStats(days = listOf(day("2026-09-13", 5_000, 1)))
        val points = ListeningInsights.lastDays(stats, 30, "2026-09-13")
        assertEquals(30, points.size)
        assertEquals("2026-08-15", points.first().day)
        assertEquals("2026-09-13", points.last().day)
        assertTrue(points.last().isToday)
        assertEquals(5_000L, points.last().durationMs)
        // 中间空白天保留，柱状图才能看出"中断"
        assertEquals(29, points.count { !it.isActive })
    }

    @Test
    fun `趋势标签为月日`() {
        val points = ListeningInsights.lastDays(ListeningStats(), 1, "2026-09-13")
        assertEquals("9/13", points.single().label)
    }

    // ===== 月度对比 =====

    @Test
    fun `月度对比返回最近三个月且顺序为由旧到新`() {
        val stats = ListeningStats(
            days = listOf(
                day("2026-07-15", 1_000, 1),
                day("2026-08-15", 2_000, 2),
                day("2026-09-15", 3_000, 3),
                day("2026-06-15", 9_000, 9), // 更早的月份不参与
            )
        )
        val months = ListeningInsights.months(stats, 3, "2026-09-13")
        assertEquals(listOf("7月", "8月", "9月"), months.map { it.label })
        assertEquals(1_000L, months[0].durationMs)
        assertEquals(2_000L, months[1].durationMs)
        assertEquals(3_000L, months[2].durationMs)
    }

    @Test
    fun `月度对比跨年取 12 月与 1 月`() {
        val months = ListeningInsights.months(ListeningStats(), 3, "2026-01-10")
        assertEquals(listOf(2025, 2025, 2026), months.map { it.year })
        assertEquals(listOf(11, 12, 1), months.map { it.month })
    }

    // ===== 时段分布 =====

    @Test
    fun `时段分布按四段划分并算百分比`() {
        val hours = MutableList(24) { 0L }
        hours[0] = 680_000L // 凌晨
        hours[13] = 200_000L // 下午
        hours[20] = 120_000L // 晚上
        val stats = ListeningStats(days = listOf(day("2026-09-13", 1_000_000L, 3, hours = hours)))

        val slots = ListeningInsights.timeSlots(stats, 30, "2026-09-13")
        assertEquals(listOf("凌晨", "上午", "下午", "晚上"), slots.map { it.label })
        assertEquals(680_000L, slots[0].durationMs)
        assertEquals(0L, slots[1].durationMs)
        assertEquals(200_000L, slots[2].durationMs)
        assertEquals(120_000L, slots[3].durationMs)
        assertEquals(68, (slots[0].percent * 100).toInt())
        assertEquals("凌晨", ListeningInsights.dominantSlot(slots)?.label)
    }

    @Test
    fun `时段分布忽略窗口外与缺小时明细的天`() {
        val hours = MutableList(24) { 0L }
        hours[10] = 999_000L
        val stats = ListeningStats(
            days = listOf(
                day("2026-08-01", 999_000L, 1, hours = hours), // 窗口外
                day("2026-09-13", 100_000L, 1), // 没有小时明细
            )
        )
        val slots = ListeningInsights.timeSlots(stats, 30, "2026-09-13")
        assertEquals(0L, slots.sumOf { it.durationMs })
        assertEquals(0f, slots[0].percent, 0.0001f)
    }

    @Test
    fun `时段边界小时归属正确`() {
        assertEquals(DaySlot.DAWN, DaySlot.of(0))
        assertEquals(DaySlot.DAWN, DaySlot.of(5))
        assertEquals(DaySlot.MORNING, DaySlot.of(6))
        assertEquals(DaySlot.MORNING, DaySlot.of(11))
        assertEquals(DaySlot.AFTERNOON, DaySlot.of(12))
        assertEquals(DaySlot.AFTERNOON, DaySlot.of(17))
        assertEquals(DaySlot.EVENING, DaySlot.of(18))
        assertEquals(DaySlot.EVENING, DaySlot.of(23))
    }

    // ===== 排行 =====

    @Test
    fun `歌曲排行按次数降序取前几`() {
        val stats = ListeningStats(
            topSongs = listOf(song("a", 3, 100_000), song("b", 9, 10_000), song("c", 5, 50_000))
        )
        val ranking = ListeningInsights.songRanking(stats, 2)
        assertEquals(listOf("b", "c"), ranking.map { it.id })
    }

    @Test
    fun `次数相同时按时长排序`() {
        val stats = ListeningStats(topSongs = listOf(song("short", 2, 10_000), song("long", 2, 90_000)))
        assertEquals("long", ListeningInsights.songRanking(stats, 5).first().id)
    }

    // ===== 格式化 =====

    @Test
    fun `时长格式化覆盖秒分小时`() {
        assertEquals("0 分", ListeningInsights.formatDuration(0))
        assertEquals("19 秒", ListeningInsights.formatDuration(19_000))
        assertEquals("59 秒", ListeningInsights.formatDuration(59_999))
        assertEquals("1 分", ListeningInsights.formatDuration(60_000))
        assertEquals("44 分", ListeningInsights.formatDuration(44 * 60_000L))
        assertEquals("1 小时", ListeningInsights.formatDuration(60 * 60_000L))
        assertEquals("1 小时 8 分", ListeningInsights.formatDuration(68 * 60_000L))
        assertEquals("23 小时", ListeningInsights.formatDuration(23 * 3_600_000L))
    }

    @Test
    fun `紧凑时长去掉空格`() {
        assertEquals("1小时8分", ListeningInsights.formatDurationCompact(68 * 60_000L))
        assertEquals("44分", ListeningInsights.formatDurationCompact(44 * 60_000L))
        assertEquals("19秒", ListeningInsights.formatDurationCompact(19_000))
    }

    @Test
    fun `大数字拆分单位`() {
        assertEquals(listOf("1" to "小时", "8" to "分"), ListeningInsights.durationParts(68 * 60_000L))
        assertEquals(listOf("44" to "分"), ListeningInsights.durationParts(44 * 60_000L))
        assertEquals(listOf("0" to "分"), ListeningInsights.durationParts(0))
    }

    @Test
    fun `播放时间点格式化为分秒`() {
        assertEquals("00:49", ListeningInsights.formatClock(49_000))
        assertEquals("03:05", ListeningInsights.formatClock(185_000))
        assertEquals("00:00", ListeningInsights.formatClock(-1))
    }

    // ===== 日期文案 =====

    @Test
    fun `相对标签区分今天昨天与更早`() {
        assertEquals("今天", ListeningInsights.relativeLabel("2026-09-13", "2026-09-13"))
        assertEquals("昨天", ListeningInsights.relativeLabel("2026-09-12", "2026-09-13"))
        assertEquals("9月1日", ListeningInsights.relativeLabel("2026-09-01", "2026-09-13"))
    }

    @Test
    fun `完整日期带星期`() {
        assertEquals("2026 年 9 月 13 日 周日", ListeningInsights.fullDayLabel("2026-09-13"))
        assertEquals("周六", ListeningInsights.weekdayOf("2026-02-28"))
    }

    @Test
    fun `相差天数可跨月为负`() {
        assertEquals(13, ListeningInsights.daysBetween("2026-09-01", "2026-09-14"))
        assertEquals(-3, ListeningInsights.daysBetween("2026-09-13", "2026-09-10"))
        assertEquals(0, ListeningInsights.daysBetween("坏数据", "2026-09-10"))
    }

    @Test
    fun `空统计不崩且返回安全值`() {
        val empty = ListeningStats()
        assertTrue(empty.isEmpty)
        assertNull(empty.day("2026-09-13"))
        assertTrue(ListeningInsights.timeSlots(empty, 30, "2026-09-13").all { it.percent == 0f })
        assertNull(ListeningInsights.dominantSlot(ListeningInsights.timeSlots(empty, 30, "2026-09-13")))
        assertEquals(0, ListeningInsights.spanDays(empty, "2026-09-13"))
    }
}
