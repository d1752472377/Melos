package com.aesthetic.music.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Song.artistNames] 的拆分规则测试。
 *
 * 这条规则直接决定"点歌手能不能选对人"：拆多了会把一个艺人拆成两个，
 * 拆少了合唱只会跳第一位。所以用真实出现过的几种写法做回归。
 */
class SongArtistNamesTest {

    private fun song(artist: String) = Song(title = "t", artist = artist, platform = "netease")

    @Test
    fun `单歌手不拆`() {
        assertEquals(listOf("周杰伦"), song("周杰伦").artistNames)
        assertEquals(listOf("G.E.M.邓紫棋"), song("G.E.M.邓紫棋").artistNames)
    }

    @Test
    fun `斜杠分隔的合唱按斜杠拆`() {
        // 网易云/QQ/酷狗都是 joinToString("/")
        assertEquals(listOf("华晨宇", "G.E.M.邓紫棋"), song("华晨宇/G.E.M.邓紫棋").artistNames)
        assertEquals(listOf("A", "B", "C"), song("A/B/C").artistNames)
    }

    @Test
    fun `斜杠两侧有空格也能拆`() {
        assertEquals(listOf("周杰伦", "温岚"), song("周杰伦 / 温岚").artistNames)
    }

    @Test
    fun `酷我那种紧凑的 and 符号要拆开`() {
        // 酷我返回"吴宗宪&温岚"这种没有空格的写法
        assertEquals(listOf("吴宗宪", "温岚"), song("吴宗宪&温岚").artistNames)
    }

    @Test
    fun `带空格的 and 是完整艺名不能拆`() {
        // "Simon & Garfunkel" 本身就是一个组合
        assertEquals(listOf("Simon & Garfunkel"), song("Simon & Garfunkel").artistNames)
        assertEquals(listOf("Earth, Wind & Fire"), song("Earth, Wind & Fire").artistNames)
    }

    @Test
    fun `顿号也当分隔符`() {
        assertEquals(listOf("周杰伦", "费玉清"), song("周杰伦、费玉清").artistNames)
    }

    @Test
    fun `空字符串与空白名字返回空列表`() {
        assertTrue(song("").artistNames.isEmpty())
        assertTrue(song("   ").artistNames.isEmpty())
    }

    @Test
    fun `重复名字去重`() {
        assertEquals(listOf("周杰伦", "温岚"), song("周杰伦/温岚/周杰伦").artistNames)
    }

    @Test
    fun `去掉每段首尾空白并丢弃空段`() {
        assertEquals(listOf("A", "B"), song(" A /  / B ").artistNames)
    }

    @Test
    fun `primaryArtistId 只认非空`() {
        assertEquals("123", Song(artist = "x", artistId = "123").primaryArtistId)
        assertEquals(null, Song(artist = "x", artistId = "").primaryArtistId)
        assertEquals(null, Song(artist = "x", artistId = null).primaryArtistId)
    }
}
