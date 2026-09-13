package com.aesthetic.music.data.sync

import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.api.ApiResponse
import com.aesthetic.music.data.api.CloudSyncPullData
import com.aesthetic.music.data.api.SyncCollection
import com.aesthetic.music.data.api.SyncDownloadKey
import com.aesthetic.music.data.api.SyncDownloadUpsert
import com.aesthetic.music.data.api.SyncPlaylistKey
import com.aesthetic.music.data.api.SyncPlaylistUpsert
import com.aesthetic.music.data.api.SyncPushPayload
import com.aesthetic.music.data.api.SyncScriptKey
import com.aesthetic.music.data.api.SyncScriptUpsert
import com.aesthetic.music.data.model.Song
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 客户端 ↔ 后端的 **wire 契约测试**（用真实服务端响应做夹具）。
 *
 * ## 这个测试防的是什么
 * 客户端的 wire 模型是 Gson 反射反序列化的 data class：**字段名差一个字母不会报错**，
 * 那个字段只会静默变成 null / 默认值。后果全是"不报错但数据不对"：
 *   · 收藏同步回来是空的（`song` 为 null 被跳过）；
 *   · 收藏的网络歌单被当成本地自建歌单（`kind` 缺失 → 默认 mine）；
 *   · 另一台设备的下载记录永远缺一条（`deviceId` 缺失 → 主键拼不出来）。
 * 只测"我自己的映射逻辑"是发现不了的——必须拿**服务端真实响应**去反序列化。
 *
 * ## 夹具从哪来
 * `app/src/test/resources/sync_pull_golden.json` 是**真实服务端**的输出，由
 * `Melos/backend/scripts/gen-client-fixture.mjs` 跑一次真实 push + pull 落盘生成。
 * 重新生成：`cd Melos/backend && node scripts/gen-client-fixture.mjs`
 * （不要手写这份 JSON：两端字段名一起写错时手写夹具照样通过，等于没测。）
 *
 * 反向（客户端 → 服务端）由 [客户端推送体的字段名与服务端约定一致] 保证：
 * 它断言序列化出来的键名，服务端就是按这些键名读的。
 */
class SyncWireContractTest {

    private fun fixture(): String =
        checkNotNull(javaClass.getResourceAsStream("/sync_pull_golden.json")) {
            "缺少测试夹具 sync_pull_golden.json（用 backend/scripts/gen-client-fixture.mjs 生成）"
        }.bufferedReader().use { it.readText() }

    private fun parsePull(): CloudSyncPullData {
        val type = object : TypeToken<ApiResponse<CloudSyncPullData>>() {}.type
        val resp: ApiResponse<CloudSyncPullData> = AppJson.gson.fromJson(fixture(), type)
        assertEquals(200, resp.code)
        return checkNotNull(resp.data) { "响应 data 解析为 null" }
    }

    @Test
    fun `真实服务端响应能被客户端模型完整解析`() {
        val d = parsePull()
        assertTrue("version 应当被解析", d.version > 0)
        assertTrue("serverTime 应当被解析", d.serverTime > 0)
        assertEquals(2, d.favorites.size)
        assertEquals(1, d.history.size)
        assertEquals(2, d.playlists.size)
        assertEquals(1, d.playlistSongs.size)
        assertEquals(1, d.scripts.size)
        assertEquals(1, d.downloads.size)
        assertEquals(1, d.playEvents.size)
    }

    @Test
    fun `收藏与历史的歌曲对象能解析出内容`() {
        val d = parsePull()
        val fav = d.favorites.first { it.id == "f1" }
        assertEquals("netease", fav.platform)
        assertNotNull("收藏的 song 不能为 null", fav.song)
        assertEquals("歌曲f1", fav.song?.title)
        assertEquals("歌手", fav.song?.artist)
        assertEquals("专辑", fav.song?.album)
        assertEquals(false, fav.deleted)

        val hist = d.history.first()
        assertEquals("h1", hist.id)
        assertEquals(1700000000000L, hist.playedAtMs)
        assertNotNull("历史的 song 不能为 null", hist.song)
    }

    @Test
    fun `歌单 kind 必须解析出 fav 而不是退化成默认 mine`() {
        val d = parsePull()
        val mine = d.playlists.first { it.id == "local_1" }
        val fav = d.playlists.first { it.id == "888" }
        assertEquals("mine", mine.kind)
        // 这条断言就是防"服务端忘了回 kind / 客户端字段改名"的：
        // 一旦解析失败，fav 会退化成默认值 "mine"，收藏的歌单会被塞进自建歌单列表。
        assertEquals("fav", fav.kind)
        assertEquals("我的通勤", mine.name)
        assertEquals("收藏的网易歌单", fav.name)
        assertEquals("https://cdn.example.com/p.jpg", fav.coverUrl)
    }

