package com.aesthetic.music.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 按账号隔离的同步状态持久化。
 *
 * 最关键的一条是"两个账号互不干扰"：游标与基线串了账号，
 * 表现是"换账号后云端数据只回来一部分 / 旧账号的记录被当成已同步"，都属于很难复现的数据问题。
 * 单测里 SharedPreferences 不可用，store 会退化到内存镜像——这正好也验证了该退化路径不炸。
 */
class SyncStateStoreTest {

    @Test
    fun `基线与待删标记可以原样读回`() {
        val uid = 9001L
        SyncStateStore.saveSnapshot(
            uid,
            SyncCollections.FAVORITES,
            SyncSnapshot(fingerprints = mapOf("k1" to "f1"), pendingDeletes = setOf("k2")),
        )
        val got = SyncStateStore.snapshot(uid, SyncCollections.FAVORITES)
        assertEquals(mapOf("k1" to "f1"), got.fingerprints)
        assertEquals(setOf("k2"), got.pendingDeletes)
    }

    @Test
    fun `不同集合各存各的`() {
        val uid = 9002L
        SyncStateStore.saveSnapshot(uid, SyncCollections.FAVORITES, SyncSnapshot(fingerprints = mapOf("a" to "1")))
        SyncStateStore.saveSnapshot(uid, SyncCollections.HISTORY, SyncSnapshot(fingerprints = mapOf("b" to "2")))
        assertEquals(setOf("a"), SyncStateStore.snapshot(uid, SyncCollections.FAVORITES).fingerprints.keys)
        assertEquals(setOf("b"), SyncStateStore.snapshot(uid, SyncCollections.HISTORY).fingerprints.keys)
        assertTrue(SyncStateStore.snapshot(uid, SyncCollections.SCRIPTS).fingerprints.isEmpty())
    }

    @Test
    fun `游标与基线按账号隔离 换账号不会串数据`() {
        SyncStateStore.setVersion(9003L, 42L)
        SyncStateStore.setEventSince(9003L, 7L)
        SyncStateStore.saveSnapshot(9003L, SyncCollections.FAVORITES, SyncSnapshot(fingerprints = mapOf("x" to "1")))

        // 另一个账号：从零开始，看不到上一个账号的任何进度
        assertEquals(0L, SyncStateStore.version(9004L))
        assertEquals(0L, SyncStateStore.eventSince(9004L))
        assertTrue(SyncStateStore.snapshot(9004L, SyncCollections.FAVORITES).fingerprints.isEmpty())
        assertNull(SyncStateStore.lastSyncStatus(9004L))
    }

    @Test
    fun `记录删除与撤销删除`() {
        val uid = 9005L
        SyncStateStore.recordDelete(uid, SyncCollections.FAVORITES, "k1")
        SyncStateStore.recordDelete(uid, SyncCollections.FAVORITES, "k2")
        assertEquals(setOf("k1", "k2"), SyncStateStore.snapshot(uid, SyncCollections.FAVORITES).pendingDeletes)

        SyncStateStore.recordAdd(uid, SyncCollections.FAVORITES, "k1")
        assertEquals(setOf("k2"), SyncStateStore.snapshot(uid, SyncCollections.FAVORITES).pendingDeletes)
    }

    @Test
    fun `批量记录删除`() {
        val uid = 9006L
        SyncStateStore.recordDeletes(uid, SyncCollections.PLAYLIST_SONGS, listOf("a", "b", "c"))
        assertEquals(3, SyncStateStore.snapshot(uid, SyncCollections.PLAYLIST_SONGS).pendingDeletes.size)
        // 空集合不做无意义的写入
        SyncStateStore.recordDeletes(uid, SyncCollections.PLAYLIST_SONGS, emptyList())
        assertEquals(3, SyncStateStore.snapshot(uid, SyncCollections.PLAYLIST_SONGS).pendingDeletes.size)
    }

    @Test
    fun `清空某个账号只影响该账号`() {
        SyncStateStore.setVersion(9007L, 11L)
        SyncStateStore.setVersion(9008L, 22L)
        SyncStateStore.recordDelete(9007L, SyncCollections.FAVORITES, "k1")

        SyncStateStore.clearUser(9007L)

        assertEquals(0L, SyncStateStore.version(9007L))
        assertTrue(SyncStateStore.snapshot(9007L, SyncCollections.FAVORITES).pendingDeletes.isEmpty())
        // 另一个账号不受影响
        assertEquals(22L, SyncStateStore.version(9008L))
    }

    @Test
    fun `同步状态文案可以更新与读取`() {
        val uid = 9009L
        SyncStateStore.updateSyncStatus(uid, "同步成功", timeMs = 1234L)
        assertEquals("同步成功", SyncStateStore.lastSyncStatus(uid))
        assertEquals(1234L, SyncStateStore.lastSyncedAt(uid))
    }

    @Test
    fun `设备标识稳定且非空`() {
        val id = SyncStateStore.deviceId()
        assertTrue(id.isNotBlank())
        assertEquals(id, SyncStateStore.deviceId())
        // 设备标识是设备级的，不属于任何账号
        assertFalse(id.startsWith("u"))
    }
}
