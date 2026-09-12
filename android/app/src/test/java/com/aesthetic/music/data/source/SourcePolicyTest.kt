package com.aesthetic.music.data.source

import com.aesthetic.music.data.settings.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SourcePolicy] 的音源挑选策略测试。
 *
 * 这里是"失败换源 / 智能切换 / 自动最快"三个开关的**唯一**语义来源：
 * 界面上的说明文案与这段实现对不上，用户就会觉得开关是假的，因此逐条钉住。
 */
class SourcePolicyTest {

    private fun plugin(
        id: String,
        name: String = "插件$id",
        platform: String = "",
        enable: Boolean = true,
        lastUsedAt: Long = 0L,
        importedAt: Long = 0L,
    ) = SourcePlugin(
        id = id,
        name = name,
        targetPlatform = platform,
        enable = enable,
        lastUsedAt = lastUsedAt,
        importedAt = importedAt,
    )

    /** 生成"某个插件在某个平台的检测结论"，用 map 返回以便用 + 拼接多条 */
    private fun probe(id: String, platform: SourcePlatform, status: ProbeStatus, ms: Long): Map<String, SourceProbe> {
        val target = SourceProbe.pluginTarget(id, platform)
        return mapOf(target to SourceProbe(target = target, status = status, latencyMs = ms))
    }

    private val base = UserPrefs()

    // ===== 默认行为 =====

    @Test
    fun `失败换源默认开启`() {
        // 产品默认：宁可多试一个源也不要让用户点开歌却播不了
        assertTrue(base.sourceFailover)
        assertFalse(base.sourceSmartSwitch)
        assertFalse(base.sourceFastest)
    }

    @Test
    fun `失败换源关闭时只尝试一个插件`() {
        val plugins = listOf(plugin("a", lastUsedAt = 2), plugin("b", lastUsedAt = 1))
        val result = SourcePolicy.candidates(plugins, "netease", emptyMap(), base.copy(sourceFailover = false))
        assertEquals(1, result.size)
        assertEquals("a", result.single().id)
    }

    @Test
    fun `失败换源开启后按顺序给出多个候选`() {
        val plugins = listOf(plugin("a", lastUsedAt = 3), plugin("b", lastUsedAt = 2), plugin("c", lastUsedAt = 1))
        val result = SourcePolicy.candidates(
            plugins, "netease", emptyMap(),
            base.copy(sourceFailover = true),
        )
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
    }

    @Test
    fun `候选数量封顶为三`() {
        val plugins = (1..5).map { plugin("p$it", lastUsedAt = it.toLong()) }
        val result = SourcePolicy.candidates(
            plugins, "netease", emptyMap(),
            base.copy(sourceFailover = true),
        )
        assertEquals(SourcePolicy.MAX_ATTEMPTS, result.size)
    }

    @Test
    fun `停用的插件不参与解析`() {
        val plugins = listOf(plugin("a", enable = false), plugin("b"))
        val result = SourcePolicy.candidates(
            plugins, "netease", emptyMap(),
            base.copy(sourceFailover = true),
        )
        assertEquals(listOf("b"), result.map { it.id })
    }

    // ===== 指定平台 =====

    @Test
    fun `指定平台的插件只用于该平台`() {
        val plugins = listOf(plugin("qq_only", platform = "qq"), plugin("generic"))
        val forQq = SourcePolicy.candidates(plugins, "qq", emptyMap(), base.copy(sourceFailover = true))
        assertEquals(setOf("qq_only", "generic"), forQq.map { it.id }.toSet())

        val forNetease = SourcePolicy.candidates(plugins, "netease", emptyMap(), base.copy(sourceFailover = true))
        assertEquals(listOf("generic"), forNetease.map { it.id })
    }

    @Test
    fun `没有任何适用插件时返回空列表`() {
        val plugins = listOf(plugin("qq_only", platform = "qq"))
        assertTrue(SourcePolicy.candidates(plugins, "netease", emptyMap(), base).isEmpty())
    }

    // ===== 手动指定 =====

    @Test
    fun `手动指定的音源排在第一位`() {
        val plugins = listOf(plugin("a", lastUsedAt = 100), plugin("b", lastUsedAt = 1))
        val result = SourcePolicy.candidates(
            plugins, "netease", emptyMap(),
            base.copy(sourceFailover = true),
            activeId = "b",
        )
        assertEquals(listOf("b", "a"), result.map { it.id })
    }

    // ===== 智能切换 =====

