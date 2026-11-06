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
 * - 云端同步：**会话式攒批**上报——播放期间本地计时累计，每 [SESSION_REPORT_MS]（20 秒）
 *   或暂停/切歌/退出时，把"这段累计听了几秒"合并成一条事件随 syncPush 上报。
 *
 * ## 为什么不是每秒一条
 * 旧实现把每 1~1.5 秒的心跳当独立事件上报：一首歌听 5 分钟在服务端变成 200+ 条
 * 1 秒记录，流量/电量/写入全被放大几十倍，管理端的"播放次数"也完全失真。
 * 现在每次上报一条"播放片段"事件（listenedMs = 本片段累计秒数），
 * 服务端再把同首歌的相邻片段合并成完整"播放次数"。
 *
 * 两个开关（设置 → 统计设置）：
 * - `statsRecording`：关闭后本地与云端都不再记录（历史保留）；
 * - `statsCloudSync`：关闭后只本地记录，不再攒批上报事件。
 */
object StatsTracker {

    private val pendingLock = Any()
    private val pending = mutableListOf<PlayEventDto>()

    /**
     * 当前正在累计的"播放片段"：
     * 同一首歌连续播放期间共用一个片段（一个 eventId），跨歌/暂停结束则封段。
     */
    private var openSegment: PlayEventDto? = null

    /**
     * 片段上报周期：播放持续期间，每 20 秒把当前片段封段上报一次。
     * （业界常见 10~30 秒；再长丢数据的风险窗口变大，再短请求太密。）
     */
    private const val SESSION_REPORT_MS = 20_000L

    /**
     * 片段起始时间（wall clock）。用于判断是否到了上报周期，
     * 以及服务端合并时判断"这两个片段是不是连续播放"。
     */
    private var segmentStartAt = 0L

    /** 待上报事件条数上限：防止无限堆积（离线兜底） */
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

    /** 一首歌真正开始播放时计数一次（本地按天/按歌曲 + 云端事件） */
    fun onPlayStarted(song: Song?) {
        if (song == null) return
        if (!recordingEnabled()) return
        StatsStore.recordPlay(song)
    }

    /**
     * 每 500ms 由播放器调用一次：累计本地明细并按需攒批云端事件。
     *
     * 云端侧：同一首歌连续播放共用一个片段（eventId 不变，listenedMs 累加）；
     * 片段时长达到 [SESSION_REPORT_MS] 时封段入队并开新片段。
     *
     * @param positionMs 当前曲内位置（单日日记里"最后 00:49"的数据来源）
     */
    fun tick(song: Song, deltaMs: Long, durationMs: Long, positionMs: Long = 0L) {
        if (deltaMs <= 0) return
        if (!recordingEnabled()) return

        // 本地明细：直接累计（落盘节流 30 秒 + 保留窗口都在 StatsStore 内部）
        StatsStore.recordListen(song, deltaMs, positionMs)

        // 云端事件：关闭「统计云同步」时不攒批（同步时自然不带事件）
        if (!cloudSyncEnabled()) return

        val now = System.currentTimeMillis()
        synchronized(pendingLock) {
            val cur = openSegment
            if (cur == null || cur.platform != song.platform || cur.id != song.id) {
                // 换歌了：旧片段已在封段时入队，这里直接开新片段
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

    /** 新建一个播放片段（eventId 在片段创建时固定，服务端据此幂等去重） */
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

    /**
     * 封段：把当前片段入队并清空。锁内调用。
     * listenedMs <= 0 的空片段直接丢弃（没有听就不该上报）。
     */
    private fun closeSegmentLocked() {
        val seg = openSegment ?: return
        openSegment = null
        if (seg.listenedMs <= 0) return
        if (pending.size < MAX_PENDING_EVENTS) {
            pending.add(seg)
        }
    }

    /** 当积攒了听歌事件且满足条件时，触发云端同步（由 LibraryController 挂接） */
    var onTriggerSync: (() -> Unit)? = null

    /** 取出并清空当前积攒的听歌事件，供 syncPush 打包上报 */
    fun takePendingEvents(): List<PlayEventDto> {
        flushNow()
        if (!cloudSyncEnabled()) {
            // 用户关掉了统计云同步：清空待上报队列，避免以后重新打开时"翻旧账"
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

    /**
     * 丢弃全部待上报事件（账号切换时调用）。
     *
     * 待发事件没有按账号记账：退出登录后队列里还留着上一个账号（或未登录期间）的听歌记录，
     * 若不丢弃，下一次同步会把它们上报给**新登录的账号**——
     * 表现为"这个账号里出现了我从未听过的歌"。宁可丢这一批（最长 debounce 窗口内的累计），
     * 也不能让它污染另一个账号的数据。
     */
    fun dropPendingEvents() {
        synchronized(pendingLock) {
            pending.clear()
            openSegment = null
        }
    }

    /** 若同步失败，将未上报成功的事件重新入队（同歌片段重新合并，尽量减少条数） */
    fun reenqueueEvents(events: List<PlayEventDto>) {
        if (events.isEmpty()) return
        if (!cloudSyncEnabled()) return
        synchronized(pendingLock) {
            for (e in events) {
                if (e.listenedMs <= 0) continue
                if (pending.size >= MAX_PENDING_EVENTS) break
                // 同歌同设备的失败片段重新合并成一条，降低重复上报条数
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

    /**
     * 暂停/切歌/退出时立即落盘并封段上报。
     * 暂停结束后如果继续播同一首歌，会开新片段（服务端会按相邻片段合并回来）。
     */
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

    /** 仅封段并入队（不触发同步），供暂停等"稍后一起推"的场景 */
    fun pauseSegment() {
        StatsStore.flush()
        synchronized(pendingLock) {
            closeSegmentLocked()
        }
    }
}
