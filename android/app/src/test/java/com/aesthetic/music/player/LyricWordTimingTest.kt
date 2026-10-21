package com.aesthetic.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 逐字歌词单测：YRC 解析、字时间贴合、近似字时间、逐字染色占比、元数据过滤。
 *
 * 这几条都是「不写测试就永远发现不了」的规则：
 * 逐字染色算错不会崩，只会让颜色和歌声对不上——用户体感是「歌词怪怪的」，
 * 但没人能一句话说清哪里怪。
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
    fun `没有 YRC 时原样返回`() {
        val base = parseLrc("[00:01.000]一行歌词")
        assertEquals(base, attachWordTimings(base, null))
        assertEquals(base, attachWordTimings(base, "  "))
    }

    // ==================== 近似字时间 ====================

    @Test
    fun `纯 LRC 也能补出近似字时间`() {
        val base = parseLrc(
            """
            [00:10.000]这是一句歌词
            [00:13.000]这是下一句
            """.trimIndent()
        )
        val out = withPseudoWords(base)
        assertEquals(2, out.size)
        assertTrue(out[0].isWordByWord)
        // 逐字切分：中文一行 6 个字
        assertEquals(6, out[0].words.size)
        // 字时间连续且落在这一行内
        assertEquals(10_000L, out[0].words.first().startMs)
        assertTrue(out[0].words.last().endMs <= 13_000L)
        for (i in 1 until out[0].words.size) {
            assertEquals(out[0].words[i - 1].endMs, out[0].words[i].startMs)
        }
    }

    @Test
    fun `英文按词切分不拆散单词`() {
        val base = parseLrc("[00:01.000]Don't Start Now")
        val out = withPseudoWords(base)
        val texts = out[0].words.map { it.text.trim() }
        assertEquals(listOf("Don't", "Start", "Now"), texts)
    }

    @Test
    fun `已有真字时间的行不被覆盖`() {
        val base = parseLrc(
            """
            [00:58.740]他们说
            [01:05.930]为何孤独
            """.trimIndent()
        )
        val withReal = attachWordTimings(base, yrcSample)
        val out = withPseudoWords(withReal)
        assertEquals(withReal[0].words, out[0].words)
    }

    @Test
    fun `行时长被夹在合理区间`() {
        // 两句之间隔了 60 秒（间奏）：不能把最后一个字算成唱 60 秒
        val base = parseLrc(
            """
            [00:01.000]第一句
            [01:01.000]第二句
            """.trimIndent()
        )
        val out = withPseudoWords(base)
        val span = out[0].words.last().endMs - out[0].words.first().startMs
        assertTrue("span=$span", span <= 9_000L)
        assertTrue("span=$span", span >= 600L)
    }

    @Test
    fun `近似时长按字数估算而不是按行间隔`() {
        // 用户反馈的场景：10 个英文词两秒唱完，但到下一句还有 4 秒。
        // 旧实现按「行间隔」铺时长 → 染色走到一半这句就换了。
        val base = parseLrc(
            """
            [00:10.000]one two three four five six seven eight nine ten
            [00:14.000]next line here
            """.trimIndent()
        )
        val out = withPseudoWords(base)
        val span = out[0].words.last().endMs - out[0].words.first().startMs
        assertTrue("10 个词应约 2 秒，实际 $span ms", span in 1_200L..3_000L)
        // 10 个词都拿到了时间，且首尾相接
        assertEquals(10, out[0].words.size)
        for (i in 1 until out[0].words.size) {
            assertEquals(out[0].words[i - 1].endMs, out[0].words[i].startMs)
        }
    }

    @Test
    fun `中文按每字约一百七十毫秒估算`() {
        val base = parseLrc(
            """
            [00:10.000]这是一句十个字左右的歌词
            [00:20.000]下一句
            """.trimIndent()
        )
        val out = withPseudoWords(base)
        val span = out[0].words.last().endMs - out[0].words.first().startMs
        val chars = out[0].words.size
        assertTrue("chars=$chars span=$span", span in (chars * 120L)..(chars * 240L))
    }

    // ==================== 逐字染色占比 ====================

    @Test
    fun `染色占比随播放推进`() {
        val line = withPseudoWords(parseLrc("[00:10.000]一二三四"))[0]
        val start = line.words.first().startMs
        val end = line.words.last().endMs

        assertEquals(0f, karaokeSweepFraction(line, start - 100), 0.001f)
        assertEquals(1f, karaokeSweepFraction(line, end + 100), 0.001f)
        val mid = karaokeSweepFraction(line, (start + end) / 2)
        assertTrue("mid=$mid", mid > 0.3f && mid < 0.7f)
    }

    @Test
    fun `没有字时间的行染色为 0`() {
        val line = LyricLine(timeMs = 1_000L, text = "没有字时间")
        assertEquals(0f, karaokeSweepFraction(line, 5_000L), 0.001f)
    }

    @Test
    fun `时间占比与字数占比都单调不减`() {
        val line = withPseudoWords(parseLrc("[00:00.000]一二三四五六七八"))[0]
        var lastSweep = -1f
        var lastProgress = -1f
        var t = line.timeMs
        while (t <= line.timeMs + 8_000L) {
            val sweep = karaokeSweepFraction(line, t)
            val progress = karaokeProgress(line, t)
            assertTrue(sweep >= lastSweep)
            assertTrue(progress >= lastProgress)
            lastSweep = sweep
            lastProgress = progress
            t += 200L
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
