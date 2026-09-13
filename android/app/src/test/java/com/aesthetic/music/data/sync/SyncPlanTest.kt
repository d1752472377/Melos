package com.aesthetic.music.data.sync

import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 云同步基线差分逻辑的单测。
 *
 * 这里每一条都对应一个真实会咬人的场景（旧实现踩过或必然会踩）：
 *  · 别的设备的删除被本机旧快照复活
 *  · 每次同步都把全量重推一遍，增量游标失效
 *  · 本地"上限裁剪"被当成删除，把云端数据一起删掉
 *  · 删了又加回来，待删标记把刚加回来的记录删掉
 *  · 换封面（本机路径）导致永远同步不完
 */
class SyncPlanTest {

    private fun song(id: String, title: String = "夜曲", platform: String = "netease") =
        Song(id = id, title = title, artist = "周杰伦", album = "十一月的萧邦", platform = platform)

    // ==================== 键 ====================

    @Test
    fun `歌曲键包含平台 避免跨平台同 id 撞车`() {
        assertEquals(SyncKey.encode("netease", "123"), SyncKey.song(song("123")))
        assertNotEquals(SyncKey.song(song("123")), SyncKey.song(song("123", platform = "kugou")))
    }

    @Test
    fun `键可以精确还原 —— 推送删除墓碑时要靠它反解字段`() {
        val key = SyncKey.playlistSong(PlaylistKind.MINE, "local_1:2", "kugou", "hash:with:colon")
        assertEquals(listOf(PlaylistKind.MINE, "local_1:2", "kugou", "hash:with:colon"), SyncKey.decode(key))
        assertEquals(listOf("devA", "netease", "1"), SyncKey.decode(SyncKey.download("devA", "netease", "1")))
    }

    @Test
    fun `下载记录键带设备维度 两台设备互不影响`() {
        assertNotEquals(
            SyncKey.download("devA", "netease", "1"),
            SyncKey.download("devB", "netease", "1"),
        )
    }

    // ==================== 指纹 ====================

    @Test
    fun `内容相同的记录指纹相同`() {
        assertEquals(SyncFingerprint.of(song("1")), SyncFingerprint.of(song("1")))
    }

    @Test
    fun `标题变化会改变指纹`() {
        assertNotEquals(SyncFingerprint.of(song("1")), SyncFingerprint.of(song("1", title = "夜的第七章")))
    }

    @Test
    fun `指纹不含播放直链 —— 否则每次播放都会重复推送收藏`() {
        // Song 里没有 url 字段，这里用一个"仅 subtitle/artistId 不同"的构造来证明指纹只看稳定字段
        val a = song("1")
        val b = song("1").copy(subtitle = "另一个副标题", artistId = "999")
        assertEquals(SyncFingerprint.of(a), SyncFingerprint.of(b))
    }

    @Test
    fun `歌单指纹只认上传形态的封面`() {
        val p = MyPlaylist(id = "p1", name = "通勤", songs = emptyList())
        // 本机封面（绝对路径）在同步时会被丢成空串：指纹必须按空串算，避免"永远同步不完"
        val local = SyncFingerprint.of(p.copy(cover = "/data/user/0/x/files/cover/a.jpg"), "mine", null)
        val none = SyncFingerprint.of(p, "mine", null)
        assertEquals(none, local)
        // 网络封面则参与指纹
        assertNotEquals(none, SyncFingerprint.of(p, "mine", "https://cdn.example.com/c.jpg"))
    }

    @Test
    fun `脚本指纹包含源码与启用状态`() {
        val base = SyncFingerprint.ofScript("s1", "某音源", "desc", "console.log(1)", true)
        assertNotEquals(base, SyncFingerprint.ofScript("s1", "某音源", "desc", "console.log(2)", true))
        assertNotEquals(base, SyncFingerprint.ofScript("s1", "某音源", "desc", "console.log(1)", false))
    }

    // ==================== 该推什么 ====================

    @Test
    fun `与基线相同的记录不再重复推送`() {
        val local = mapOf("netease:1" to "aa", "netease:2" to "bb")
        val snapshot = SyncSnapshot(fingerprints = local)
        assertTrue(SyncPlan.changedKeys(local, snapshot).isEmpty())
    }

    @Test
    fun `新增与内容变化的记录才推送`() {
        val snapshot = SyncSnapshot(fingerprints = mapOf("netease:1" to "aa", "netease:2" to "bb"))
        val local = mapOf(
            "netease:1" to "aa",      // 未变
            "netease:2" to "bb2",     // 变了
            "netease:3" to "cc",      // 新增
        )
        assertEquals(listOf("netease:2", "netease:3"), SyncPlan.changedKeys(local, snapshot))
    }

    @Test
    fun `本地被上限裁掉的记录不会被当成删除`() {
        // 关键回归：旧实现把"本地没有"直接当删除推上去，一台设备的上限会清空云端收藏
        val snapshot = SyncSnapshot(
            fingerprints = mapOf("netease:1" to "aa", "netease:2" to "bb"),
            pendingDeletes = emptySet(),
        )
        val local = mapOf("netease:2" to "bb") // netease:1 被本地上限裁掉了
        assertEquals(emptyList<String>(), SyncPlan.effectiveDeletes(snapshot.pendingDeletes, local.keys))
        assertTrue("被裁掉的记录不该出现在待推列表里", SyncPlan.changedKeys(local, snapshot).isEmpty())
    }

