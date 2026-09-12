package com.aesthetic.music.data

import com.aesthetic.music.data.api.PlaylistLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 歌单链接 / 分享文案解析的单元测试。
 *
 * 这段解析决定了"导入歌单"能不能用：
 * - 判错平台 → 拿网易云的 ID 去请求 QQ 接口，用户看到的是"歌单不存在"，无从排查；
 * - 认不出就把裸数字当歌单 ID → 用户粘一段普通文字也会触发一次必然失败的请求。
 * 因此这里把四平台链接、分享文案与各种非歌单输入都钉住。
 */
class PlaylistLinkParserTest {

    @Test
    fun `网易云链接带 hash 路由也能识别`() {
        val link = PlaylistLinkParser.parse("https://music.163.com/#/playlist?id=5202687076")
        assertEquals("netease", link?.platform)
        assertEquals("5202687076", link?.playlistId)
    }

    @Test
    fun `网易云短域名与带 query 的链接都能识别`() {
        val withQuery = PlaylistLinkParser.parse("https://music.163.com/playlist?id=12345678&userid=1")
        assertEquals("netease", withQuery?.platform)
        assertEquals("12345678", withQuery?.playlistId)

        val withToplist = PlaylistLinkParser.parse("https://music.163.com/#/toplist/19723756")
        assertEquals("19723756", withToplist?.playlistId)
    }

    @Test
    fun `QQ音乐链接识别`() {
        val link = PlaylistLinkParser.parse("https://y.qq.com/n/ryqq/playlist/8667696780")
        assertEquals("qq", link?.platform)
        assertEquals("8667696780", link?.playlistId)
    }

    @Test
    fun `QQ音乐 disstid 参数识别`() {
        val link = PlaylistLinkParser.parse("https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?disstid=7011264340")
        assertEquals("qq", link?.platform)
        assertEquals("7011264340", link?.playlistId)
    }

    @Test
    fun `酷狗歌单链接识别`() {
        val link = PlaylistLinkParser.parse("https://www.kugou.com/yy/special/single/1234567.html")
        assertEquals("kugou", link?.platform)
        assertEquals("1234567", link?.playlistId)
    }

    @Test
    fun `酷我歌单链接识别`() {
        val link = PlaylistLinkParser.parse("https://m.kuwo.cn/newh5app/playlist_detail/2841834985")
        assertEquals("kuwo", link?.platform)
        assertEquals("2841834985", link?.playlistId)
    }

    @Test
    fun `分享文案里能同时识别平台 歌单名与链接`() {
        val text = "分享@Melos的歌单《深夜emo循环》https://music.163.com/#/playlist?id=5202687076 来自网易云音乐（来自@网易云音乐）"
        val link = PlaylistLinkParser.parse(text)
        assertEquals("netease", link?.platform)
        assertEquals("5202687076", link?.playlistId)
        assertEquals("深夜emo循环", link?.titleHint)
        assertEquals("https://music.163.com/#/playlist?id=5202687076", link?.rawUrl)
    }

    @Test
    fun `文案里只有平台名与歌单 ID 时按该平台解析`() {
        val link = PlaylistLinkParser.parse("我在QQ音乐创建了歌单 8667696780 快来听")
        assertEquals("qq", link?.platform)
        assertEquals("8667696780", link?.playlistId)
    }

    @Test
    fun `歌单名标签写法也能作为名字提示`() {
        val link = PlaylistLinkParser.parse("歌单名：通勤电子 https://music.163.com/#/playlist?id=5202687076")
        assertEquals("通勤电子", link?.titleHint)
    }

    @Test
    fun `裸歌单 ID 保持历史行为按网易云处理`() {
        val link = PlaylistLinkParser.parse("5202687076")
        assertEquals("netease", link?.platform)
        assertEquals("5202687076", link?.playlistId)
    }

    @Test
    fun `不支持的平台链接返回 null 而不是猜一个平台`() {
        assertNull(PlaylistLinkParser.parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertNull(PlaylistLinkParser.parse("https://music.apple.com/cn/playlist/pl.abc123"))
    }

    @Test
    fun `非歌单文本返回 null`() {
        assertNull(PlaylistLinkParser.parse(""))
        assertNull(PlaylistLinkParser.parse("   "))
        assertNull(PlaylistLinkParser.parse("今天天气不错"))
        // 位数不足的数字不当成歌单 ID
        assertNull(PlaylistLinkParser.parse("1234"))
    }

    @Test
    fun `平台中文名映射完整`() {
        assertEquals("网易云音乐", PlaylistLinkParser.platformName("netease"))
        assertEquals("QQ音乐", PlaylistLinkParser.platformName("qq"))
        assertEquals("酷狗音乐", PlaylistLinkParser.platformName("kugou"))
        assertEquals("酷我音乐", PlaylistLinkParser.platformName("kuwo"))
    }
}
