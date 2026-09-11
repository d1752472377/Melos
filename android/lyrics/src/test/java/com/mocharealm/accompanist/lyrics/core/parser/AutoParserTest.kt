package com.mocharealm.accompanist.lyrics.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 格式自动识别（AutoParser）单元测试。
 *
 * AutoParser 按固定顺序遍历已注册解析器并取第一个 `canParse` 命中的，
 * 因此**顺序本身就是契约**：TTML 必须早于其它解析器，否则含 `[00:00.00]` 的
 * TTML 内容会被 EnhancedLrcParser 抢先认领，导致逐字信息整体丢失。
 */
class AutoParserTest {

    private val auto = AutoParser()

    private val plainLrc = "[00:12.34]Hello\n[00:15.00]World"
    private val yrc = "[12580,3470](12580,250,0)难(12830,300,0)以"
    private val ttml = """<?xml version="1.0"?><tt xmlns="http://www.w3.org/ns/ttml"></tt>"""

    @Test
    fun `识别普通 LRC 并委派给 EnhancedLrcParser`() {
        assertTrue(auto.canParse(plainLrc))
        assertEquals(
            EnhancedLrcParser.parse(plainLrc).lines.size,
            auto.parse(plainLrc).lines.size,
        )
        assertEquals(2, auto.parse(plainLrc).lines.size)
    }

    @Test
    fun `识别 YRC 并委派给 NeteaseYrcParser`() {
        assertTrue(auto.canParse(yrc))
        assertEquals(
            NeteaseYrcParser.parse(yrc).lines.size,
            auto.parse(yrc).lines.size,
        )
        assertEquals(1, auto.parse(yrc).lines.size)
    }

    @Test
    fun `TTML 优先于其它格式被识别`() {
        // TTML 内容里也常出现 LRC 风格时间戳，必须靠顺序保证不被抢先解析
        assertTrue(auto.canParse(ttml))
    }

    @Test
    fun `无法识别的内容 canParse 为 false 且解析出空行列表`() {
        val junk = "这是一段没有任何格式标记的文本"
        assertFalse(auto.canParse(junk))
        assertEquals(0, auto.parse(junk).lines.size)
    }

    @Test
    fun `空输入安全返回空行列表`() {
        assertFalse(auto.canParse(""))
        assertEquals(0, auto.parse("").lines.size)
    }
}
