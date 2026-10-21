package com.aesthetic.music.ui.guess

import com.aesthetic.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「猜你喜欢」算法单测。
 *
 * 这里的每个用例都对应 `GuessRecommend` 里抄自 GZ `y33.java` 的一条规则，
 * 目的不是覆盖率，而是把「权重/衰减/轮转/同名上限/排除/降级」这些
 * 只能靠肉眼读反编译源码确认的规则钉住——改坏任何一条，页面仍然能跑，
 * 只是推荐质量悄悄退化，这种问题不写测试根本发现不了。
 */
class GuessRecommendTest {

    private fun song(
        id: String,
        title: String = "t$id",
        artist: String = "a$id",
        platform: String = "netease",
        album: String = "",
    ) = Song(id = id, title = title, artist = artist, album = album, platform = platform)

    private val now = 1_767_225_600_000L
    private val day = 86_400_000L

    // ==================== 种子构造 ====================

    @Test
    fun `收藏权重高于最近播放且按权重降序`() {
        val seeds = GuessRecommend.buildSeeds(
            favorites = listOf(song("1")),
            recents = listOf(song("2")),
            lastPlayedAt = emptyMap(),
            nowMs = now,
        )
        assertEquals(2, seeds.size)
        assertEquals("1", seeds[0].songId)
        assertEquals(GuessRecommend.FAVORITE_WEIGHT, seeds[0].weight, 0.0001)
        assertEquals(GuessRecommend.RECENT_WEIGHT, seeds[1].weight, 0.0001)
    }

    @Test
    fun `同一首歌同时出现在收藏与播放里只留收藏那条`() {
        val seeds = GuessRecommend.buildSeeds(
            favorites = listOf(song("1")),
            recents = listOf(song("1")),
            lastPlayedAt = emptyMap(),
            nowMs = now,
        )
        assertEquals(1, seeds.size)
        assertEquals(GuessRecommend.FAVORITE_WEIGHT, seeds[0].weight, 0.0001)
    }

    @Test
    fun `最近播放按七天半衰期衰减`() {
        val seeds = GuessRecommend.buildSeeds(
            favorites = emptyList(),
            recents = listOf(song("9")),
            // 7 天前听过：weight = 1.0 * 0.9^(7/7) = 0.9
            lastPlayedAt = mapOf("netease:9" to now - 7 * day),
            nowMs = now,
        )
        assertEquals(0.9, seeds.single().weight, 0.0001)
    }

    @Test
    fun `收藏按三十天半衰期衰减`() {
        val seeds = GuessRecommend.buildSeeds(
            favorites = listOf(song("9")),
            recents = emptyList(),
            // 30 天前收藏过：weight = 3.0 * 0.9^(30/30) = 2.7
            lastPlayedAt = mapOf("netease:9" to now - 30 * day),
            nowMs = now,
        )
        assertEquals(2.7, seeds.single().weight, 0.0001)
    }

    @Test
    fun `没有时间戳的种子不衰减`() {
        val seeds = GuessRecommend.buildSeeds(
            favorites = listOf(song("9")),
            recents = emptyList(),
            lastPlayedAt = emptyMap(),
            nowMs = now,
        )
        assertEquals(GuessRecommend.FAVORITE_WEIGHT, seeds.single().weight, 0.0001)
    }

    @Test
    fun `网易云非数字 id 的种子被丢弃`() {
        val seeds = GuessRecommend.buildSeeds(
            favorites = listOf(song("abc"), song("12")),
            recents = emptyList(),
            lastPlayedAt = emptyMap(),
            nowMs = now,
        )
        assertEquals(listOf("12"), seeds.map { it.songId })
    }

    @Test
    fun `其它平台的种子保留但不参与相似歌曲`() {
        val seeds = GuessRecommend.buildSeeds(
            favorites = listOf(song("003abc", platform = "qq")),
            recents = emptyList(),
            lastPlayedAt = emptyMap(),
            nowMs = now,
        )
        assertEquals(1, seeds.size)
        assertEquals("qq", seeds.single().platform)
    }

