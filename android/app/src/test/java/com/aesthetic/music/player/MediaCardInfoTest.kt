package com.aesthetic.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 媒体卡片文案与按钮顺序的单测。
 *
 * 这里的每一条都对应一个真实踩过的坑或产品约定，改动前先看注释：
 * 「按钮顺序」那条是用户实测反馈的回归测试（卡片上没有"下一首"）。
 */
class MediaCardInfoTest {

    // ===== 按钮顺序（回归测试）=====

    @Test
    fun `传输按钮顺序固定为上一首 播放暂停 下一首`() {
        assertEquals(
            listOf(
                MediaCardInfo.ActionKind.PREVIOUS,
                MediaCardInfo.ActionKind.PLAY_PAUSE,
                MediaCardInfo.ActionKind.NEXT,
            ),
            MediaCardInfo.transportKinds(canSwitchQueue = true),
        )
    }

    @Test
    fun `下一首必须在前三个之内 —— 系统只显示前几个按钮时不能把它挤掉`() {
        val order = MediaCardInfo.transportKinds(canSwitchQueue = true)
        val nextIndex = order.indexOf(MediaCardInfo.ActionKind.NEXT)
        assertTrue("下一首的下标必须是 2 或更小，实际 $nextIndex", nextIndex <= 2)
        // 卡片上只允许这几种按钮：以后往里加按钮必须先想清楚它会不会挤掉"下一首"
        assertEquals(3, order.size)
    }

    @Test
    fun `队列只有一首时只留播放暂停 —— 不画点了没反应的假按钮`() {
        // 单曲 timeline 下 seekToNext/Previous 的命令不可用（见 MelosSessionPlayer），
        // 画出来的上一首/下一首会点了没反应，比没有按钮更让人困惑
        assertEquals(
            listOf(MediaCardInfo.ActionKind.PLAY_PAUSE),
            MediaCardInfo.transportKinds(canSwitchQueue = false),
        )
    }

    // ===== 音质标签 =====

    @Test
    fun `音质值映射为卡片短标签`() {
        assertEquals("标准", MediaCardInfo.qualityLabel("standard"))
        assertEquals("高品质", MediaCardInfo.qualityLabel("high"))
        assertEquals("无损", MediaCardInfo.qualityLabel("lossless"))
        assertEquals("Hi-Res", MediaCardInfo.qualityLabel("hires"))
    }

    @Test
    fun `未知音质原样显示 空值返回 null`() {
        assertEquals("something-else", MediaCardInfo.qualityLabel("something-else"))
        assertNull(MediaCardInfo.qualityLabel(null))
        assertNull(MediaCardInfo.qualityLabel(""))
    }

    // ===== 信息行 =====

    @Test
    fun `信息行按 专辑 音质 音源 串联`() {
        val line = MediaCardInfo.infoLine(
            album = "十一月的萧邦",
            quality = "lossless",
            sourceName = "某插件",
        )
        assertEquals("十一月的萧邦 · 无损 · 某插件", line)
    }

    @Test
    fun `信息行跳过空项与未知音源`() {
        assertEquals("无损", MediaCardInfo.infoLine(album = null, quality = "lossless", sourceName = null))
        assertEquals("无损", MediaCardInfo.infoLine(album = "", quality = "lossless", sourceName = "未知音源"))
    }

    @Test
    fun `关掉专辑开关后只留音质与音源`() {
        val line = MediaCardInfo.infoLine(
            album = "十一月的萧邦",
            quality = "lossless",
            sourceName = "内置直连",
            showAlbum = false,
        )
        assertEquals("无损 · 内置直连", line)
    }

    @Test
    fun `信息行全空时返回 null 卡片不显示这一行`() {
        assertNull(MediaCardInfo.infoLine(album = null, quality = null, sourceName = null))
        assertNull(MediaCardInfo.infoLine(album = "  ", quality = "", sourceName = ""))
    }

    @Test
    fun `歌手为空时用未知歌手占位`() {
        assertEquals("未知歌手", MediaCardInfo.artistLine(null))
        assertEquals("未知歌手", MediaCardInfo.artistLine("   "))
        assertEquals("周杰伦", MediaCardInfo.artistLine(" 周杰伦 "))
    }

    // ===== 实时活动（Android 16 状态栏胶囊）=====

    @Test
    fun `胶囊文案是歌名`() {
        assertEquals("夜曲", MediaCardInfo.shortCriticalText(" 夜曲 "))
    }

    @Test
    fun `没有歌名时返回 null 让系统自己决定`() {
        assertNull(MediaCardInfo.shortCriticalText(null))
        assertNull(MediaCardInfo.shortCriticalText("  "))
    }
}
