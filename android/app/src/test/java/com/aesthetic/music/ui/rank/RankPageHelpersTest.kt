package com.aesthetic.music.ui.rank

import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.platform.RankCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排行榜页展示辅助函数单测。
 *
 * 这两条规则以前写在 Compose 里，只能靠肉眼确认；抽成 internal 纯函数后，
 * "曲目数/播放量都为空时不显示空行""未识别的榜单不显示语义徽标"这类细节不会再回退。
 */
class RankPageHelpersTest {

    @Test
    fun `次要信息按曲目数加播放量拼接`() {
        assertEquals(
            "300 首 · 1920.0万",
            rankMetaText(PlaylistItem(id = "1", name = "热歌榜", trackCount = 300L, playCount = 19_200_000L)),
        )
        // 只有曲目数（酷狗榜单没有 totalNum 时）
        assertEquals("55 首", rankMetaText(PlaylistItem(id = "1", name = "x", trackCount = 55L)))
        // 只有播放量
        assertEquals("1073.1万", rankMetaText(PlaylistItem(id = "1", name = "x", playCount = 10_730_840L)))
        // 两者都没有：返回空串，界面不渲染这一行（而不是一行空白或 "0 首"）
        assertEquals("", rankMetaText(PlaylistItem(id = "1", name = "x")))
        // 0 与负数视为"没有"：接口常用 0 表示未知
        assertEquals("", rankMetaText(PlaylistItem(id = "1", name = "x", trackCount = 0L, playCount = 0L)))
    }

    @Test
    fun `榜单语义徽标只给识别出的四类`() {
        assertEquals("热歌", rankKindLabel("hot"))
        assertEquals("新歌", rankKindLabel("new"))
        assertEquals("飙升", rankKindLabel("rising"))
        assertEquals("原创", rankKindLabel("original"))
        assertEquals("", rankKindLabel("other"))
        assertEquals("", rankKindLabel(null))
    }

    @Test
    fun `按名字推导的徽标与平台目录一致`() {
        val qqHot = PlaylistItem(id = "26", name = "热歌榜", platform = RankCatalog.QQ)
        assertEquals("热歌", rankKindLabel(qqHot.kind ?: RankCatalog.kindOf(qqHot.name)))
        val kuwoOther = PlaylistItem(id = "9", name = "国潮音乐榜", platform = RankCatalog.KUWO)
        assertEquals("", rankKindLabel(kuwoOther.kind ?: RankCatalog.kindOf(kuwoOther.name)))
    }

    // ==================== 新版排行榜页（此刻焦点 / 榜单卡） ====================

    @Test
    fun `焦点榜单优先飙升榜`() {
        val hot = PlaylistItem(id = "1", name = "热歌榜")
        val rising = PlaylistItem(id = "2", name = "飙升榜")
        val fresh = PlaylistItem(id = "3", name = "新歌榜")
        // 飙升榜排在中间也要被选中（参考图里「此刻焦点」就是飙升榜）
        assertEquals(rising, rankSpotlight(listOf(hot, rising, fresh)))
        // 没有飙升榜就取第一个
        assertEquals(hot, rankSpotlight(listOf(hot, fresh)))
        assertEquals(null, rankSpotlight(emptyList()))
    }

    @Test
    fun `四种语义榜单各有一套配色`() {
        val kinds = listOf("rising", "new", "original", "hot", "other")
        val colors = kinds.map { rankAccentColors(it).first }
        // 五种语义的主色互不相同，否则卡片就分不出类别
        assertEquals(colors.size, colors.distinct().size)
        assertEquals(rankAccentColors("rising"), rankAccentColors("rising"))
    }

    @Test
    fun `说明文案优先用接口字段，否则按语义给固定文案`() {
        // 接口有 description 就用接口的
        assertEquals(
            "接口给的简介",
            rankDescription(PlaylistItem(id = "1", name = "新歌榜", description = "接口给的简介")),
        )
        // 没有就用语义文案，且不含任何具体数字（不伪造数据）
        val rising = rankDescription(PlaylistItem(id = "2", name = "飙升榜"))
        assertTrue(rising.contains("上升最快"))
        assertTrue(rising.none { it.isDigit() })
        val hot = rankDescription(PlaylistItem(id = "3", name = "热歌榜"))
        assertTrue(hot.contains("最热"))
        assertTrue(hot.none { it.isDigit() })
        // 未识别语义也有兜底文案
        assertTrue(rankDescription(PlaylistItem(id = "4", name = "国潮音乐榜")).isNotEmpty())
    }

    @Test
    fun `更新时间按本地拉取时间换算`() {
        val now = 1_767_225_600_000L
        assertEquals("刚刚更新", rankUpdatedLabel(0L, now))
        assertEquals("刚刚更新", rankUpdatedLabel(now - 5 * 60_000L, now))
        assertEquals("刚刚更新", rankUpdatedLabel(now - 59 * 60_000L, now))
        assertEquals("2 小时前更新", rankUpdatedLabel(now - 2 * 3_600_000L, now))
        assertEquals("3 天前更新", rankUpdatedLabel(now - 3 * 86_400_000L, now))
        assertEquals("较久未更新", rankUpdatedLabel(now - 60 * 86_400_000L, now))
        // 时钟回拨（本地时间被改过）不应出现负数文案
        assertEquals("刚刚更新", rankUpdatedLabel(now + 3_600_000L, now))
    }

    @Test
    fun `平台品牌色四个平台互不相同`() {
        val colors = RankCatalog.platforms.map { platformBrandColor(it.id) }
        assertEquals(colors.size, colors.distinct().size)
        assertEquals(platformBrandColor("unknown"), platformBrandColor("nope"))
    }
}
