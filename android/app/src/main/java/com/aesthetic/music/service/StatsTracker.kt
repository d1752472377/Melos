package com.aesthetic.music.service

import com.aesthetic.music.data.api.PlayEventDto
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.StatsStore
import java.util.UUID

/**
 * 听歌统计跟踪器：
 * - 本地统计：[StatsStore] 实时累计（总时长/次数 + 按天/按时段/按歌曲明细，本地完全自治，
 *   未登录或断网均记录；写入节流与保留窗口由 StatsStore 负责）；
 * - 云端同步：攒批收集播放事件明细，在云同步时随 syncPush 统一幂等上报。
 *
 * 两个开关（设置 → 统计设置）：
 * - `statsRecording`：关闭后本地与云端都不再记录（历史保留）；
 * - `statsCloudSync`：关闭后只本地记录，不再攒批上报事件。
 */
object StatsTracker {

    private val pendingLock = Any()
    private val pending = mutableMapOf<String, PlayEventDto>()

    /** 待上报事件条数上限：防止无限堆积 */
    private const val MAX_PENDING_KEYS = 300

    private fun recordingEnabled(): Boolean = try {
        SettingsStore.readPrefs().statsRecording
    } catch (_: Exception) {
        true
    }

    private fun cloudSyncEnabled(): Boolean = try {
        SettingsStore.readPrefs().statsCloudSync
    } catch (_: Exception) {
        true
    }

    /** 一首歌真正开始播放时计数一次（本地按天/按歌曲 + 云端事件） */
    fun onPlayStarted(song: Song?) {
        if (song == null) return
        if (!recordingEnabled()) return
        StatsStore.recordPlay(song)
    }

    /**
     * 每 500ms 由播放器调用一次：累计本地明细并按需攒批云端事件。
     *
     * @param positionMs 当前曲内位置（单日日记里"最后 00:49"的数据来源）
     */
    fun tick(song: Song, deltaMs: Long, durationMs: Long, positionMs: Long = 0L) {
        if (deltaMs <= 0) return
        if (!recordingEnabled()) return

        // 本地明细：直接累计（落盘节流 30 秒 + 保留窗口都在 StatsStore 内部，
        // 这里不再每 5 秒强制写盘——明细 JSON 比旧版大，强制写会明显加大闪存写入）
        StatsStore.recordListen(song, deltaMs, positionMs)

        // 云端事件：关闭「统计云同步」时不攒批（同步时自然不带事件）
        if (!cloudSyncEnabled()) return
        val key = "${song.platform}:${song.id}"
        val now = System.currentTimeMillis()
        synchronized(pendingLock) {
            if (pending.size >= MAX_PENDING_KEYS && !pending.containsKey(key)) {
                return
            }
            val cur = pending[key] ?: PlayEventDto(
                eventId = UUID.randomUUID().toString(),
                platform = song.platform,
                id = song.id,
                title = song.title,
                artist = song.artist,
                listenedAtMs = now,
                deviceId = com.aesthetic.music.data.sync.SyncStateStore.deviceId(),
            )
            pending[key] = cur.copy(
                listenedMs = cur.listenedMs + deltaMs,
                durationMs = if (durationMs > 0) durationMs else cur.durationMs,
            )
        }
    }

    /** 当积攒了听歌事件且满足条件时，触发云端同步（由 LibraryController 挂接） */
    var onTriggerSync: (() -> Unit)? = null

    /** 取出并清空当前积攒的听歌事件，供 syncPush 打包上报 */
    fun takePendingEvents(): List<PlayEventDto> {
        flushNow()
        if (!cloudSyncEnabled()) {
            // 用户关掉了统计云同步：清空待上报队列，避免以后重新打开时"翻旧账"
            synchronized(pendingLock) { pending.clear() }
            return emptyList()
        }
        synchronized(pendingLock) {
            val list = pending.values.toList()
            pending.clear()
            return list
        }
    }

    /** 若同步失败，将未上报成功的事件重新入队 */
    fun reenqueueEvents(events: List<PlayEventDto>) {
        if (events.isEmpty()) return
        if (!cloudSyncEnabled()) return
        synchronized(pendingLock) {
            for (e in events) {
                val key = "${e.platform}:${e.id}"
                val cur = pending[key]
                if (cur == null) {
                    if (pending.size < MAX_PENDING_KEYS) {
                        pending[key] = e
                    }
                } else {
                    pending[key] = cur.copy(
                        listenedMs = cur.listenedMs + e.listenedMs,
                        durationMs = maxOf(cur.durationMs, e.durationMs),
                    )
                }
            }
        }
    }

    /** 暂停/切歌/退出时立即落盘 */
    fun flushNow() {
        StatsStore.flush()
        synchronized(pendingLock) {
            if (pending.isNotEmpty()) {
                onTriggerSync?.invoke()
            }
        }
    }
}
