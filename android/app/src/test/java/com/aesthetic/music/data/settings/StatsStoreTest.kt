package com.aesthetic.music.data.settings

import com.aesthetic.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [StatsStore] 的累计、序列化、保留窗口测试。
 *
 * 这里刻意不依赖 SharedPreferences（JVM 下 `init` 未调用，落盘自动降级为 no-op），
 * 但会在对象里维护内存态，所以每个测试前必须 [StatsStore.clearAll] 清干净，
 * 否则用例之间会互相污染。
 */
class StatsStoreTest {

    @Before
    fun setUp() {
        StatsStore.clearAll()
    }

    private fun song(id: String, title: String = "夜曲", artist: String = "周杰伦") = Song(
        id = id,
        title = title,
        artist = artist,
        album = "十一月的萧邦",
        platform = "netease",
        artwork = null,
    )

    // ===== 记录 =====

    @Test
    fun `播放一次会累计总次数与当天次数`() {
        StatsStore.recordPlay(song("1"))
        val stats = StatsStore.snapshot()
        assertEquals(1L, stats.totalPlays)
        val today = stats.day(StatsStore.todayKey())
        assertNotNull(today)
        assertEquals(1, today!!.plays)
        assertEquals(StatsStore.todayKey(), stats.firstDay)
        assertEquals(1, stats.topSongs.single().plays)
    }

    @Test
    fun `收听增量累加到当天与全期且不重复计数`() {
        val s = song("1")
        StatsStore.recordPlay(s)
        StatsStore.recordListen(s, 60_000, 49_000)
        StatsStore.recordListen(s, 30_000, 59_000)

        val stats = StatsStore.snapshot()
        assertEquals(90_000L, stats.totalListenMs)
        val today = stats.day(StatsStore.todayKey())!!
        assertEquals(90_000L, today.listenMs)
        // 最后一次收听到的位置是 59 秒
        assertEquals(59_000L, today.songs.single().lastPositionMs)
        // 全期排行必须也是 90 秒：日条目是"当天累计"，若误当日增量相加会变成 150 秒
        assertEquals(90_000L, stats.topSongs.single().listenMs)
        assertEquals(1, stats.topSongs.single().plays)
    }

    @Test
    fun `最后播放位置随每次收听更新`() {
        val s = song("1")
        StatsStore.recordPlay(s)
        StatsStore.recordListen(s, 1_000, 12_000)
        StatsStore.recordListen(s, 1_000, 49_000)
        val day = StatsStore.snapshot().day(StatsStore.todayKey())!!
        assertEquals(49_000L, day.songs.single().lastPositionMs)
    }

    @Test
    fun `小时时段按当前小时归集且总量相等`() {
        val s = song("1")
        StatsStore.recordListen(s, 45_000, 1_000)
        val day = StatsStore.snapshot().day(StatsStore.todayKey())!!
        assertEquals(StatsStore.HOURS_PER_DAY, day.hourMs.size)
        val currentHour = StatsStore.hourOf(System.currentTimeMillis())
        assertEquals(45_000L, day.hourMs[currentHour])
        assertEquals(day.listenMs, day.hourMs.sum())
    }

    @Test
    fun `多首歌分别累计并排行`() {
        val a = song("a", title = "A")
        val b = song("b", title = "B")
        StatsStore.recordPlay(a)
        StatsStore.recordPlay(a)
        StatsStore.recordPlay(b)
        StatsStore.recordListen(a, 10_000, 0)
        StatsStore.recordListen(b, 99_000, 0)

        val stats = StatsStore.snapshot()
        assertEquals(3L, stats.totalPlays)
        assertEquals(109_000L, stats.totalListenMs)
        // 排行按次数：A(2 次) 在 B(1 次) 前面，即使 B 时长更长
        assertEquals(listOf("A", "B"), stats.topSongs.map { it.displayTitle })
        assertEquals(2, stats.day(StatsStore.todayKey())!!.songs.size)
    }

    @Test
    fun `零或负数增量被忽略`() {
        val s = song("1")
        StatsStore.recordListen(s, 0, 0)
        StatsStore.recordListen(s, -5, 0)
        assertTrue(StatsStore.snapshot().isEmpty)
    }

    @Test
    fun `清空后所有数据归零`() {
        StatsStore.recordPlay(song("1"))
        StatsStore.recordListen(song("1"), 5_000, 1_000)
        StatsStore.clearAll()
        val stats = StatsStore.snapshot()
        assertTrue(stats.isEmpty)
        assertEquals(0L, stats.totalListenMs)
        assertEquals(0L, stats.totalPlays)
        assertTrue(stats.topSongs.isEmpty())
        assertTrue(stats.days.isEmpty())
    }

