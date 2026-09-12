package com.aesthetic.music.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音源模型与检测结论的测试。
 *
 * 重点是**平台映射**与**结论分档**：应用平台 code（netease/qq/…）与脚本侧 LX 标识（wy/tx/…）不同名，
 * 映射错了就会出现"某个平台永远解析失败"；分档错了则"自动最快/智能切换"会拿错误的依据做决策。
 */
class SourceModelsTest {

    // ===== 平台映射 =====

    @Test
    fun `应用平台 code 与 LX 标识都能解析`() {
        assertEquals(SourcePlatform.NETEASE, SourcePlatform.of("netease"))
        assertEquals(SourcePlatform.NETEASE, SourcePlatform.of("wy"))
        assertEquals(SourcePlatform.QQ, SourcePlatform.of("qq"))
        assertEquals(SourcePlatform.QQ, SourcePlatform.of("tx"))
        assertEquals(SourcePlatform.KUGOU, SourcePlatform.of("kg"))
        assertEquals(SourcePlatform.KUWO, SourcePlatform.of("kw"))
        assertEquals(SourcePlatform.MIGU, SourcePlatform.of("mg"))
    }

    @Test
    fun `大小写与空白不会导致解析失败`() {
        assertEquals(SourcePlatform.NETEASE, SourcePlatform.of(" NETEASE "))
        assertEquals(SourcePlatform.KUWO, SourcePlatform.of("KW"))
    }

    @Test
    fun `未知或缺省平台返回 null 并给出兜底显示名`() {
        assertNull(SourcePlatform.of("bilibili"))
        assertNull(SourcePlatform.of(null))
        // 未知平台按原样展示，便于把脚本声明里的陌生源如实呈现出来，而不是显示成"未知"
        assertEquals("bilibili", SourcePlatform.labelOf("bilibili"))
        assertEquals("未知平台", SourcePlatform.labelOf(null))
        assertEquals("网易云音乐", SourcePlatform.labelOf("netease"))
    }

    @Test
    fun `内置平台为四个 咪咕没有内置实现`() {
        assertEquals(
            listOf("netease", "qq", "kugou", "kuwo"),
            SourcePlatform.builtins.map { it.code },
        )
        assertFalse(SourcePlatform.MIGU.hasBuiltin)
        assertTrue(SourcePlatform.NETEASE.hasBuiltin)
    }

    // ===== 插件记录 =====

    @Test
    fun `未指定平台的插件对所有平台适用`() {
        val plugin = SourcePlugin(id = "p1", targetPlatform = "")
        assertTrue(plugin.appliesTo("netease"))
        assertTrue(plugin.appliesTo("migu"))
        assertEquals("全平台通用", plugin.targetLabel)
    }

    @Test
    fun `指定平台的插件只对该平台适用且展示明确`() {
        val plugin = SourcePlugin(id = "p1", targetPlatform = "qq")
        assertTrue(plugin.appliesTo("qq"))
        assertFalse(plugin.appliesTo("netease"))
        assertEquals("指定：QQ 音乐", plugin.targetLabel)
    }

    @Test
    fun `脚本声明的源会转成平台并去重`() {
        val plugin = SourcePlugin(
            id = "p1",
            declaredSources = listOf("wy", "tx", "wy", "unknown_src"),
        )
        assertEquals(listOf(SourcePlatform.NETEASE, SourcePlatform.QQ), plugin.declaredPlatforms)
    }

    // ===== 检测结论 =====

    @Test
    fun `速度分档以阈值划分正常与偏慢`() {
        assertEquals(ProbeStatus.OK, SourceProbe.statusOf(200, ok = true))
        assertEquals(ProbeStatus.SLOW, SourceProbe.statusOf(SourceProbe.SLOW_THRESHOLD_MS, ok = true))
        assertEquals(ProbeStatus.SLOW, SourceProbe.statusOf(9000, ok = true))
        assertEquals(ProbeStatus.FAIL, SourceProbe.statusOf(50, ok = false))
    }

    @Test
    fun `耗时展示按秒与毫秒切换`() {
        assertEquals("—", SourceProbe().latencyLabel)
        assertEquals("82ms", SourceProbe(latencyMs = 82).latencyLabel)
        assertEquals("2.5s", SourceProbe(latencyMs = 2500).latencyLabel)
    }

    @Test
    fun `排序用的速度估计依次回退到检测值 播放实测 中性值`() {
        assertEquals(300L, SourceProbe(latencyMs = 300, observedMs = 900).speedScoreMs)
        assertEquals(900L, SourceProbe(latencyMs = -1, observedMs = 900, observedCount = 2).speedScoreMs)
        assertEquals(SourceProbe.UNKNOWN_LATENCY_MS, SourceProbe().speedScoreMs)
    }

    @Test
    fun `只有正常与偏慢算成功`() {
        assertTrue(SourceProbe(status = ProbeStatus.OK).ok)
        assertTrue(SourceProbe(status = ProbeStatus.SLOW).ok)
        assertFalse(SourceProbe(status = ProbeStatus.FAIL).ok)
        assertFalse(SourceProbe(status = ProbeStatus.UNTESTABLE).ok)
        assertFalse(SourceProbe(status = ProbeStatus.UNKNOWN).ok)
    }

    @Test
    fun `检测目标 id 格式稳定`() {
        assertEquals("builtin:netease", SourceProbe.builtinTarget(SourcePlatform.NETEASE))
        assertEquals("plugin:abc:qq", SourceProbe.pluginTarget("abc", SourcePlatform.QQ))
    }
}
