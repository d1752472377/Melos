package com.aesthetic.music.data.sync

import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.data.api.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地数据 → 同步数据的字段规则。
 *
 * 这一层是"同步到底传了什么"的唯一出口，因此测试重点不是"能跑通"，
 * 而是**不该传的东西一条都不能传**（本机路径、播放直链），以及删除墓碑能按键反解正确。
 */
class SyncMapperTest {

    private fun song(id: String, platform: String = "netease") =
        Song(id = id, title = "夜曲", artist = "周杰伦", album = "十一月的萧邦", platform = platform)

    // ==================== 指纹 ====================

    @Test
    fun `收藏指纹按平台与歌曲id 去重`() {
        val fps = SyncMapper.favoriteFingerprints(listOf(song("1"), song("2"), song("1")))
        assertEquals(setOf(SyncKey.song("netease", "1"), SyncKey.song("netease", "2")), fps.keys)
    }

    @Test
    fun `没有 id 的脏数据不参与同步`() {
        assertTrue(SyncMapper.favoriteFingerprints(listOf(Song(id = "", title = "坏数据"))).isEmpty())
    }

    @Test
    fun `歌单封面只同步 http 地址 —— 本机图片路径传上去在别的设备必然坏图`() {
        assertNull(SyncMapper.wireCover("/data/user/0/com.aesthetic.music/files/cover/a.jpg"))
        assertNull(SyncMapper.wireCover(""))
        assertNull(SyncMapper.wireCover(null))
        assertEquals("https://cdn.example.com/a.jpg", SyncMapper.wireCover("https://cdn.example.com/a.jpg"))
    }

    @Test
    fun `歌单曲目指纹包含位置 —— 拖动排序必须能同步过去`() {
        val p1 = MyPlaylist(id = "p1", name = "通勤", songs = listOf(song("1"), song("2")))
        val p2 = p1.copy(songs = listOf(song("2"), song("1")))
        val fps1 = SyncMapper.playlistSongFingerprints(listOf(p1))
        val fps2 = SyncMapper.playlistSongFingerprints(listOf(p2))
        val key1 = SyncKey.playlistSong(PlaylistKind.MINE, "p1", "netease", "1")
        assertEquals(2, fps1.size)
        // 同一首歌换了位置，指纹必须变，否则顺序改动永远同步不出去
        assertEquals(fps1.keys, fps2.keys)
        assertTrue(fps1[key1] != fps2[key1])
    }

    @Test
    fun `下载记录指纹按设备维度区分`() {
        val item = SyncMapper.LocalDownload(platform = "netease", songId = "1", title = "夜曲", sizeBytes = 1024)
        val a = SyncMapper.downloadFingerprints(listOf(item), "devA")
        val b = SyncMapper.downloadFingerprints(listOf(item), "devB")
        assertEquals(1, a.size)
        assertEquals(1, b.size)
        assertTrue(a.keys.first() != b.keys.first())
    }

    // ==================== 增量推送 ====================

    @Test
    fun `只把我选中的键变成上传对象`() {
        val list = listOf(song("1"), song("2"), song("3"))
        val keys = listOf(SyncKey.song("netease", "2"))
        assertEquals(listOf("2"), SyncMapper.favoriteUpserts(list, keys).map { it.id })
        assertEquals(listOf("2"), SyncMapper.historyUpserts(list, keys).map { it.id })
    }

    @Test
    fun `没有变更时不产生任何上传对象`() {
        val list = listOf(song("1"))
        assertTrue(SyncMapper.favoriteUpserts(list, emptyList()).isEmpty())
        assertTrue(SyncMapper.historyUpserts(list, emptyList()).isEmpty())
        assertTrue(SyncMapper.playlistUpserts(emptyList(), emptyList(), emptyList()).isEmpty())
        assertTrue(SyncMapper.scriptUpserts(emptyList(), emptyList()).isEmpty())
        assertTrue(SyncMapper.downloadUpserts(emptyList(), "devA", "手机", emptyList()).isEmpty())
    }

    @Test
    fun `歌单上传带 kind 以区分自建与收藏`() {
        val mine = listOf(MyPlaylist(id = "m1", name = "自建"))
        val fav = listOf(PlaylistItem(id = "f1", name = "收藏的"))
        val upserts = SyncMapper.playlistUpserts(
            mine, fav,
            listOf(SyncKey.playlist(PlaylistKind.MINE, "m1"), SyncKey.playlist(PlaylistKind.FAV, "f1")),
        )
        assertEquals(2, upserts.size)
        assertEquals(setOf(PlaylistKind.MINE, PlaylistKind.FAV), upserts.map { it.kind }.toSet())
    }

    @Test
    fun `歌单曲目上传带位置`() {
        val p = MyPlaylist(id = "p1", name = "通勤", songs = listOf(song("1"), song("2")))
        val upserts = SyncMapper.playlistSongUpserts(p.let { listOf(it) }, listOf(
            SyncKey.playlistSong(PlaylistKind.MINE, "p1", "netease", "2"),
        ))
        assertEquals(1, upserts.size)
        assertEquals(1, upserts.first().position)
        assertEquals("2", upserts.first().song?.id)
        assertEquals("p1", upserts.first().playlistId)
    }

