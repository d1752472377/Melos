package com.aesthetic.music.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SourceStatsStore] 的检测结论与播放实测样本测试。
 *
 * "自动最快"与"智能切换"都建立在这份数据上，因此这里重点验证两件事：
 * 1. 播放成功样本做指数滑动平均（避免一次抖动就换源）；
 * 2. 播放失败**不会**污染耗时均值（否则一个坏源会显得很快而被优先选中）。
 */
class SourceStatsStoreTest {

    private val target = SourceProbe.pluginTarget("p1", SourcePlatform.NETEASE)

    @Before
    fun setUp() {
        SourceStatsStore.clear()
    }

    @Test
    fun `保存检测结论后可以读回`() {
        SourceStatsStore.save(
            listOf(
                SourceProbe(target = target, status = ProbeStatus.OK, latencyMs = 240, detail = "命中 5 首"),
            )
        )
        val probe = SourceStatsStore.get(target)
        assertNotNull(probe)
        assertEquals(ProbeStatus.OK, probe!!.status)
        assertEquals(240L, probe.latencyMs)
        assertEquals("命中 5 首", probe.detail)
        assertTrue(probe.checkedAt > 0)
    }

    @Test
    fun `播放成功样本写入滑动平均`() {
        SourceStatsStore.recordObserved(target, 1000, ok = true)
        assertEquals(1000L, SourceStatsStore.get(target)!!.observedMs)

        SourceStatsStore.recordObserved(target, 100, ok = true)
        // 1000 * 0.65 + 100 * 0.35 = 685
        assertEquals(685L, SourceStatsStore.get(target)!!.observedMs)
        assertEquals(2, SourceStatsStore.get(target)!!.observedCount)
    }

    @Test
    fun `播放失败样本不写入耗时均值`() {
        SourceStatsStore.recordObserved(target, 800, ok = true)
        SourceStatsStore.recordObserved(target, 10, ok = false, failReason = "未取到直链")
        val probe = SourceStatsStore.get(target)!!
        assertEquals(800L, probe.observedMs)
        assertEquals(1, probe.observedCount)
        assertEquals(ProbeStatus.FAIL, probe.status)
        assertEquals("未取到直链", probe.detail)
    }

    @Test
    fun `播放成功后失败状态会被纠正`() {
        SourceStatsStore.recordObserved(target, 500, ok = false)
        assertEquals(ProbeStatus.FAIL, SourceStatsStore.get(target)!!.status)
        SourceStatsStore.recordObserved(target, 300, ok = true)
        assertEquals(ProbeStatus.OK, SourceStatsStore.get(target)!!.status)
        assertEquals(300L, SourceStatsStore.get(target)!!.observedMs)
    }

    @Test
    fun `用户已有的检测结论不会被播放实测覆盖`() {
        SourceStatsStore.save(
            listOf(SourceProbe(target = target, status = ProbeStatus.SLOW, latencyMs = 5000))
        )
        SourceStatsStore.recordObserved(target, 120, ok = true)
        val probe = SourceStatsStore.get(target)!!
        assertEquals(ProbeStatus.SLOW, probe.status)
        assertEquals(5000L, probe.latencyMs)
        assertEquals(120L, probe.observedMs)
    }

    @Test
    fun `负耗时不会被记录`() {
        SourceStatsStore.recordObserved(target, -1, ok = true)
        assertNull(SourceStatsStore.get(target))
    }

    @Test
    fun `多条结论各自独立保存`() {
        val other = SourceProbe.pluginTarget("p2", SourcePlatform.QQ)
        SourceStatsStore.save(
            listOf(
                SourceProbe(target = target, status = ProbeStatus.OK, latencyMs = 100),
                SourceProbe(target = other, status = ProbeStatus.FAIL, latencyMs = 900),
            )
        )
        assertEquals(ProbeStatus.OK, SourceStatsStore.get(target)!!.status)
        assertEquals(ProbeStatus.FAIL, SourceStatsStore.get(other)!!.status)
        assertEquals(2, SourceStatsStore.all().size)
    }

    @Test
    fun `clear 会清空全部结论`() {
        SourceStatsStore.save(listOf(SourceProbe(target = target, status = ProbeStatus.OK, latencyMs = 100)))
        SourceStatsStore.clear()
        assertTrue(SourceStatsStore.all().isEmpty())
        assertNull(SourceStatsStore.get(target))
    }
}
