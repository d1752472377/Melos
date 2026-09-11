package com.mocharealm.accompanist.lyrics.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 时间戳解析与格式化的单元测试。
 *
 * `parseAsTime` 是全部歌词解析器的公共底座：LRC / YRC / KRC / TTML 四种格式的
 * 时间字段最终都汇聚到这里。它此前是被内联在各解析器里的字符串拼接逻辑，
 * 收敛成单一实现后必须锁住行为边界，避免后续"优化"时静默改变时间轴。
 */
class TimeUtilsTest {

    @Test
    fun `parseAsTime 空串返回 0`() {
        assertEquals(0, "".parseAsTime())
    }

    @Test
    fun `parseAsTime 纯秒数按秒解释`() {
        assertEquals(12_000, "12".parseAsTime())
        assertEquals(0, "0".parseAsTime())
    }

    @Test
    fun `parseAsTime 小数部分按位数缩放`() {
        // ".1" -> 100ms，".12" -> 120ms，".123" -> 123ms（左对齐补零语义）
        assertEquals(12_100, "12.1".parseAsTime())
        assertEquals(12_120, "12.12".parseAsTime())
        assertEquals(12_123, "12.123".parseAsTime())
    }

    @Test
    fun `parseAsTime 超过三位的小数只取前三位`() {
        assertEquals(12_123, "12.1234".parseAsTime())
    }

    @Test
    fun `parseAsTime 冒号结尾无小数部分时忽略毫秒`() {
        assertEquals(12_000, "12.".parseAsTime())
    }

    @Test
    fun `parseAsTime MM SS ms 格式`() {
        assertEquals(83_450, "01:23.45".parseAsTime())
        assertEquals(0, "00:00.00".parseAsTime())
    }

    @Test
    fun `parseAsTime HH MM SS ms 格式`() {
        assertEquals(3_723_004, "1:02:03.004".parseAsTime())
    }

    @Test
    fun `parseAsTime 非数字字符按 0 处理而不抛异常`() {
        // 脏数据不应导致解析崩溃，而是退化为 0
        assertEquals(0, "ab:cd.ef".parseAsTime())
        assertEquals(0, ":".parseAsTime())
    }

    @Test
    fun `toTimeFormattedString 零填充到 MM SS mmm`() {
        assertEquals("00:00.000", 0.toTimeFormattedString())
        assertEquals("01:23.450", 83_450.toTimeFormattedString())
        assertEquals("00:00.005", 5.toTimeFormattedString())
        assertEquals("10:00.000", 600_000.toTimeFormattedString())
    }

    @Test
    fun `toTimeFormattedString 负数安全退化`() {
        assertEquals("00:00.000", (-5).toTimeFormattedString())
    }

    @Test
    fun `parseAsTime 与 toTimeFormattedString 互为逆运算`() {
        val samples = listOf(0, 5, 1_000, 83_450, 600_000, 3_723_004)
        for (ms in samples) {
            assertEquals("往返失败: $ms", ms, ms.toTimeFormattedString().parseAsTime())
        }
    }
}
