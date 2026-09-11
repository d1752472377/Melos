package com.mocharealm.accompanist.lyrics.core.parser

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enhanced LRC 解析器单元测试。
 *
 * 该解析器承担两种输入的解析：普通 LRC（行级时间戳）与 Enhanced LRC（逐字音节、
 * 背景和声、多歌手对齐）。此前这两条路径混合在同一个方法里，且时间轴偏移、
 * 末行结束时间补齐等逻辑完全依赖运行时表现，缺少回归保护。
 */
class EnhancedLrcParserTest {

    // ---------------- 格式识别 ----------------

    @Test
    fun `canParse 识别标准 LRC 时间戳`() {
        assertTrue(EnhancedLrcParser.canParse("[00:12.34]Hello"))
        assertTrue(EnhancedLrcParser.canParse("[00:12.345]Hello"))
    }

    @Test
    fun `canParse 拒绝无时间戳的纯文本`() {
        assertFalse(EnhancedLrcParser.canParse("just plain text"))
        assertFalse(EnhancedLrcParser.canParse("[ti:标题]"))
    }

    // ---------------- 行级时间戳 ----------------

    @Test
    fun `普通 LRC 每行解析为一条 SyncedLine`() {
        val lyrics = EnhancedLrcParser.parse("[00:12.34]Hello\n[00:15.00]World")

        assertEquals(2, lyrics.lines.size)

        val first = lyrics.lines[0] as SyncedLine
        assertEquals("Hello", first.content)
        assertEquals(12_340, first.start)
        // 末行结束时间取自下一行起点，保证高亮区间连续无空隙
        assertEquals(15_000, first.end)

        val second = lyrics.lines[1] as SyncedLine
        assertEquals("World", second.content)
        assertEquals(15_000, second.start)
    }

    @Test
    fun `空行被忽略且不产生空歌词行`() {
        val lyrics = EnhancedLrcParser.parse("[00:01.00]A\n\n\n[00:02.00]B")
        assertEquals(2, lyrics.lines.size)
    }

    // ---------------- 逐字音节 ----------------

    @Test
    fun `尖括号逐字时间戳解析为音节级卡拉OK行`() {
        val lyrics = EnhancedLrcParser.parse("[00:12.34]<00:12.34>Hel<00:12.60>lo")

        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0] as KaraokeLine.MainKaraokeLine

        assertEquals(2, line.syllables.size)
        assertEquals("Hel", line.syllables[0].content)
        assertEquals(12_340, line.syllables[0].start)
        // 音节结束时间被补齐为下一个音节的起点
        assertEquals(12_600, line.syllables[0].end)

        assertEquals("lo", line.syllables[1].content)
        assertEquals(12_600, line.syllables[1].start)

        // 行的整体区间覆盖全部音节
        assertEquals(12_340, line.start)
        assertEquals(12_600, line.end)
    }

    // ---------------- 已知问题：方括号逐字格式 ----------------

    /**
     * ⚠️ 该用例锁定的是**当前实现的实际行为**，而其中一处结果很可能是 bug，不是期望行为。
     *
     * 解析器 KDoc 把 `[00:12.34][00:12.34]Hel[00:12.60]lo` 列为"支持的劣质卡拉 OK 格式"，
     * 但实现里 `parseLine` 会把**所有开头连续的方括号组**都当作行级时间戳收进 `leadingTags`，
     * 于是第一个音节的 `[00:12.34]` 被当作"第二个行时间戳"吃掉，紧跟其后的文本 `Hel` 无人认领。
     *
     * 后果：`Hel` 丢失，且因为存在两个行时间戳，同一行被展开成两条。
     *
     * 之所以不直接"修好"：该格式与 LRC 中真实存在的
     * `[00:12.34][00:15.00]同一句副歌`（一行文本配多个播放时间）在语法上无法区分——
     * 后者要求把两个时间戳都当作行级。在拿到真实音源数据前贸然改动，
     * 可能修好一种格式而弄坏另一种。
     *
     * 若确认真实数据中不存在此格式，应删掉 KDoc 中的该示例；
     * 若存在，则需要按"首个方括号为行时间戳、其余并入内容"来区分处理。
     */
    @Test
    fun `方括号逐字格式首个音节文本丢失_已知问题`() {
        val lyrics = EnhancedLrcParser.parse("[00:12.34][00:12.34]Hel[00:12.60]lo")

        // 两个行时间戳 → 同一行被展开成两条（这本身也可疑）
        assertEquals(2, lyrics.lines.size)

        val first = lyrics.lines[0] as KaraokeLine.MainKaraokeLine
        // "Hel" 丢失，只剩其后带方括号时间戳的音节
        assertEquals(1, first.syllables.size)
        assertEquals("lo", first.syllables[0].content)
        assertEquals(12_600, first.syllables[0].start)
    }

    @Test
    fun `一行文本配多个行时间戳时两处都会产出歌词行`() {
        // 这是 LRC 中真实存在的写法（副歌重复），必须保持支持：
        // 与上一个用例的输入在语法上无法区分，因此不能为修复它而破坏本行为。
        val lyrics = EnhancedLrcParser.parse("[00:12.34][00:15.00]同一句副歌")

        assertEquals(2, lyrics.lines.size)
        assertEquals("同一句副歌", (lyrics.lines[0] as SyncedLine).content)
        assertEquals(12_340, lyrics.lines[0].start)
        assertEquals("同一句副歌", (lyrics.lines[1] as SyncedLine).content)
        assertEquals(15_000, lyrics.lines[1].start)
    }

    // ---------------- 背景和声 ----------------

    @Test
    fun `bg 标签解析为伴奏行且挂在主行之后`() {
        val lyrics = EnhancedLrcParser.parse(
            "[00:12.34]Hello\n[bg:<00:12.34>Back<00:12.60>ground]"
        )

        assertEquals(2, lyrics.lines.size)
        assertEquals("Hello", (lyrics.lines[0] as SyncedLine).content)

        val bg = lyrics.lines[1] as KaraokeLine.AccompanimentKaraokeLine
        assertEquals(2, bg.syllables.size)
        assertEquals("Back", bg.syllables[0].content)
        assertEquals("ground", bg.syllables[1].content)
    }

    // ---------------- 元数据 ----------------

    @Test
    fun `元数据行被剔除并用于填充标题`() {
        val lyrics = EnhancedLrcParser.parse("[ti:Test Song]\n[ar:Artist A]\n[00:01.00]Hi")

        assertEquals("Test Song", lyrics.title)
        assertEquals(1, lyrics.lines.size)
        assertEquals("Hi", (lyrics.lines[0] as SyncedLine).content)
    }

    // ---------------- 脏数据 ----------------

    @Test
    fun `无有效内容的输入返回空行列表而不抛异常`() {
        assertEquals(0, EnhancedLrcParser.parse("").lines.size)
        assertEquals(0, EnhancedLrcParser.parse("完全没有时间戳的一段话").lines.size)
    }
}