    @Test
    fun `脚本上传包含源码与启用状态`() {
        val s = SyncMapper.LocalScript(id = "s1", name = "某音源", description = "d", code = "js", enabled = false)
        val upserts = SyncMapper.scriptUpserts(listOf(s), listOf(SyncKey.script("s1")))
        assertEquals(1, upserts.size)
        assertEquals("js", upserts.first().code)
        assertFalse(upserts.first().enabled)
    }

    @Test
    fun `超大脚本不上传但会被单独识别出来`() {
        // 服务端只限制整个请求体（4MB），没有字段级上限：一个超大脚本会把一次 push 整个顶爆，
        // 连带同一次同步里其它数据都推不上去。所以必须在源头挡住，并让上层能提示用户。
        val huge = SyncMapper.LocalScript(
            id = "huge", name = "巨型脚本", description = "",
            code = "x".repeat(SyncMapper.MAX_SCRIPT_BYTES + 1), enabled = true,
        )
        val normal = SyncMapper.LocalScript(id = "ok", name = "正常脚本", description = "", code = "const a = 1", enabled = true)
        val keys = listOf(SyncKey.script("huge"), SyncKey.script("ok"))

        val upserts = SyncMapper.scriptUpserts(listOf(huge, normal), keys)
        assertEquals(1, upserts.size)
        assertEquals("ok", upserts.first().id)

        assertEquals(listOf("huge"), SyncMapper.oversizedScripts(listOf(huge, normal)).map { it.id })
        assertTrue(SyncMapper.oversizedScripts(listOf(normal)).isEmpty())
    }

    @Test
    fun `恰好等于上限的脚本仍然上传`() {
        // 边界值用「字节数」而不是字符数判断：多字节字符下按字符数算会误放行
        val edge = SyncMapper.LocalScript(id = "edge", name = "边界", description = "", code = "x".repeat(SyncMapper.MAX_SCRIPT_BYTES), enabled = true)
        assertEquals(1, SyncMapper.scriptUpserts(listOf(edge), listOf(SyncKey.script("edge"))).size)
    }

    @Test
    fun `下载记录上传带设备信息但不带本机路径`() {
        val item = SyncMapper.LocalDownload(
            platform = "kugou", songId = "h1", title = "夜曲", artist = "周杰伦",
            sizeBytes = 2048, downloadedAtMs = 123L,
        )
        val upserts = SyncMapper.downloadUpserts(listOf(item), "devA", "Pixel 8", listOf(
            SyncKey.download("devA", "kugou", "h1"),
        ))
        assertEquals(1, upserts.size)
        assertEquals("devA", upserts.first().deviceId)
        assertEquals("Pixel 8", upserts.first().deviceName)
        assertEquals(2048L, upserts.first().sizeBytes)
    }

    // ==================== 删除墓碑反解 ====================

    @Test
    fun `删除墓碑从键精确反解字段`() {
        val del = SyncMapper.favoriteDeletes(listOf(SyncKey.song("kugou", "hash:1")))
        assertEquals(1, del.size)
        assertEquals("kugou", del.first().platform)
        assertEquals("hash:1", del.first().id)

        val pls = SyncMapper.playlistDeletes(listOf(SyncKey.playlist(PlaylistKind.FAV, "123")))
        assertEquals("123", pls.first().id)

        val songs = SyncMapper.playlistSongDeletes(
            listOf(SyncKey.playlistSong(PlaylistKind.MINE, "p1", "netease", "9")),
        )
        assertEquals(1, songs.size)
        assertEquals("p1", songs.first().playlistId)
        assertEquals("netease", songs.first().platform)
        assertEquals("9", songs.first().songId)

        val scripts = SyncMapper.scriptDeletes(listOf(SyncKey.script("uuid-1")))
        assertEquals("uuid-1", scripts.first().id)

        val downloads = SyncMapper.downloadDeletes(listOf(SyncKey.download("devA", "netease", "1")))
        assertEquals("devA", downloads.first().deviceId)
        assertEquals("netease", downloads.first().platform)
        assertEquals("1", downloads.first().id)
    }

    @Test
    fun `损坏的键被丢弃而不是产出半截墓碑`() {
        assertTrue(SyncMapper.favoriteDeletes(listOf("只有一个字段")).isEmpty())
        assertTrue(SyncMapper.playlistSongDeletes(listOf("a\u0001b")).isEmpty())
        assertTrue(SyncMapper.downloadDeletes(listOf("a\u0001b")).isEmpty())
    }

    @Test
    fun `歌单封面只同步网络地址与内联图片`() {
        // 网络封面：原样传（去掉首尾空白）
        assertEquals(
            "https://p1.music.126.net/a.jpg",
            SyncMapper.wireCover("https://p1.music.126.net/a.jpg"),
        )
        assertEquals("http://x/y.jpg", SyncMapper.wireCover("  http://x/y.jpg  "))

        // 内联图片（用户自选封面压成 288px JPEG）：跟着歌单一起同步，换设备也能看到
        val inline = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ"
        assertEquals(inline, SyncMapper.wireCover(inline))
        assertEquals(inline.uppercase(), SyncMapper.wireCover(inline.uppercase()))

        // 本机绝对路径：不同步（另一台设备上必然是坏图）
        assertNull(SyncMapper.wireCover("/data/user/0/com.aesthetic.music/files/playlist_covers/cover_1.jpg"))
        assertNull(SyncMapper.wireCover("file:///sdcard/a.jpg"))
        assertNull(SyncMapper.wireCover(""))
        assertNull(SyncMapper.wireCover(null))
    }
}