    @Test
    fun `空 id 的种子被丢弃`() {
        val seeds = GuessRecommend.buildSeeds(
            favorites = listOf(Song(id = "", title = "x", platform = "netease")),
            recents = emptyList(),
            lastPlayedAt = emptyMap(),
            nowMs = now,
        )
        assertTrue(seeds.isEmpty())
    }

    @Test
    fun `种子数量上限 40 且每类最多取 20`() {
        // 网易云只接受数字 id（非数字会被当成不可扩展的种子丢掉），所以这里用数字段区分两类
        val favorites = (1..50).map { song("$it") }          // 1..50
        val recents = (1001..1050).map { song("$it") }        // 1001..1050
        val seeds = GuessRecommend.buildSeeds(
            favorites = favorites,
            recents = recents,
            lastPlayedAt = emptyMap(),
            nowMs = now,
        )
        assertEquals(GuessRecommend.MAX_SEEDS, seeds.size)
        // 收藏权重更高，前 20 个必须全是收藏（1..20）
        assertEquals((1..20).map { "$it" }, seeds.take(20).map { it.songId })
        // 后 20 个是最近播放（1001..1020），且两类各只取 20 个
        assertEquals((1001..1020).map { "$it" }, seeds.drop(20).map { it.songId })
    }

    // ==================== 轮转挑选 ====================

    @Test
    fun `高权重种子出歌更多`() {
        val seeds = listOf(
            GuessRecommend.Seed("netease", "1", 3.0),
            GuessRecommend.Seed("netease", "2", 1.0),
        )
        val candidates = listOf(
            (1..10).map { song("h$it") }, // 高权重种子
            (1..10).map { song("l$it") }, // 低权重种子
        )
        val picked = GuessRecommend.pick(seeds, candidates, exclude = emptySet())
        val high = picked.count { it.id.startsWith("h") }
        val low = picked.count { it.id.startsWith("l") }
        assertEquals(3, high) // round(3.0) = 3 轮
        assertEquals(1, low)  // round(1.0) = 1 轮
        assertEquals(4, picked.size)
    }

    @Test
    fun `每轮每个种子只出一首且按种子顺序`() {
        val seeds = listOf(
            GuessRecommend.Seed("netease", "1", 2.0),
            GuessRecommend.Seed("netease", "2", 2.0),
        )
        val candidates = listOf(
            listOf(song("a1"), song("a2")),
            listOf(song("b1"), song("b2")),
        )
        val picked = GuessRecommend.pick(seeds, candidates, exclude = emptySet())
        assertEquals(listOf("a1", "b1", "a2", "b2"), picked.map { it.id })
    }

    @Test
    fun `同名歌曲最多两首`() {
        val seeds = listOf(GuessRecommend.Seed("netease", "1", 5.0))
        val candidates = listOf(
            (1..6).map { song("x$it", title = "起风了") },
        )
        val picked = GuessRecommend.pick(
            seeds, candidates, exclude = emptySet(), maxPerTitle = GuessRecommend.MAX_PER_TITLE,
        )
        assertEquals(GuessRecommend.MAX_PER_TITLE, picked.size)
    }

    @Test
    fun `放宽同名上限后能拿到更多`() {
        val seeds = listOf(GuessRecommend.Seed("netease", "1", 5.0))
        val candidates = listOf((1..6).map { song("x$it", title = "起风了") })
        val picked = GuessRecommend.pick(
            seeds, candidates, exclude = emptySet(), maxPerTitle = Int.MAX_VALUE,
        )
        assertEquals(5, picked.size) // 5 轮上限，第 6 首取不到
    }

    @Test
    fun `同名判定忽略大小写与分隔符`() {
        assertEquals(
            GuessRecommend.normalizeTitle("Hello, World"),
            GuessRecommend.normalizeTitle("hello world"),
        )
        assertEquals(
            GuessRecommend.normalizeTitle("A - B"),
            GuessRecommend.normalizeTitle("A/B"),
        )
        // 带括号的版本号不同 → 不算同名
        assertFalse(
            GuessRecommend.normalizeTitle("起风了") == GuessRecommend.normalizeTitle("起风了 (Live)"),
        )
    }

