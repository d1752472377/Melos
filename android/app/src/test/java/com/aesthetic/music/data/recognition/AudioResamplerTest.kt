package com.aesthetic.music.data.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AudioResampler] 纯计算层测试。
 *
 * 它不碰 Android API（只用 Math），可以直接在 JVM 上跑，所以这里覆盖三件事：
 * 输出长度对得上、正弦波降采样后仍是同频正弦、混叠被低通压下去。
 */
class AudioResamplerTest {

    private val targetRate = 8000

    @Test
    fun `44k1 输入十秒后输出八万个采样点`() {
        val resampler = AudioResampler(44100, seconds = 10)
        // 喂满 10 秒的输入
        repeat(44100 * 10) { resampler.add(0.0) }
        assertEquals(80000, resampler.count)
        assertTrue(resampler.isFull)
        assertEquals(160000, resampler.toPcmBytes().size)
    }

    @Test
    fun `8k 直通输入不做滤波且长度一致`() {
        val resampler = AudioResampler(8000, seconds = 1)
        val samples = 8000
        repeat(samples) { i ->
            resampler.add(Math.sin(2 * Math.PI * 440 * i / 8000.0))
        }
        assertEquals(samples, resampler.count)
        // 直通路径下输出应当和输入波形一致（幅度略有量化误差）
        for (i in 0 until samples step 97) {
            val expected = Math.sin(2 * Math.PI * 440 * i / 8000.0)
            val actual = resampler.samples[i] / 32768.0
            assertTrue("index $i: $actual vs $expected", Math.abs(actual - expected) < 0.01)
        }
    }

    @Test
    fun `44k1 的 440Hz 正弦降采样后仍是 440Hz`() {
        val resampler = AudioResampler(44100, seconds = 1)
        repeat(44100) { i -> resampler.add(Math.sin(2 * Math.PI * 440 * i / 44100.0)) }
        assertEquals(8000, resampler.count)

        // 过零点计数：440Hz 在 1 秒内应有约 880 次过零（每个周期两次）
        var crossings = 0
        var previous = resampler.samples[0]
        for (i in 1 until resampler.count) {
            val current = resampler.samples[i]
            if ((previous < 0 && current >= 0) || (previous >= 0 && current < 0)) crossings++
            previous = current
        }
        assertTrue("过零次数 $crossings 偏离 440Hz 预期", crossings in 820..940)
    }

    @Test
    fun `超出截止频率的高频被低通压下去`() {
        // 6 kHz 在 8 kHz 目标下会混叠；低通后幅度应当明显小于输入的 1.0
        val resampler = AudioResampler(44100, seconds = 1)
        repeat(44100) { i -> resampler.add(Math.sin(2 * Math.PI * 6000 * i / 44100.0)) }

        var peak = 0.0
        for (i in 0 until resampler.count) {
            peak = maxOf(peak, Math.abs(resampler.samples[i] / 32768.0))
        }
        assertTrue("6kHz 残留幅度 $peak 过大，低通没起效", peak < 0.35)
    }

    @Test
    fun `静音输入的 rms 为零`() {
        val resampler = AudioResampler(44100, seconds = 1)
        repeat(44100) { resampler.add(0.0) }
        assertEquals(0.0, resampler.rms(), 1e-9)
    }

    @Test
    fun `非有限值被当成静音而不是污染输出`() {
        val resampler = AudioResampler(8000, seconds = 1)
        resampler.add(Double.NaN)
        resampler.add(Double.POSITIVE_INFINITY)
        resampler.add(0.5)
        // 前两个点写进历史后应被替换成 0，第三个正常通过
        assertEquals(3, resampler.count)
        assertEquals(0, resampler.samples[0].toInt())
        assertEquals(0, resampler.samples[1].toInt())
        assertTrue(
            "0.5 应量化到约 16383，实际 ${resampler.samples[2]}",
            Math.abs(resampler.samples[2].toInt() - 16383) <= 2,
        )
    }

    @Test
    fun `缓冲满了之后再喂数据不会越界`() {
        val resampler = AudioResampler(8000, seconds = 1)
        repeat(8000 * 3) { i -> resampler.add(Math.sin(i.toDouble())) }
        assertEquals(8000, resampler.count)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `采样率低于目标值时直接拒绝`() {
        AudioResampler(4000)
    }
}
