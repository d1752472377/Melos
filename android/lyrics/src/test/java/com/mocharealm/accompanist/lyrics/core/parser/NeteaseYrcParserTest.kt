package com.mocharealm.accompanist.lyrics.core.parser

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网易云 YRC 格式解析器单元测试。
 *
 * YRC 有两种时间语义：绝对时间（音节时间戳已含行起始偏移）与相对时间
 * （音节时间戳是相对行起始的偏移量）。两种语义的判定完全依赖
 * 「首音节起点 < 行起点」这一条件，是典型的容易在重构中被写反的逻辑。
 */
class NeteaseYrcParserTest {

    private val yrcLine = "[12580,3470](12580,250,0)难(12830,300,0)以"

    @Test
    fun `canParse 识别 YRC 行格式`() {
        assertTrue(NeteaseYrcParser.canParse(yrcLine))
    }

    @Test
    fun `canParse 拒绝普通 LRC 与纯文本`() {
        assertFalse(NeteaseYrcParser.canParse("[00:12.34]Hello"))
        assertFalse(NeteaseYrcParser.canParse("just plain text"))
    }

    @Test
    fun `绝对时间语义下音节时间戳原样保留`() {
        val lyrics = NeteaseYrcParser.parse(yrcLine)

        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0] as KaraokeLine.MainKaraokeLine

        assertEquals(2, line.syllables.size)
        assertEquals("难", line.syllables[0].content)
        assertEquals(12_580, line.syllables[0].start)
        assertEquals(12_830, line.syllables[0].end)

        assertEquals("以", line.syllables[1].content)
        assertEquals(12_830, line.syllables[1].start)
        assertEquals(13_130, line.syllables[1].end)

        // 行区间：起点取首音节，终点取「行声明的结束」与「末音节结束」的较大值
        assertEquals(12_580, line.start)
        assertEquals(16_050, line.end)
    }

    @Test
    fun `相对时间语义下音节时间戳叠加行起点`() {
        // 音节时间戳 (100,250) 明显小于行起点 10000 → 判定为相对时间
        val relative = "[10000,3000](100,250,0)甲(350,300,0)乙"
        val line = NeteaseYrcParser.parse(relative).lines[0] as KaraokeLine.MainKaraokeLine

        assertEquals(10_100, line.syllables[0].start)
        assertEquals(10_350, line.syllables[0].end)
        assertEquals(10_350, line.syllables[1].start)
        assertEquals(10_650, line.syllables[1].end)
    }

    @Test
    fun `无音节标记的行降级为普通同步行`() {
        val lyrics = NeteaseYrcParser.parse("[1000,2000]纯文本")
        assertEquals(1, lyrics.lines.size)

        val line = lyrics.lines[0] as SyncedLine
        assertEquals("纯文本", line.content)
        assertEquals(1_000, line.start)
        assertEquals(3_000, line.end)
    }

    @Test
    fun `JSON 署名行被忽略`() {
        val content = "{\"t\":0,\"c\":[{\"tx\":\"作词\"}]}\n$yrcLine"
        val lyrics = NeteaseYrcParser.parse(content)
        assertEquals(1, lyrics.lines.size)
    }

    @Test
    fun `脏数据返回空行列表而不抛异常`() {
        assertEquals(0, NeteaseYrcParser.parse("").lines.size)
        assertEquals(0, NeteaseYrcParser.parse("[abc,def]乱码").lines.size)
    }
}