    @Test
    fun `歌单曲目带 position 与歌曲对象`() {
        val d = parsePull()
        val row = d.playlistSongs.first()
        assertEquals("local_1", row.playlistId)
        assertEquals(0, row.position)
        assertEquals("f1", row.songId)
        assertNotNull("曲目的 song 不能为 null", row.song)
    }

    @Test
    fun `音源脚本源码与启用状态能解析`() {
        val d = parsePull()
        val s = d.scripts.first()
        assertEquals("sc1", s.id)
        assertEquals("自写音源", s.name)
        assertEquals("module.exports = { search: async () => [] };", s.code)
        assertTrue("enabled 必须解析成 true（服务端返回布尔而不是 0/1）", s.enabled)
    }

    @Test
    fun `下载记录带设备维度与体积时间`() {
        val d = parsePull()
        val dl = d.downloads.first()
        assertEquals("netease", dl.platform)
        assertEquals("f1", dl.id)
        assertEquals("dev-phone", dl.deviceId)
        assertEquals("Pixel 8", dl.deviceName)
        assertEquals(4096L, dl.sizeBytes)
        assertEquals(1700000000000L, dl.downloadedAtMs)
    }

    @Test
    fun `听歌事件字段完整`() {
        val d = parsePull()
        val e = d.playEvents.first()
        assertEquals("ev1", e.eventId)
        assertEquals("f1", e.songId)
        assertEquals(30000L, e.listenedMs)
        assertEquals(200000L, e.durationMs)
        assertEquals(1700000000000L, e.listenedAtMs)
    }

    @Test
    fun `客户端推送体的字段名与服务端约定一致`() {
        // 服务端读的就是这些键（见 backend/src/lib/db.js 的 applySync）：
        // 键名一改，服务端会把整条记录当成脏数据跳过——同样不会报错，只是数据静默不入库。
        val payload = SyncPushPayload(
            favorites = SyncCollection(
                upsert = listOf(Song(id = "f1", title = "歌曲f1", artist = "歌手", platform = "netease")),
                delete = emptyList(),
            ),
            playlists = SyncCollection(
                upsert = listOf(SyncPlaylistUpsert(id = "local_1", kind = "mine", name = "通勤", coverUrl = "")),
                delete = listOf(SyncPlaylistKey(id = "888", kind = "fav")),
            ),
            scripts = SyncCollection(
                upsert = listOf(SyncScriptUpsert(id = "sc1", name = "脚本", code = "x", enabled = false)),
                delete = listOf(SyncScriptKey(id = "sc2")),
            ),
            downloads = SyncCollection(
                upsert = listOf(
                    SyncDownloadUpsert(platform = "netease", id = "f1", deviceId = "devA", deviceName = "Pixel 8", sizeBytes = 4096),
                ),
                delete = listOf(SyncDownloadKey(platform = "netease", id = "f2", deviceId = "devA")),
            ),
        )
        val json = JsonParser.parseString(AppJson.gson.toJson(payload)).asJsonObject

        val pl = json.getAsJsonObject("playlists").getAsJsonArray("upsert")[0].asJsonObject
        assertEquals("local_1", pl.get("id").asString)
        assertEquals("mine", pl.get("kind").asString)
        assertTrue("歌单删除墓碑必须带 kind", json.getAsJsonObject("playlists").getAsJsonArray("delete")[0].asJsonObject.has("kind"))

        val sc = json.getAsJsonObject("scripts").getAsJsonArray("upsert")[0].asJsonObject
        assertEquals("sc1", sc.get("id").asString)
        assertEquals("x", sc.get("code").asString)
        assertEquals(false, sc.get("enabled").asBoolean)

        val dl = json.getAsJsonObject("downloads").getAsJsonArray("upsert")[0].asJsonObject
        assertEquals("devA", dl.get("deviceId").asString)
        assertEquals(4096L, dl.get("sizeBytes").asLong)

        val dlDel = json.getAsJsonObject("downloads").getAsJsonArray("delete")[0].asJsonObject
        assertEquals("devA", dlDel.get("deviceId").asString)
        assertEquals("f2", dlDel.get("id").asString)

        val fav = json.getAsJsonObject("favorites").getAsJsonArray("upsert")[0].asJsonObject
        assertEquals("netease", fav.get("platform").asString)
        assertEquals("f1", fav.get("id").asString)
        // 未传的集合（本用例里的 history / playEvents）不应出现在 JSON 里：
        // 服务端把"缺省"解释为"本次不改动"，传空数组与传 null 语义不同。
        assertTrue("未改动的集合不该被序列化出来", !json.has("history") && !json.has("playEvents"))
    }
}