    @Test
    fun `排除集里的歌不会被选中`() {
        val seeds = listOf(GuessRecommend.Seed("netease", "1", 2.0))
        val candidates = listOf(listOf(song("a"), song("b"), song("c")))
        val picked = GuessRecommend.pick(
            seeds, candidates, exclude = setOf("netease:a", "netease:b"),
        )
        assertEquals(listOf("c"), picked.map { it.id })
    }

    @Test
    fun `结果上限 30`() {
        val seeds = listOf(GuessRecommend.Seed("netease", "1", 50.0))
        val candidates = listOf((1..100).map { song("x$it", title = "t$it") })
        val picked = GuessRecommend.pick(seeds, candidates, exclude = emptySet())
        assertEquals(GuessRecommend.MAX_RESULT, picked.size)
    }

    @Test
    fun `候选池全空时返回空列表`() {
        val seeds = listOf(GuessRecommend.Seed("netease", "1", 2.0))
        assertTrue(GuessRecommend.pick(seeds, listOf(emptyList()), emptySet()).isEmpty())
        assertTrue(GuessRecommend.pick(emptyList(), emptyList(), emptySet()).isEmpty())
    }

    @Test
    fun `打乱不会改变结果数量`() {
        val seeds = listOf(GuessRecommend.Seed("netease", "1", 4.0))
        val candidates = listOf((1..20).map { song("x$it", title = "t$it") })
        val plain = GuessRecommend.pick(seeds, candidates, emptySet(), shuffle = false)
        val shuffled = GuessRecommend.pick(seeds, candidates, emptySet(), shuffle = true)
        assertEquals(plain.size, shuffled.size)
        assertEquals(plain.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
    }

    // ==================== 降级 ====================

    @Test
    fun `结果不足 10 首时降级到放宽同名`() {
        val seeds = listOf(GuessRecommend.Seed("netease", "1", 10.0))
        val candidates = listOf((1..20).map { song("x$it", title = "同名") })
        val (songs, level) = GuessRecommend.pickWithFallback(
            seeds = seeds,
            candidates = candidates,
            exclude = emptySet(),
            favoritesOnlyExclude = emptySet(),
            shuffle = false,
        )
        assertEquals(1, level)
        assertEquals(10, songs.size) // 放宽后取满 10 轮
    }

    @Test
    fun `首选策略够 10 首时不降级`() {
        val seeds = listOf(GuessRecommend.Seed("netease", "1", 10.0))
        val candidates = listOf((1..20).map { song("x$it", title = "t$it") })
        val (songs, level) = GuessRecommend.pickWithFallback(
            seeds = seeds,
            candidates = candidates,
            exclude = emptySet(),
            favoritesOnlyExclude = emptySet(),
            shuffle = false,
        )
        assertEquals(0, level)
        assertEquals(10, songs.size)
    }

    // ==================== 保底池 ====================

    @Test
    fun `保底池优先同歌手`() {
        val seeds = listOf(GuessRecommend.Seed("qq", "1", 3.0, artist = "周杰伦"))
        val pool = listOf(
            song("p1", artist = "别人"),
            song("p2", artist = "周杰伦"),
            song("p3", artist = "别人2"),
        )
        val picked = GuessRecommend.pickFromPool(pool, seeds, exclude = emptySet())
        assertEquals("p2", picked.first().id)
        assertEquals(3, picked.size)
    }

    @Test
    fun `保底池也排除已知歌曲`() {
        val seeds = listOf(GuessRecommend.Seed("qq", "1", 3.0, artist = "A"))
        val pool = listOf(song("p1", artist = "A"), song("p2", artist = "B"))
        val picked = GuessRecommend.pickFromPool(pool, seeds, exclude = setOf("netease:p1"))
        assertEquals(listOf("p2"), picked.map { it.id })
    }

    @Test
    fun `保底池为空时返回空`() {
        assertTrue(
            GuessRecommend.pickFromPool(emptyList(), emptyList(), emptySet()).isEmpty(),
        )
    }

    @Test
    fun `歌曲唯一键与全工程口径一致`() {
        assertEquals("netease:123", GuessRecommend.songKey(song("123")))
        assertEquals("qq:003abc", GuessRecommend.songKey(song("003abc", platform = "qq")))
        assertEquals("netease:1", GuessRecommend.songKey(song(" 1 ", platform = " NETEASE ")))
    }
}
