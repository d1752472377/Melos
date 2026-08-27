package com.aesthetic.music.service

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.PlayEventDto
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 真实听歌统计：
 * - 本地：StatsStore 累计总时长/次数/当日时长（未登录也有效）
 * - 云端：登录后每 30 秒批量上报播放明细，Profile 展示服务端聚合结果
 */
object StatsTracker {

    private val repository = MusicRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pending = mutableMapOf<String, PlayEventDto>()
    private var lastFlushAt = 0L
    /** 本地统计内存累积值：攒够 5 秒才落盘一次（原先每 500ms 一次 Gson+SharedPreferences 往返） */
    @Volatile
    private var pendingListenMs = 0L
    @Volatile
    private var lastStatsFlushAt = 0L
    /** 待上报事件条数上限：失败重试重新入队时防止无限堆积撑爆内存 */
    private const val MAX_PENDING_KEYS = 300

    /** 一首歌真正开始播放时计数一次 */
    fun onPlayStarted(song: Song?) {
        if (song == null) return
        StatsStore.addPlay()
    }

    /** 每 500ms 由播放器调用一次，累计本地统计并攒批上报 */
    fun tick(song: Song, deltaMs: Long, durationMs: Long) {
        if (deltaMs <= 0) return
        pendingListenMs += deltaMs
        val now = System.currentTimeMillis()
        if (now - lastStatsFlushAt >= 5_000) {
            StatsStore.addListen(pendingListenMs)
            pendingListenMs = 0
            lastStatsFlushAt = now
        }
        if (!UserStore.isLoggedIn()) return
        val key = song.platform + ":" + song.id
        val cur = pending[key] ?: PlayEventDto(
            platform = song.platform,
            id = song.id,
            title = song.title,
            artist = song.artist,
        )
        pending[key] = cur.copy(
            listenedMs = cur.listenedMs + deltaMs,
            durationMs = if (durationMs > 0) durationMs else cur.durationMs,
        )
        if (now - lastFlushAt >= 30_000) flushNow()
    }

    fun flushNow() {
        // 先把未落的本地统计冲掉，保证"退出前最后一次刷新"数据完整
        if (pendingListenMs > 0) {
            StatsStore.addListen(pendingListenMs)
            pendingListenMs = 0
        }
        if (pending.isEmpty()) return
        lastFlushAt = System.currentTimeMillis()
        val batch = pending.values.toList()
        pending.clear()
        if (!UserStore.isLoggedIn()) return
        scope.launch {
            val result = repository.reportStats(batch)
            if (result.isFailure) {
                // 上报失败重新入队，下次 flush 重试；超上限丢弃最旧批次防止无限堆积。
                // pending 只允许主线程访问，回写必须切回 Main。
                withContext(Dispatchers.Main) {
                    if (pending.size < MAX_PENDING_KEYS) {
                        for (e in batch) {
                            val key = e.platform + ":" + e.id
                            val cur = pending[key]
                            pending[key] = if (cur == null) {
                                e
                            } else {
                                cur.copy(
                                    listenedMs = cur.listenedMs + e.listenedMs,
                                    durationMs = maxOf(cur.durationMs, e.durationMs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
