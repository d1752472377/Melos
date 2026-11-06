package com.aesthetic.music.service

import com.aesthetic.music.data.api.PlayEventDto
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.StatsStore
import java.util.UUID

object StatsTracker {

    private val pendingLock = Any()
    private val pending = mutableListOf<PlayEventDto>()

    private var openSegment: PlayEventDto? = null

    private const val SESSION_REPORT_MS = 20_000L

    private var segmentStartAt = 0L

    private const val MAX_PENDING_EVENTS = 500

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

    fun onPlayStarted(song: Song?) {
        if (song == null) return
        if (!recordingEnabled()) return
        StatsStore.recordPlay(song)
    }

    fun tick(song: Song, deltaMs: Long, durationMs: Long, positionMs: Long = 0L) {
        if (deltaMs <= 0) return
        if (!recordingEnabled()) return

        StatsStore.recordListen(song, deltaMs, positionMs)

        if (!cloudSyncEnabled()) return

        val now = System.currentTimeMillis()
        synchronized(pendingLock) {
            val cur = openSegment
            if (cur == null || cur.platform != song.platform || cur.id != song.id) {
                openSegment = newSegment(song, now)
                segmentStartAt = now
            }
            val seg = openSegment!!
            openSegment = seg.copy(
                listenedMs = seg.listenedMs + deltaMs,
                durationMs = if (durationMs > 0) durationMs else seg.durationMs,
            )
            if (now - segmentStartAt >= SESSION_REPORT_MS) {
                closeSegmentLocked()
            }
        }
    }

    private fun newSegment(song: Song, now: Long): PlayEventDto = PlayEventDto(
        eventId = UUID.randomUUID().toString(),
        platform = song.platform,
        id = song.id,
        title = song.title,
        artist = song.artist,
        listenedMs = 0L,
        listenedAtMs = now,
        deviceId = com.aesthetic.music.data.sync.SyncStateStore.deviceId(),
    )

    private fun closeSegmentLocked() {
        val seg = openSegment ?: return
        openSegment = null
        if (seg.listenedMs <= 0) return
        if (pending.size < MAX_PENDING_EVENTS) {
            pending.add(seg)
        }
    }

    var onTriggerSync: (() -> Unit)? = null

    fun takePendingEvents(): List<PlayEventDto> {
        flushNow()
        if (!cloudSyncEnabled()) {
            dropPendingEvents()
            return emptyList()
        }
        synchronized(pendingLock) {
            closeSegmentLocked()
            val list = pending.toList()
            pending.clear()
            return list
        }
    }

    fun dropPendingEvents() {
        synchronized(pendingLock) {
            pending.clear()
            openSegment = null
        }
    }

    fun reenqueueEvents(events: List<PlayEventDto>) {
        if (events.isEmpty()) return
        if (!cloudSyncEnabled()) return
        synchronized(pendingLock) {
            for (e in events) {
                if (e.listenedMs <= 0) continue
                if (pending.size >= MAX_PENDING_EVENTS) break
                val same = pending.indexOfLast {
                    it.platform == e.platform && it.id == e.id && it.deviceId == e.deviceId
                }
                if (same >= 0) {
                    val cur = pending[same]
                    pending[same] = cur.copy(
                        listenedMs = cur.listenedMs + e.listenedMs,
                        durationMs = maxOf(cur.durationMs, e.durationMs),
                    )
                } else {
                    pending.add(e)
                }
            }
        }
    }

    fun flushNow() {
        StatsStore.flush()
        val shouldTrigger = synchronized(pendingLock) {
            val had = pending.isNotEmpty()
            closeSegmentLocked()
            had || pending.isNotEmpty()
        }
        // 回调会触发同步（可能再次取待发事件），不要放在锁内调用。
        if (shouldTrigger) onTriggerSync?.invoke()
    }

    fun pauseSegment() {
        StatsStore.flush()
        synchronized(pendingLock) {
            closeSegmentLocked()
        }
    }
}
