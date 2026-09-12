package com.aesthetic.music.ui.rank

import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.platform.RankCatalog
import org.junit.Assert.assertEquals
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
}
