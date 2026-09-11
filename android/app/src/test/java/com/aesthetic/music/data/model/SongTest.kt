package com.aesthetic.music.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Song.subtitleText] 的单元测试。
 *
 * 这是列表项副标题的唯一来源，被历史/收藏/歌单/搜索结果等几乎所有列表复用。
 * 逻辑很短但分支完整（歌手+专辑 / 仅歌手 / 都缺失），且带一个"未知歌手"兜底，
 * 属于典型的"写的时候对、改的时候容易漏"的小函数。
 */
class SongTest {

    @Test
    fun `歌手与专辑都存在时用间隔号拼接`() {
        val song = Song(artist = "周杰伦", album = "魔杰座")
        assertEquals("周杰伦 · 魔杰座", song.subtitleText)
    }

    @Test
    fun `只有歌手时只显示歌手`() {
        val song = Song(artist = "周杰伦", album = "")
        assertEquals("周杰伦", song.subtitleText)
    }

    @Test
    fun `歌手与专辑都为空时兜底为未知歌手`() {
        val song = Song(artist = "", album = "")
        assertEquals("未知歌手", song.subtitleText)
    }

    @Test
    fun `专辑有值但歌手为空时兜底为未知歌手`() {
        // 当前实现以歌手是否为空作为主判断，此行为需被锁定
        val song = Song(artist = "", album = "魔杰座")
        assertEquals("未知歌手", song.subtitleText)
    }

    @Test
    fun `纯空白歌手名被视为未知`() {
        val song = Song(artist = "   ", album = "")
        assertEquals("未知歌手", song.subtitleText)
    }
}
