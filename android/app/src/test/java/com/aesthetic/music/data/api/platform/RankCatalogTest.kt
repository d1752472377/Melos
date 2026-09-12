package com.aesthetic.music.data.api.platform

import com.aesthetic.music.data.api.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 榜单平台目录单测：平台身份、榜单归类、排序、单次拉取上限。
 *
 * 榜单名来自四个平台线上真实返回（网易云/QQ/酷狗/酷我），用它们当用例可以保证
 * "换平台以后榜单顺序乱跳""飙升榜被归成热歌榜"这类问题在编译期之外也被钉住。
 */
class RankCatalogTest {

    @Test
    fun `平台 tag 的顺序与名称固定`() {
        assertEquals(
            listOf("netease", "qq", "kugou", "kuwo"),
            RankCatalog.platforms.map { it.id },
        )
        assertEquals(
            listOf("网易云", "QQ 音乐", "酷狗音乐", "酷我音乐"),
            RankCatalog.platforms.map { it.label },
        )
    }

    @Test
    fun `未知平台回落到默认而不是产生空 tag`() {
        assertEquals(RankCatalog.NETEASE, RankCatalog.normalize(null))
        assertEquals(RankCatalog.NETEASE, RankCatalog.normalize(""))
        assertEquals(RankCatalog.NETEASE, RankCatalog.normalize("QQ音乐")) // 中文平台名不是 id
        assertEquals(RankCatalog.QQ, RankCatalog.normalize(" QQ "))
        assertEquals(RankCatalog.KUGOU, RankCatalog.normalize("KUGOU"))
        assertEquals("QQ 音乐", RankCatalog.label("qq"))
        assertEquals("网易云", RankCatalog.label("不存在"))
    }

    @Test
    fun `各平台真实榜单名都能归类`() {
        // 网易云 / QQ / 酷狗 / 酷我 线上返回里出现过的名字
        assertEquals("hot", RankCatalog.kindOf("热歌榜"))
        assertEquals("hot", RankCatalog.kindOf("酷我热歌榜"))
        assertEquals("hot", RankCatalog.kindOf("TOP500"))
        assertEquals("hot", RankCatalog.kindOf("视频号热歌酷狗榜"))
        assertEquals("new", RankCatalog.kindOf("新歌榜"))
        assertEquals("new", RankCatalog.kindOf("酷我新歌榜"))
        assertEquals("rising", RankCatalog.kindOf("飙升榜"))
        assertEquals("rising", RankCatalog.kindOf("酷我飙升榜"))
        assertEquals("original", RankCatalog.kindOf("原创榜"))
        // 未识别的榜单归 other，不能错误地混进"热歌"
        assertEquals("other", RankCatalog.kindOf("国潮音乐榜"))
        assertEquals("other", RankCatalog.kindOf("美国公告牌榜"))
        assertEquals("other", RankCatalog.kindOf(null))
    }

    @Test
    fun `排序把热歌新歌飙升原创提到前面且同权重稳定`() {
        val input = listOf(
            rank("国潮音乐榜", "other"),
            rank("飙升榜", "rising"),
            rank("热歌榜", "hot"),
            rank("新歌榜", "new"),
            rank("电音榜", "other"),
        )
        val sorted = RankCatalog.sortRanks(input)
        assertEquals(listOf("热歌榜", "新歌榜", "飙升榜", "国潮音乐榜", "电音榜"), sorted.map { it.name })
    }

    @Test
    fun `缺 kind 时按名字推导再排序`() {
        // 后端/缓存里可能存在只有名字没有 kind 的旧数据
        val sorted = RankCatalog.sortRanks(
            listOf(
                PlaylistItem(id = "1", name = "酷我新歌榜", platform = RankCatalog.KUWO),
                PlaylistItem(id = "2", name = "酷我热歌榜", platform = RankCatalog.KUWO),
            )
        )
        assertEquals(listOf("酷我热歌榜", "酷我新歌榜"), sorted.map { it.name })
    }

    @Test
    fun `榜单曲目上限按平台给出安全值`() {
        assertEquals(300, RankCatalog.songLimit(RankCatalog.KUWO))
        // QQ 的 h5 榜单接口实测单页最多 50 首（请求 100 也只返 50）
        assertEquals(50, RankCatalog.songLimit(RankCatalog.QQ))
        assertEquals(100, RankCatalog.songLimit(RankCatalog.KUGOU))
        assertEquals(200, RankCatalog.songLimit(RankCatalog.NETEASE))
        // 未知平台走默认分支（网易云同档）
        assertEquals(200, RankCatalog.songLimit("future"))
    }

    @Test
    fun `重复榜单被去掉 避免列表 key 冲突`() {
        val dup = listOf(
            PlaylistItem(id = "489927", name = "酷我热歌榜", platform = RankCatalog.KUWO, group = "官方"),
            PlaylistItem(id = "489927", name = "酷我热歌榜", platform = RankCatalog.KUWO, group = "特色"),
            PlaylistItem(id = "26", name = "热歌榜", platform = RankCatalog.QQ, group = "巅峰榜"),
        )
        val cleaned = RankCatalog.dedupe(dup)
        assertEquals(2, cleaned.size)
        // 保留第一次出现的分组，顺序不变
        assertEquals("官方", cleaned.first().group)
    }

    @Test
    fun `分组字段默认是空的`() {
        // 非榜单场景（歌单/专辑）不该被分组逻辑误伤
        assertNull(PlaylistItem(id = "1", name = "x").group)
        val qq = PlaylistItem(id = "26", name = "热歌榜", platform = RankCatalog.QQ, group = "巅峰榜")
        assertEquals("巅峰榜", qq.group)
        assertTrue(RankCatalog.kindOrder(RankCatalog.kindOf(qq.name)) < RankCatalog.kindOrder("other"))
    }

    private fun rank(name: String, kind: String) =
        PlaylistItem(id = name, name = name, platform = RankCatalog.NETEASE, kind = kind)
}