    @Test
    fun `本地概要数据与明细一致`() {
        val s = song("1")
        StatsStore.recordPlay(s)
        StatsStore.recordListen(s, 30_000, 0)
        val local = StatsStore.load()
        assertEquals(30_000L, local.totalPlayMs)
        assertEquals(1L, local.totalPlays)
        assertEquals(30_000L, local.dayPlayMs)
        assertEquals(StatsStore.todayKey(), local.day)
    }

    // ===== 序列化往返 =====

    @Test
    fun `序列化与反序列化保持全部字段`() {
        // 日期必须取「今天」：serialize 会走 prune()，而小时明细只保留最近 31 天、
        // 当日歌曲只保留最近 21 天。此前这里写死 "2026-09-13"，
        // 时间一过窗口这两个断言就会失败（测试随日历腐烂，而不是代码坏了）。
        val today = StatsStore.todayKey()
        val stats = ListeningStats(
            totalListenMs = 12_345_678L,
            totalPlays = 42L,
            firstDay = "2026-08-01",
            lastAtMs = 1_700_000_000_000L,
            days = listOf(
                DayStat(
                    day = today,
                    listenMs = 600_000L,
                    plays = 3,
                    hourMs = List(24) { if (it == 23) 600_000L else 0L },
                    songs = listOf(
                        SongStat(
                            platform = "netease",
                            id = "123",
                            title = "夜曲",
                            artist = "周杰伦",
                            plays = 3,
                            listenMs = 600_000L,
                            lastAtMs = 1_700_000_000_000L,
                            lastPositionMs = 49_000L,
                        )
                    ),
                )
            ),
            topSongs = listOf(
                SongStat("netease", "123", "夜曲", "周杰伦", 3, 600_000L, 1_700_000_000_000L, 49_000L)
            ),
        )
        val json = StatsStore.serializeForTest(stats)
        val parsed = StatsStore.parseForTest(json)

        assertEquals(stats.totalListenMs, parsed.totalListenMs)
        assertEquals(stats.totalPlays, parsed.totalPlays)
        assertEquals(stats.firstDay, parsed.firstDay)
        assertEquals(stats.lastAtMs, parsed.lastAtMs)
        val day = parsed.day(today)!!
        assertEquals(600_000L, day.listenMs)
        assertEquals(3, day.plays)
        assertEquals(24, day.hourMs.size)
        assertEquals(600_000L, day.hourMs[23])
        assertEquals("夜曲", day.songs.single().displayTitle)
        assertEquals(49_000L, day.songs.single().lastPositionMs)
        assertEquals("netease:123", parsed.topSongs.single().key)
    }

    @Test
    fun `歌名含引号与换行也能往返`() {
        val title = "他说\"你好\"\n第二行"
        // 同「序列化与反序列化保持全部字段」：日期取今天，避免被 prune() 的窗口裁掉
        val today = StatsStore.todayKey()
        val stats = ListeningStats(
            totalPlays = 1,
            days = listOf(DayStat(today, 1_000, 1, emptyList(), listOf(
                SongStat("qq", "9", title, "某歌手", 1, 1_000, 0, 0)
            ))),
        )
        val parsed = StatsStore.parseForTest(StatsStore.serializeForTest(stats))
        assertEquals(title, parsed.day(today)!!.songs.single().title)
    }

    @Test
    fun `脏数据只丢条目不抛异常`() {
        val parsed = StatsStore.parseForTest(
            """{"v":2,"totalMs":100,"days":[{"ms":5},{"d":"2026-09-13","ms":7,"n":2}],
                "hours":[{"d":"2026-09-13","h":[1,2,3]}],
                "daySongs":[{"d":"2026-09-13","s":[{"t":"没有平台和 id"}]}],
                "songs":[{"p":"netease","i":"1","n":"不是数字"}]}"""
        )
        assertEquals(100L, parsed.totalListenMs)
        // 没有日期的条目被丢弃
        assertEquals(listOf("2026-09-13"), parsed.days.map { it.day })
        // 小时数组长度不足时补零到 24
        assertEquals(24, parsed.days.single().hourMs.size)
        assertEquals(1L, parsed.days.single().hourMs[0])
        assertEquals(0L, parsed.days.single().hourMs[23])
        // 缺平台/id 的歌曲被丢弃
        assertTrue(parsed.days.single().songs.isEmpty())
        // 数字字段类型不对时回落 0，而不是崩
        assertEquals(0, parsed.topSongs.single().plays)
    }

    @Test
    fun `缺字段的 JSON 得到空统计`() {
        val parsed = StatsStore.parseForTest("{}")
        assertTrue(parsed.isEmpty)
        assertEquals("", parsed.firstDay)
    }

