package com.mocharealm.accompanist.lyrics.core.model

import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 歌词高亮索引查找单元测试。
 *
 * 这两个方法在播放过程中被每帧调用，内部是手写二分查找。手写二分最怕边界
 * 处理出错——尤其是「时间落在所有歌词之后」与「当前时间早于第一行」两种
 * 边界，出错表现是歌词高亮卡住或整段错位，但不会抛异常，因此很难在运行时发现。
 */
class SyncedLyricsTest {

    private fun lyricsOf(vararg ranges: Pair<Int, Int>): SyncedLyrics =
        SyncedLyrics(
            lines = ranges.mapIndexed { index, (start, end) ->
                SyncedLine(content = "line$index", translation = null, start = start, end = end)
            }
        )

    private val sequential = lyricsOf(0 to 1000, 1000 to 2000, 2000 to 3000)

    // ---------------- 单行高亮 ----------------

    @Test
    fun `时间落在某行区间内返回该行索引`() {
        assertEquals(0, sequential.getCurrentFirstHighlightLineIndexByTime(0))
        assertEquals(0, sequential.getCurrentFirstHighlightLineIndexByTime(500))
        assertEquals(1, sequential.getCurrentFirstHighlightLineIndexByTime(1500))
        assertEquals(2, sequential.getCurrentFirstHighlightLineIndexByTime(2500))
    }

    @Test
    fun `相邻行区间端点重叠时取靠前的行`() {
        // (0,1000) 与 (1000,2000) 在 1000 处重叠（区间为闭区间），契约是取靠前的行
        assertEquals(0, sequential.getCurrentFirstHighlightLineIndexByTime(999))
        assertEquals(0, sequential.getCurrentFirstHighlightLineIndexByTime(1000))
        // 越过 1000 后进入第二行
        assertEquals(1, sequential.getCurrentFirstHighlightLineIndexByTime(1001))
    }

    @Test
    fun `时间晚于所有歌词返回行数`() {
        assertEquals(sequential.lines.size, sequential.getCurrentFirstHighlightLineIndexByTime(99_999))
    }

    @Test
    fun `时间早于第一行时回落到首行`() {
        assertEquals(0, sequential.getCurrentFirstHighlightLineIndexByTime(-100))
    }

    @Test
    fun `空歌词返回 0 而不抛异常`() {
        val empty = SyncedLyrics(lines = emptyList())
        assertEquals(0, empty.getCurrentFirstHighlightLineIndexByTime(1234))
        assertEquals(0, empty.getCurrentAllHighlightLineIndicesByTime(1234).size)
    }

    // ---------------- 多行同时高亮（合唱 / 和声） ----------------

    private val overlapping = lyricsOf(0 to 2000, 1000 to 3000)

    @Test
    fun `重叠区间返回全部命中的行索引且保持升序`() {
        assertEquals(listOf(0, 1), overlapping.getCurrentAllHighlightLineIndicesByTime(1500))
    }

    @Test
    fun `仅命中单行时只返回该行`() {
        assertEquals(listOf(0), overlapping.getCurrentAllHighlightLineIndicesByTime(500))
        assertEquals(listOf(1), overlapping.getCurrentAllHighlightLineIndicesByTime(2500))
    }

    @Test
    fun `无命中返回空列表`() {
        assertEquals(emptyList<Int>(), overlapping.getCurrentAllHighlightLineIndicesByTime(99_999))
    }

    // ---------------- 大量行下的二分正确性 ----------------

    @Test
    fun `一千行顺序歌词中每行都能被正确定位`() {
        val many = SyncedLyrics(
            lines = (0 until 1000).map { i ->
                SyncedLine("line$i", null, i * 1000, (i + 1) * 1000)
            }
        )
        for (i in 0 until 1000) {
            assertEquals("第 $i 行定位失败", i, many.getCurrentFirstHighlightLineIndexByTime(i * 1000 + 1))
        }
    }
}
