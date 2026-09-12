package com.aesthetic.music.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 歌单落盘 JSON 的兼容性测试。
 *
 * 这段逻辑守着一条真实的线上风险：本项目用 Gson 反序列化 Kotlin data class，
 * Gson 绕过构造器 → JSON 里缺失的字段会留成 null，而不是 Kotlin 默认值。
 * 本次新增了 `cover` / `sourcePlatform` 等字段，旧版本落盘的数据全都没有这些键；
 * 若不补齐，`cover.isBlank()` 这类调用会在老用户升级后第一次打开歌单页时直接 NPE。
 */
class PlaylistStoreTest {

    @Test
    fun `旧版本数据缺少新字段时回落到空值而不是 null`() {
        // 这是老版本真实写出的形态：只有 id/name/songs/cloudId
        val legacy = """[{"id":"local_1","name":"老歌单","songs":[],"cloudId":""}]"""

        val list = PlaylistStore.parsePlaylists(legacy)

        assertEquals(1, list.size)
        val p = list.first()
        assertEquals("local_1", p.id)
        assertEquals("老歌单", p.name)
        assertEquals("", p.cover)
        assertEquals("", p.sourcePlatform)
        assertEquals("", p.sourcePlaylistId)
        assertEquals("", p.sourceName)
        assertFalse(p.isImported)
        // 没有封面、也没有歌曲 → 回落结果为 null，而不是抛异常
        assertNull(p.coverUrl)
    }

    @Test
    fun `字段显式为 null 时也被补齐`() {
        val broken = """[{"id":"local_2","name":"脏数据","songs":null,"cloudId":null,"cover":null}]"""

        val list = PlaylistStore.parsePlaylists(broken)

        assertEquals(1, list.size)
        assertEquals(emptyList<Any>(), list.first().songs)
        assertEquals("", list.first().cover)
        assertEquals("", list.first().cloudId)
    }

    @Test
    fun `缺少 songs 键时按空列表处理`() {
        val list = PlaylistStore.parsePlaylists("""[{"id":"local_3","name":"无歌曲字段"}]""")

        assertEquals(1, list.size)
        assertTrue(list.first().songs.isEmpty())
    }

    @Test
    fun `新字段能正常往返`() {
        val original = listOf(
            MyPlaylist(
                id = "local_9",
                name = "深夜emo",
                songs = emptyList(),
                cloudId = "",
                cover = "/data/user/0/app/files/playlist_covers/cover_1.jpg",
                sourcePlatform = "netease",
                sourcePlaylistId = "5202687076",
                sourceName = "深夜emo循环",
            )
        )
        val raw = com.aesthetic.music.data.AppJson.gson.toJson(original)

        val restored = PlaylistStore.parsePlaylists(raw).first()

        assertEquals("/data/user/0/app/files/playlist_covers/cover_1.jpg", restored.cover)
        assertEquals("netease", restored.sourcePlatform)
        assertEquals("5202687076", restored.sourcePlaylistId)
        assertEquals("深夜emo循环", restored.sourceName)
        assertTrue(restored.isImported)
        // 自定义封面优先于首曲封面
        assertEquals(restored.cover, restored.coverUrl)
    }

    @Test
    fun `没有自定义封面时回落首曲封面`() {
        val raw = """
            [{"id":"local_4","name":"带歌的歌单","songs":[
              {"id":"1","title":"歌","artist":"歌手","album":"专辑","artwork":"https://a/b.jpg","platform":"netease"}
            ],"cloudId":"","cover":""}]
        """.trimIndent()

        val p = PlaylistStore.parsePlaylists(raw).first()

        assertEquals("https://a/b.jpg", p.coverUrl)
    }

    @Test
    fun `空值与垃圾输入都返回空列表而不抛异常`() {
        assertTrue(PlaylistStore.parsePlaylists(null).isEmpty())
        assertTrue(PlaylistStore.parsePlaylists("").isEmpty())
        assertTrue(PlaylistStore.parsePlaylists("   ").isEmpty())
        assertTrue(PlaylistStore.parsePlaylists("not-json").isEmpty())
        assertTrue(PlaylistStore.parsePlaylists("""{"id":"not-array"}""").isEmpty())
    }

    @Test
    fun `缺少 id 的脏数据被丢弃`() {
        val raw = """[{"name":"没有 id"},{"id":"local_5","name":"正常"}]"""

        val list = PlaylistStore.parsePlaylists(raw)

        assertEquals(1, list.size)
        assertEquals("local_5", list.first().id)
    }
}
