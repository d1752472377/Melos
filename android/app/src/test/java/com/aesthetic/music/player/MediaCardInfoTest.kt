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
    fun `三个播放控制永远排在最前 收藏排最后`() {
        val order = MediaCardInfo.actionOrder(showFavorite = true)
        assertEquals(
            listOf(
                MediaCardInfo.ActionKind.PREVIOUS,
                MediaCardInfo.ActionKind.PLAY_PAUSE,
                MediaCardInfo.ActionKind.NEXT,
                MediaCardInfo.ActionKind.FAVORITE,
            ),
            order,
        )
    }

    @Test
    fun `下一首必须在前三个之内 —— 系统只显示前几个按钮时不能把它挤掉`() {
        val order = MediaCardInfo.actionOrder(showFavorite = true)
        val nextIndex = order.indexOf(MediaCardInfo.ActionKind.NEXT)
        assertTrue("下一首的下标必须是 2 或更小，实际 $nextIndex", nextIndex <= 2)
        assertTrue(
            "收藏按钮必须排在最后，实际下标 ${order.indexOf(MediaCardInfo.ActionKind.FAVORITE)}",
            order.indexOf(MediaCardInfo.ActionKind.FAVORITE) == order.lastIndex,
        )
    }

    @Test
    fun `关掉收藏按钮后只剩三个播放控制`() {
        assertEquals(
            listOf(
                MediaCardInfo.ActionKind.PREVIOUS,
                MediaCardInfo.ActionKind.PLAY_PAUSE,
                MediaCardInfo.ActionKind.NEXT,
            ),
            MediaCardInfo.actionOrder(showFavorite = false),
        )
    }

    @Test
    fun `折叠态只取前三个下标`() {
        assertEquals(listOf(0, 1, 2), MediaCardInfo.compactIndices(4).toList())
        assertEquals(listOf(0, 1, 2), MediaCardInfo.compactIndices(3).toList())
        assertEquals(listOf(0, 1), MediaCardInfo.compactIndices(2).toList())
        assertEquals(listOf(0), MediaCardInfo.compactIndices(1).toList())
        assertEquals(emptyList<Int>(), MediaCardInfo.compactIndices(0).toList())
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

    // ===== 进度 =====

    @Test
    fun `进度文案为 已播放 斜杠 总时长`() {
        assertEquals("01:23 / 04:05", MediaCardInfo.progressText(83_000, 245_000))
    }

    @Test
    fun `时长未知时只显示已播放`() {
        assertEquals("01:23", MediaCardInfo.progressText(83_000, 0))
    }

    @Test
    fun `超过一小时的时长补上小时位`() {
        assertEquals("1:00:00", MediaCardInfo.clock(3_600_000))
        assertEquals("1:05:07", MediaCardInfo.clock(3_907_000))
    }

    @Test
    fun `负数时间按零处理 不出现负号`() {
        assertEquals("00:00", MediaCardInfo.clock(-1_000))
        assertEquals("-00:00", MediaCardInfo.remainingText(1_000, 500))
    }

    @Test
    fun `剩余时间文案带负号`() {
        assertEquals("-02:42", MediaCardInfo.remainingText(83_000, 245_000))
    }

    @Test
    fun `进度比例对时长为零返回零 而不是 NaN`() {
        assertEquals(0f, MediaCardInfo.progressFraction(10_000, 0), 0.0001f)
        assertEquals(0f, MediaCardInfo.progressFraction(10_000, -5), 0.0001f)
    }

    @Test
    fun `进度比例被夹在 0 到 1 之间`() {
        assertEquals(0.5f, MediaCardInfo.progressFraction(5_000, 10_000), 0.0001f)
        assertEquals(0f, MediaCardInfo.progressFraction(-500, 10_000), 0.0001f)
        assertEquals(1f, MediaCardInfo.progressFraction(20_000, 10_000), 0.0001f)
    }

    // ===== 实时活动（Android 16 状态栏胶囊）=====

    @Test
    fun `胶囊文案优先歌词`() {
        assertEquals("穿过云层", MediaCardInfo.shortCriticalText(" 穿过云层 ", "夜曲"))
    }

    @Test
    fun `没有歌词时退回歌名`() {
        assertEquals("夜曲", MediaCardInfo.shortCriticalText(null, "夜曲"))
        assertEquals("夜曲", MediaCardInfo.shortCriticalText("   ", "夜曲"))
    }

    @Test
    fun `歌词与歌名都没有时返回 null 让系统自己决定`() {
        assertNull(MediaCardInfo.shortCriticalText(null, null))
        assertNull(MediaCardInfo.shortCriticalText("", "  "))
    }
}