    // ===== 保留窗口 =====

    @Test
    fun `超出保留窗口的天被裁剪`() {
        val today = StatsStore.todayKey()
        fun d(offset: Int) = DayStat(StatsStore.shiftDay(today, -offset), 1_000, 1)
        // 窗口是 180 天（含今天），因此临界点为 -179：-179 保留、-180 丢弃
        val pruned = StatsStore.pruneForTest(
            ListeningStats(days = listOf(d(200), d(180), d(179), d(100), d(0)))
        )
        assertEquals(
            listOf(StatsStore.shiftDay(today, -179), StatsStore.shiftDay(today, -100), today),
            pruned.days.map { it.day },
        )
    }

    @Test
    fun `小时明细与单日歌曲按各自窗口裁剪`() {
        val today = StatsStore.todayKey()
        val hours = List(24) { 1L }
        val songs = List(10) { SongStat("p", "i$it", "t$it", "a", 1, 1_000, 0, 0) }
        val stats = ListeningStats(
            days = listOf(
                // 40 天前：小时（31 天）与歌曲（21 天）明细都已过期
                DayStat(StatsStore.shiftDay(today, -40), 1_000, 1, hours, songs),
                // 25 天前：小时还在窗口内，歌曲已过期
                DayStat(StatsStore.shiftDay(today, -25), 1_000, 1, hours, songs),
                // 5 天前：两者都保留，歌曲按每日上限截断
                DayStat(StatsStore.shiftDay(today, -5), 1_000, 1, hours, songs),
            )
        )
        val pruned = StatsStore.pruneForTest(stats)
        val old = pruned.days.first()
        val mid = pruned.days[1]
        val recent = pruned.days.last()

        assertTrue(old.hourMs.isEmpty())
        assertTrue(old.songs.isEmpty())

        assertEquals(24, mid.hourMs.size)
        assertTrue(mid.songs.isEmpty())

        assertEquals(24, recent.hourMs.size)
        assertEquals(8, recent.songs.size)
    }

    @Test
    fun `全期排行条数有上限`() {
        val songs = List(80) { SongStat("p", "i$it", "t$it", "a", 80 - it, 1_000, 0, 0) }
        val pruned = StatsStore.pruneForTest(ListeningStats(topSongs = songs))
        assertEquals(60, pruned.topSongs.size)
        // 裁剪后仍按次数降序（前 60 名里第一名次数最多）
        assertEquals(80, pruned.topSongs.first().plays)
    }

    // ===== 旧数据迁移 =====

    @Test
    fun `旧版扁平统计迁移后总数不丢`() {
        val stats = StatsStore.migrateLegacyJson(
            """{"totalPlayMs":3600000,"totalPlays":12,"dayPlayMs":600000,"day":"2026-09-13"}""",
            today = "2026-09-13",
        )
        assertEquals(3_600_000L, stats.totalListenMs)
        assertEquals(12L, stats.totalPlays)
        assertEquals("2026-09-13", stats.firstDay)
        assertEquals(600_000L, stats.day("2026-09-13")!!.listenMs)
        // 历史逐日明细无法还原，如实留空（不编造）
        assertTrue(stats.topSongs.isEmpty())
    }

    @Test
    fun `迁移时未来日期被纠正为今天`() {
        val stats = StatsStore.migrateLegacyJson(
            """{"totalPlayMs":1000,"totalPlays":1,"dayPlayMs":500,"day":"2026-12-31"}""",
            today = "2026-09-13",
        )
        assertEquals("2026-09-13", stats.days.single().day)
        assertEquals("2026-09-13", stats.firstDay)
    }

    @Test
    fun `迁移遇到空数据或坏 JSON 返回空`() {
        assertTrue(StatsStore.migrateLegacyJson(null, "2026-09-13").isEmpty)
        assertTrue(StatsStore.migrateLegacyJson("", "2026-09-13").isEmpty)
        assertTrue(StatsStore.migrateLegacyJson("坏数据", "2026-09-13").isEmpty)
        assertTrue(StatsStore.migrateLegacyJson("""{"totalPlayMs":0,"totalPlays":0}""", "2026-09-13").isEmpty)
    }

    @Test
    fun `日期加减跨月正确`() {
        assertEquals("2026-09-01", StatsStore.shiftDay("2026-09-13", -12))
        assertEquals("2026-08-31", StatsStore.shiftDay("2026-09-01", -1))
        assertEquals("2027-01-01", StatsStore.shiftDay("2026-12-31", 1))
        assertEquals("坏数据", StatsStore.shiftDay("坏数据", -1))
    }
}
