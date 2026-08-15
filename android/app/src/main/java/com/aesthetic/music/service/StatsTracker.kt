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

    /** 一首歌真正开始播放时计数一次 */
    fun onPlayStarted(song: Song?) {
        if (song == null) return
        StatsStore.addPlay()
    }

    /** 每 500ms 由播放器调用一次，累计本地统计并攒批上报 */
    fun tick(song: Song, deltaMs: Long, durationMs: Long) {
        if (deltaMs <= 0) return
        StatsStore.addListen(deltaMs)
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
        val now = System.currentTimeMillis()
        if (now - lastFlushAt >= 30_000) flushNow()
    }

    fun flushNow() {
        if (pending.isEmpty()) return
        lastFlushAt = System.currentTimeMillis()
        val batch = pending.values.toList()
        pending.clear()
        if (!UserStore.isLoggedIn()) return
        scope.launch {
            repository.reportStats(batch)
        }
    }
}
