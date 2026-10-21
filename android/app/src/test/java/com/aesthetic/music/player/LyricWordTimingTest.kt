package com.aesthetic.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 逐字歌词单测：YRC 解析、字时间贴合、逐字染色占比、元数据过滤。
 *
 * 核心约定（用户明确要求）：**只认真实返回的逐字数据**。
 * 有 YRC 就逐字，没有就逐句——不再用「按字数估算」的假字时间，
 * 因为估算的吐字速度与真实演唱对不上（一句唱完了染色才走一半）。
 */
class LyricWordTimingTest {

    // ==================== YRC 解析 ====================

    /** 网易云 YRC 的真实片段（`/api/song/lyric?yv=-1`，字时间是绝对毫秒） */
    private val yrcSample = """
        [0,0](0,0,0) 作词 : 唐恬
        [58740,5690](58740,270,0)他(59010,150,0)们(59160,740,0)说
        [65930,3050](65930,200,0)为(66130,250,0)何(66380,230,0)孤(66610,640,0)独
    """.trimIndent()

    @Test
    fun `YRC 解析出绝对毫秒的字时间`() {
        val lines = parseYrc(yrcSample)
        assertEquals(2, lines.size) // 「作词」行被元数据过滤掉
        assertEquals(58740L, lines[0].timeMs)
        assertEquals(5690L, lines[0].durationMs)
        assertEquals("他们说", lines[0].text)
        assertEquals(3, lines[0].words.size)
        assertEquals(58740L, lines[0].words[0].startMs)
        assertEquals(59010L, lines[0].words[0].endMs)
        assertEquals(59010L, lines[0].words[1].startMs)
    }

    @Test
    fun `YRC 空内容返回空列表`() {
        assertTrue(parseYrc(null).isEmpty())
        assertTrue(parseYrc("").isEmpty())
        assertTrue(parseYrc("不是 YRC 格式").isEmpty())
    }

    // ==================== 字时间贴合到 LRC 文本 ====================

    @Test
    fun `字时间按行起始贴到 LRC 上`() {
        val lrc = """
            [00:58.740]他们说
            [01:05.930]为何孤独
        """.trimIndent()
        val base = parseLrc(lrc)
        assertEquals(2, base.size)
        assertFalse(base[0].isWordByWord)

        val merged = attachWordTimings(base, yrcSample)
        assertTrue(merged[0].isWordByWord)
        assertEquals(3, merged[0].words.size)
        // 文本仍来自 LRC（YRC 的文本会把标点拆到单独的字里）
        assertEquals("他们说", merged[0].text)
        // 第二行同样贴上
        assertTrue(merged[1].isWordByWord)
        assertEquals(4, merged[1].words.size)
    }

    @Test
    fun `时间对不上的行不硬贴`() {
        val base = parseLrc("[00:10.000]完全不同的行")
        val merged = attachWordTimings(base, yrcSample)
        assertFalse(merged[0].isWordByWord)
    }

    @Test
    fun `没有 YRC 时整首保持逐句`() {
        val base = parseLrc(
            """
            [00:01.000]第一句歌词
            [00:05.000]第二句歌词
            """.trimIndent()
        )
        // null / 空串 / 非 YRC 内容都不应产生任何字时间
        assertEquals(base, attachWordTimings(base, null))
        assertEquals(base, attachWordTimings(base, "  "))
        assertEquals(base, attachWordTimings(base, "不是 YRC 的内容"))
        assertTrue(base.none { it.isWordByWord })
    }

    @Test
    fun `只贴时间匹配得上的行，其余行仍是逐句`() {
        val lrc = """
            [00:58.740]他们说
            [00:30.000]这一行没有字时间
        """.trimIndent()
        val merged = attachWordTimings(parseLrc(lrc), yrcSample)
        val byText = merged.associateBy { it.text }
        assertTrue(byText.getValue("他们说").isWordByWord)
        assertFalse(byText.getValue("这一行没有字时间").isWordByWord)
    }

    // ==================== 逐字染色占比 ====================

    @Test
    fun `染色占比随播放推进`() {
        val line = attachWordTimings(parseLrc("[00:58.740]他们说"), yrcSample)[0]
        val start = line.words.first().startMs
        val end = line.words.last().endMs

        assertEquals(0f, karaokeSweepFraction(line, start - 100), 0.001f)
        assertEquals(1f, karaokeSweepFraction(line, end + 100), 0.001f)
        val mid = karaokeSweepFraction(line, (start + end) / 2)
        assertTrue("mid=$mid", mid > 0.2f && mid < 0.9f)
    }

    @Test
    fun `逐句歌词没有字时间因此染色恒为 0`() {
        val line = parseLrc("[00:01.000]只有行时间的一句歌词")[0]
        assertFalse(line.isWordByWord)
        assertEquals(0f, karaokeSweepFraction(line, 3_000L), 0.001f)
        assertEquals(0f, karaokeSweepFraction(line, 60_000L), 0.001f)
    }

    @Test
    fun `染色占比单调不减`() {
        val line = attachWordTimings(parseLrc("[00:58.740]他们说"), yrcSample)[0]
        var last = -1f
        var t = line.timeMs
        while (t <= line.timeMs + 6_000L) {
            val sweep = karaokeSweepFraction(line, t)
            assertTrue(sweep >= last)
            last = sweep
            t += 100L
        }
    }

    // ==================== 元数据过滤 ====================

    @Test
    fun `制作人信息被过滤`() {
        assertTrue(MetadataFilter.isMetadataLine("作词 : 唐恬"))
        assertTrue(MetadataFilter.isMetadataLine("编曲：钱雷"))
        assertTrue(MetadataFilter.isMetadataLine("OP：某某音乐"))
        assertTrue(MetadataFilter.isMetadataLine("SP: 某某代理"))
        assertTrue(MetadataFilter.isMetadataLine("   "))
    }

    @Test
    fun `以 Op 或 Sp 开头的英文歌词不会被误杀`() {
        // 曾经的 bug：OP / SP 用 startsWith 匹配，把 Open / Spend 开头的歌词整行吃掉
        assertFalse(MetadataFilter.isMetadataLine("Open your eyes"))
        assertFalse(MetadataFilter.isMetadataLine("Spend my life with you"))
        assertFalse(MetadataFilter.isMetadataLine("Speaking in tongues"))
    }

    @Test
    fun `普通歌词行不算元数据`() {
        assertFalse(MetadataFilter.isMetadataLine("沿着你的听歌足迹"))
        assertFalse(MetadataFilter.isMetadataLine("Don't Start Now"))
    }
}
