package com.aesthetic.music.ui.library

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.PlaylistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「导入歌单」落到收藏歌单、而不是混进自建歌单 —— 这是产品语义边界，钉住它。
 *
 * 说明：`FavoritePlaylistStore` / `PlaylistStore` 在未 init 时读写都是安全的空操作，
 * 因此这里可以直接构造控制器验证内存态行为。
 */
class LibraryControllerCollectTest {

    private fun controller(): LibraryController =
        LibraryController(
            repository = MusicRepository(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            onToast = {},
        )

    private fun item(id: String, name: String) = PlaylistItem(
        id = id,
        name = name,
        coverUrl = "https://example.com/$id.jpg",
        trackCount = 12,
        platform = "netease",
    )

    @Test
    fun `导入收藏只进收藏歌单不动自建歌单`() {
        val c = controller()

        c.collectPlaylist(item("5202687076", "深夜emo"))

        assertEquals(listOf("5202687076"), c.favoritePlaylists.value.map { it.id })
        assertTrue(c.isPlaylistFavorite("5202687076"))
        assertTrue("导入不应创建自建歌单", c.myPlaylists.value.isEmpty())
    }

    @Test
    fun `重复导入同一歌单不会取消收藏只刷新信息`() {
        val c = controller()

        c.collectPlaylist(item("1", "旧名字"))
        c.collectPlaylist(item("1", "新名字"))

        val list = c.favoritePlaylists.value
        assertEquals("重复导入应保持一条", 1, list.size)
        assertEquals("新名字", list.single().name)
        assertTrue(c.isPlaylistFavorite("1"))
    }

    @Test
    fun `新导入的歌单排在收藏列表最前`() {
        val c = controller()

        c.collectPlaylist(item("1", "第一个"))
        c.collectPlaylist(item("2", "第二个"))

        assertEquals(listOf("2", "1"), c.favoritePlaylists.value.map { it.id })
    }

    @Test
    fun `收藏开关与导入语义不同_开关会取消收藏`() {
        val c = controller()

        c.collectPlaylist(item("1", "歌单"))
        c.toggleFavoritePlaylist(item("1", "歌单"))

        assertEquals(emptyList<String>(), c.favoritePlaylists.value.map { it.id })
        assertFalse(c.isPlaylistFavorite("1"))
    }
}
