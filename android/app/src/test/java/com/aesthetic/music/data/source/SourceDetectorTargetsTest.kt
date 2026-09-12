package com.aesthetic.music.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SourceDetector.buildTargets] 的目标清单测试。
 *
 * 目标清单决定了界面上能检测到哪些源、以及"检测"这件事覆盖范围是否诚实：
 * 内置四平台必须各有一项；插件的检测项按**脚本声明的平台**生成，
 * 而不是拿内置四平台硬套（否则会去测一个脚本根本不支持的平台，报出的失败是假的）。
 */
class SourceDetectorTargetsTest {

    private val detector = SourceDetector()

    private fun plugin(id: String = "p1", declared: List<String> = emptyList(), enable: Boolean = true) =
        SourcePlugin(id = id, name = "插件$id", declaredSources = declared, enable = enable)

    @Test
    fun `没有插件时只有四个内置平台`() {
        val targets = detector.buildTargets(emptyList())
        assertEquals(4, targets.size)
        assertEquals(
            listOf("builtin:netease", "builtin:qq", "builtin:kugou", "builtin:kuwo"),
            targets.map { it.id },
        )
        assertEquals("内置直连 · 网易云音乐", targets.first().label)
    }

    @Test
    fun `插件的检测项按脚本声明生成`() {
        val targets = detector.buildTargets(listOf(plugin(declared = listOf("wy", "kg"))))
        val pluginTargets = targets.filterIsInstance<DetectTarget.PluginPlatform>()
        assertEquals(listOf("p1"), pluginTargets.map { it.plugin.id }.distinct())
        assertEquals(
            listOf("plugin:p1:netease", "plugin:p1:kugou"),
            pluginTargets.map { it.id },
        )
    }

    @Test
    fun `声明了咪咕也会生成检测项 由检测逻辑标记为无法自动检测`() {
        val targets = detector.buildTargets(listOf(plugin(declared = listOf("mg"))))
        val migu = targets.filterIsInstance<DetectTarget.PluginPlatform>()
            .first { it.platform == SourcePlatform.MIGU }
        assertEquals("plugin:p1:migu", migu.id)
        // 咪咕没有内置搜索入口，这一项在 UI 上必须显示为"无法自动检测"而不是成功
        assertTrue(!migu.platform.hasBuiltin)
    }

    @Test
    fun `还没抓到声明源的插件按内置四平台生成检测项`() {
        val targets = detector.buildTargets(listOf(plugin(declared = emptyList())))
        assertEquals(4, targets.filterIsInstance<DetectTarget.PluginPlatform>().size)
    }

    @Test
    fun `停用的插件不参与检测`() {
        val targets = detector.buildTargets(listOf(plugin(enable = false, declared = listOf("wy"))))
        assertTrue(targets.filterIsInstance<DetectTarget.PluginPlatform>().isEmpty())
    }

    @Test
    fun `多个插件的检测项不会互相覆盖`() {
        val targets = detector.buildTargets(
            listOf(
                plugin(id = "a", declared = listOf("wy")),
                plugin(id = "b", declared = listOf("wy", "tx")),
            )
        )
        val pluginTargets = targets.filterIsInstance<DetectTarget.PluginPlatform>()
        assertEquals(listOf("plugin:a:netease", "plugin:b:netease", "plugin:b:qq"), pluginTargets.map { it.id })
        assertTrue(pluginTargets.map { it.label }.contains("插件b · 网易云音乐"))
    }
}
