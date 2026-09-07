package com.aesthetic.music.service

import com.aesthetic.music.data.api.PlayEventDto
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.StatsStore
import java.util.UUID

/**
 * 听歌统计跟踪器：
 * - 本地统计：StatsStore 实时累计总时长/总次数/当日时长（本地完全自治，未登录或断网均实时落盘）；
 * - 云端同步：攒批收集播放事件明细，在云同步时随 syncPush 统一幂等上报。
 */
object StatsTracker {

    private val pendingLock = Any()
    private val pending = mutableMapOf<String, PlayEventDto>()

    /** 本地统计内存累积值：攒够 5 秒才落盘一次 */
    @Volatile
    private var pendingListenMs = 0L
    @Volatile
    private var lastStatsFlushAt = 0L

    /** 待上报事件条数上限：防止无限堆积 */
    private const val MAX_PENDING_KEYS = 300

    /** 一首歌真正开始播放时计数一次 */
    fun onPlayStarted(song: Song?) {
        if (song == null) return
        StatsStore.addPlay()
    }

    /** 每 500ms 由播放器调用一次，累计本地统计并攒批明细事件 */
    fun tick(song: Song, deltaMs: Long, durationMs: Long) {
        if (deltaMs <= 0) return
        pendingListenMs += deltaMs
        val now = System.currentTimeMillis()
        if (now - lastStatsFlushAt >= 5_000) {
            StatsStore.addListen(pendingListenMs)
            pendingListenMs = 0
            lastStatsFlushAt = now
        }

        val key = "${song.platform}:${song.id}"
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
            )
            pending[key] = cur.copy(
                listenedMs = cur.listenedMs + deltaMs,
                durationMs = if (durationMs > 0) durationMs else cur.durationMs,
            )
        }
    }

    /** 取出并清空当前积攒的听歌事件，供 syncPush 打包上报 */
    fun takePendingEvents(): List<PlayEventDto> {
        flushNow()
        synchronized(pendingLock) {
            val list = pending.values.toList()
            pending.clear()
            return list
        }
    }

    /** 若同步失败，将未上报成功的事件重新入队 */
    fun reenqueueEvents(events: List<PlayEventDto>) {
        if (events.isEmpty()) return
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

    fun flushNow() {
        if (pendingListenMs > 0) {
            StatsStore.addListen(pendingListenMs)
            pendingListenMs = 0
            lastStatsFlushAt = System.currentTimeMillis()
        }
    }
}
