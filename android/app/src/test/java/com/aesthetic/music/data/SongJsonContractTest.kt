package com.aesthetic.music.data

import com.aesthetic.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 歌曲模型的 JSON 持久化契约测试。
 *
 * 为什么这组测试重要：`Song` 是「历史 / 收藏 / 歌单 / 播放会话」四类本地数据
 * 以及 Retrofit 网络 DTO 的共同载体，全部经 [AppJson] 的 Gson 反射序列化落盘。
 * 而 Gson 按**字段名**反射填充——一旦有人重命名或删除字段，旧版本写下的 JSON
 * 会静默反序列化成默认值，表现为用户升级后「收藏和播放历史全没了」，
 * 且不会有任何异常抛出。
 *
 * proguard-rules.pro 里对 `Song` 的 `<init>` 与 `<fields>` 保留规则，
 * 正是为这条链路服务的；本测试则从行为侧守住同一契约。
 */
class SongJsonContractTest {

    private val gson = AppJson.gson

    @Test
    fun `完整字段往返序列化不丢数据`() {
        val original = Song(
            id = "123456",
            title = "稻香",
            artist = "周杰伦",
            album = "魔杰座",
            artwork = "https://example.com/cover.jpg",
            platform = "netease",
            subtitle = "副标题",
            artistId = "6452",
            artistPlatform = "netease",
        )

        val restored = gson.fromJson(gson.toJson(original), Song::class.java)

        assertEquals(original, restored)
    }

    @Test
    fun `可空字段为 null 时往返仍相等`() {
        val original = Song(id = "1", title = "T", artist = "A", platform = "kuwo")

        val restored = gson.fromJson(gson.toJson(original), Song::class.java)

        assertEquals(original, restored)
        assertNull(restored.artwork)
        assertNull(restored.artistId)
        assertNull(restored.artistPlatform)
    }

    @Test
    fun `列表往返保持顺序与内容`() {
        val list = listOf(
            Song(id = "1", title = "第一首", platform = "netease"),
            Song(id = "2", title = "第二首", platform = "kugou"),
            Song(id = "3", title = "第三首", platform = "qq"),
        )

        val type = object : com.google.gson.reflect.TypeToken<List<Song>>() {}.type
        val restored: List<Song> = gson.fromJson(gson.toJson(list), type)

        assertEquals(list, restored)
    }

    @Test
    fun `JSON 字段名是持久化契约的一部分`() {
        // 这些字段名一旦变更，旧版本落盘的数据将无法被新版本读回。
        // 此断言的作用是：让"改字段名"这件事必须显式改到这里，从而被 review 看见。
        //
        // 注意：AppJson 的 Gson 未开启 serializeNulls()，null 字段会被直接省略，
        // 因此这里必须给全部字段赋值，否则可空字段不会出现在 JSON 里。
        val json = gson.toJson(
            Song(
                id = "1", title = "T", artist = "A", album = "B",
                artwork = "u", platform = "p", subtitle = "s",
                artistId = "ai", artistPlatform = "ap",
            )
        )

        for (field in listOf("id", "title", "artist", "album", "artwork", "platform", "subtitle", "artistId", "artistPlatform")) {
            assertTrue("Song 的 JSON 契约缺少字段: $field", json.contains("\"$field\""))
        }
    }

    @Test
    fun `null 字段不写入 JSON 且读回仍为 null`() {
        // 锁定当前序列化行为：可空字段为 null 时被省略，而非写成 "field": null。
        // 这既省体积，也保证旧数据（缺字段）与新数据（有字段但为 null）读回结果一致。
        val json = gson.toJson(Song(id = "1", title = "T", platform = "p"))
        assertTrue("null 字段不应出现在 JSON 中", !json.contains("\"subtitle\""))

        val restored = gson.fromJson(json, Song::class.java)
        assertNull(restored.subtitle)
    }

    @Test
    fun `缺失字段的历史数据能安全反序列化并落到默认值`() {
        // 模拟"旧版本只写了部分字段"的历史 JSON
        val legacyJson = """{"id":"9","title":"老歌","platform":"netease"}"""

        val restored = gson.fromJson(legacyJson, Song::class.java)

        assertEquals("9", restored.id)
        assertEquals("老歌", restored.title)
        assertEquals("netease", restored.platform)
        assertEquals("", restored.artist)
        assertEquals("", restored.album)
    }

    @Test
    fun `未知字段被忽略而不抛异常`() {
        // 模拟"新版本写了额外字段、用户回滚到旧版本"的场景
        val futureJson = """{"id":"1","title":"T","platform":"p","futureField":"x"}"""

        val restored = gson.fromJson(futureJson, Song::class.java)

        assertEquals("1", restored.id)
    }
}