    @Test
    fun `智能切换会跳过失败的源`() {
        val probes = probe("a", SourcePlatform.NETEASE, ProbeStatus.FAIL, 300) + probe("b", SourcePlatform.NETEASE, ProbeStatus.OK, 200)
        val result = SourcePolicy.candidates(
            listOf(plugin("a", lastUsedAt = 2), plugin("b", lastUsedAt = 1)),
            "netease", probes,
            base.copy(sourceFailover = true, sourceSmartSwitch = true),
        )
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun `智能切换会跳过偏慢的源`() {
        val probes = probe("a", SourcePlatform.NETEASE, ProbeStatus.SLOW, 4000) + probe("b", SourcePlatform.NETEASE, ProbeStatus.OK, 200)
        val result = SourcePolicy.candidates(
            listOf(plugin("a", lastUsedAt = 2), plugin("b", lastUsedAt = 1)),
            "netease", probes,
            base.copy(sourceFailover = true, sourceSmartSwitch = true),
        )
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun `智能切换在全部不可用时仍保留候选避免无源可播`() {
        val probes = probe("a", SourcePlatform.NETEASE, ProbeStatus.FAIL, 300) + probe("b", SourcePlatform.NETEASE, ProbeStatus.FAIL, 300)
        val result = SourcePolicy.candidates(
            listOf(plugin("a", lastUsedAt = 2), plugin("b", lastUsedAt = 1)),
            "netease", probes,
            base.copy(sourceFailover = true, sourceSmartSwitch = true),
        )
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `未检测过的源不会被智能切换剔除`() {
        val probes = probe("a", SourcePlatform.NETEASE, ProbeStatus.FAIL, 300)
        val result = SourcePolicy.candidates(
            listOf(plugin("a", lastUsedAt = 2), plugin("b", lastUsedAt = 1)),
            "netease", probes,
            base.copy(sourceFailover = true, sourceSmartSwitch = true),
        )
        assertEquals(listOf("b"), result.map { it.id })
    }

    // ===== 自动最快 =====

    @Test
    fun `自动最快按实测耗时排序`() {
        val probes = probe("slow", SourcePlatform.NETEASE, ProbeStatus.OK, 1800) + probe("fast", SourcePlatform.NETEASE, ProbeStatus.OK, 120)
        val result = SourcePolicy.candidates(
            listOf(plugin("slow", lastUsedAt = 100), plugin("fast", lastUsedAt = 1)),
            "netease", probes,
            base.copy(sourceFastest = true, sourceFailover = true),
        )
        assertEquals(listOf("fast", "slow"), result.map { it.id })
    }

    @Test
    fun `自动最快时没有速度数据的源使用中性估值而不是被永久排在最后`() {
        val probes = probe("measured", SourcePlatform.NETEASE, ProbeStatus.OK, 1400)
        val result = SourcePolicy.candidates(
            listOf(plugin("measured", lastUsedAt = 100), plugin("fresh", lastUsedAt = 1)),
            "netease", probes,
            base.copy(sourceFastest = true, sourceFailover = true),
        )
        // 1400 < 中性估值 1500，已测的排前；差距很小，但顺序必须确定
        assertEquals(listOf("measured", "fresh"), result.map { it.id })
    }

    @Test
    fun `自动最快会用播放实测均值兜底`() {
        val fastObserved = SourceProbe(
            target = SourceProbe.pluginTarget("fast", SourcePlatform.NETEASE),
            status = ProbeStatus.UNKNOWN,
            observedMs = 150,
            observedCount = 3,
        )
        val slowProbe = SourceProbe(
            target = SourceProbe.pluginTarget("slow", SourcePlatform.NETEASE),
            status = ProbeStatus.UNKNOWN,
            latencyMs = 900,
        )
        val probes = mapOf(
            SourceProbe.pluginTarget("fast", SourcePlatform.NETEASE) to fastObserved,
            SourceProbe.pluginTarget("slow", SourcePlatform.NETEASE) to slowProbe,
        )
        val result = SourcePolicy.candidates(
            listOf(plugin("slow", lastUsedAt = 100), plugin("fast", lastUsedAt = 1)),
            "netease", probes,
            base.copy(sourceFastest = true, sourceFailover = true),
        )
        assertEquals(listOf("fast", "slow"), result.map { it.id })
    }

    // ===== 兜底与辅助 =====

    @Test
    fun `失败换源关闭时不再继续尝试`() {
        assertFalse(SourcePolicy.shouldContinueAfterFailure(base.copy(sourceFailover = false)))
        assertTrue(SourcePolicy.shouldContinueAfterFailure(base.copy(sourceFailover = true)))
    }

    @Test
    fun `内置直连只对内置平台可用`() {
        assertTrue(SourcePolicy.builtinFallbackAvailable("netease"))
        assertTrue(SourcePolicy.builtinFallbackAvailable("kugou"))
        // 咪咕没有内置实现，兜底不存在
        assertFalse(SourcePolicy.builtinFallbackAvailable("migu"))
        assertFalse(SourcePolicy.builtinFallbackAvailable("unknown"))
    }
}