    @Test
    fun `显式删除会进入待推删除列表`() {
        val snap = SyncPlan.recordDelete(SyncSnapshot(fingerprints = mapOf("netease:1" to "aa")), "netease:1")
        assertEquals(setOf("netease:1"), snap.pendingDeletes)
        assertEquals(listOf("netease:1"), SyncPlan.effectiveDeletes(snap.pendingDeletes, emptySet()))
    }

    @Test
    fun `删了又加回来 不会发墓碑`() {
        var snap = SyncSnapshot(fingerprints = mapOf("netease:1" to "aa"))
        snap = SyncPlan.recordDelete(snap, "netease:1")   // 删除
        snap = SyncPlan.recordAdd(snap, "netease:1")      // 又加回来
        assertTrue(snap.pendingDeletes.isEmpty())
        // 加回来的记录因为基线已被清空，会被当作"新增"正常推送
        val local = mapOf("netease:1" to "aa")
        assertEquals(listOf("netease:1"), SyncPlan.changedKeys(local, snap))
    }

    // ==================== 拉取后的基线推进 ====================

    @Test
    fun `应用远端更新后 不会把自己的数据再推回去`() {
        val snapshot = SyncSnapshot(fingerprints = mapOf("netease:1" to "old"))
        val pulled = SyncPlan.applyPulled(snapshot, upserts = mapOf("netease:1" to "new"))
        // 本地应用了远端内容后，指纹就是 new —— 与基线一致，因此不再回推（避免无意义的重复写入）
        val localAfterApply = mapOf("netease:1" to "new")
        assertTrue(SyncPlan.changedKeys(localAfterApply, pulled).isEmpty())
    }

    @Test
    fun `远端删除会从基线移除 本地删除后不会重复上报`() {
        val snapshot = SyncSnapshot(fingerprints = mapOf("netease:1" to "aa", "netease:2" to "bb"))
        val pulled = SyncPlan.applyPulled(snapshot, deletes = listOf("netease:1"))
        assertEquals(setOf("netease:2"), pulled.fingerprints.keys)
        // 本地也删掉它（远端已删，本地应用墓碑后同样没有）→ 不该再上报
        assertEquals(emptyList<String>(), SyncPlan.effectiveDeletes(pulled.pendingDeletes, setOf("netease:2")))
    }

    @Test
    fun `本地显式删除优先于远端更新`() {
        var snap = SyncSnapshot(fingerprints = mapOf("netease:1" to "aa"))
        snap = SyncPlan.recordDelete(snap, "netease:1") // 本机刚删
        // 另一台设备随后更新了它 → 不应该在本机复活
        assertFalse(SyncPlan.shouldApplyPulledUpsert("netease:1", snap))
        // 其它记录照常应用
        assertTrue(SyncPlan.shouldApplyPulledUpsert("netease:2", snap))
        // 并且下一次推送会把墓碑发上去（两端收敛到删除态）
        assertEquals(listOf("netease:1"), SyncPlan.effectiveDeletes(snap.pendingDeletes, emptySet()))
    }

    // ==================== 推送后的基线推进 ====================

    @Test
    fun `推送成功后基线只吸收本次推送的键`() {
        val snapshot = SyncSnapshot(
            fingerprints = mapOf("netease:1" to "aa"),
            pendingDeletes = setOf("netease:9"),
        )
        val after = SyncPlan.afterPush(
            snapshot,
            pushedUpserts = mapOf("netease:2" to "cc"),
            pushedDeletes = listOf("netease:9"),
        )
        assertEquals(mapOf("netease:1" to "aa", "netease:2" to "cc"), after.fingerprints)
        assertTrue(after.pendingDeletes.isEmpty())
    }

    @Test
    fun `推送期间发生的新改动不会被误标为已同步`() {
        // 推的是 netease:1 的旧内容；用户在推送途中改了 netease:2
        val snapshot = SyncSnapshot(fingerprints = mapOf("netease:1" to "old"))
        val after = SyncPlan.afterPush(snapshot, pushedUpserts = mapOf("netease:1" to "new"), pushedDeletes = emptyList())
        val localNow = mapOf("netease:1" to "new", "netease:2" to "changed")
        assertEquals(listOf("netease:2"), SyncPlan.changedKeys(localNow, after))
    }

    @Test
    fun `清空某集合后基线也清干净`() {
        val snapshot = SyncSnapshot(
            fingerprints = mapOf("netease:1" to "aa"),
            pendingDeletes = setOf("netease:2"),
        )
        val cleared = SyncPlan.clearCollection(snapshot, listOf("netease:1", "netease:2"))
        assertTrue(cleared.fingerprints.isEmpty())
        assertTrue(cleared.pendingDeletes.isEmpty())
    }

    @Test
    fun `集合常量覆盖需要同步的六类数据`() {
        assertEquals(
            listOf("favorites", "history", "playlists", "playlistSongs", "scripts", "downloads"),
            SyncCollections.ALL,
        )
    }

    @Test
    fun `歌单键按 kind 分域 —— 自建与收藏不会互相覆盖`() {
        assertEquals(SyncKey.encode(PlaylistKind.MINE, "local_1"), SyncKey.playlist(PlaylistKind.MINE, "local_1"))
        assertNotEquals(SyncKey.playlist(PlaylistKind.MINE, "1"), SyncKey.playlist(PlaylistKind.FAV, "1"))
    }
}
