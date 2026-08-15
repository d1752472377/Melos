package com.aesthetic.music.player

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.PlayerSessionStore
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.service.StatsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 播放模式（参考 App 的 PlayMode） */
enum class PlayMode { SEQUENCE, REPEAT, SHUFFLE }

/** 播放器全局状态 */
data class PlayerState(
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lyrics: List<LyricLine> = emptyList(),
    val isLoading: Boolean = false,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val errorMessage: String? = null,
    /** 最近一次播放错误（UI 弹提示；消费后清空，避免重复弹） */
    val lastError: String? = null,
    /** 倍速播放 0.5x - 2.0x */
    val speed: Float = 1f,
    /** 睡眠定时剩余毫秒；0 = 未开启 */
    val sleepRemainingMs: Long = 0,
) {
    val currentSong: Song? get() = queue.getOrNull(currentIndex)
}

/**
 * 全局播放器单例：Media3 ExoPlayer + 播放队列 + 播放模式 + 歌词 + 缓存。
 * 新增能力：断点续播、倍速播放、睡眠定时（末尾淡出）、真实听歌统计。
 */
class PlayerManager private constructor(private val appContext: Context) {

    private val repository = MusicRepository()

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext).build().apply {
            setPlaybackSpeed(SettingsStore.getPlaybackSpeed())
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(
        PlayerState(speed = SettingsStore.getPlaybackSpeed())
    )
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var progressJob: Job? = null
    private var sleepJob: Job? = null
    private var pendingSeekMs: Long = 0
    private var statsSongKey: String? = null
    private var lastSessionSaveAt = 0L

    /** 外部监听（系统悬浮胶囊 / MediaSession 等） */
    var onStateChanged: ((PlayerState) -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    val key = _state.value.currentSong?.let { it.platform + ":" + it.id }
                    if (key != null && key != statsSongKey) {
                        statsSongKey = key
                        StatsTracker.onPlayStarted(_state.value.currentSong)
                    }
                }
                notifyState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        _state.value = _state.value.copy(
                            durationMs = player.duration.takeIf { it > 0 } ?: _state.value.durationMs,
                            isLoading = false,
                        )
                        notifyState()
                    }
                    Player.STATE_BUFFERING -> {
                        _state.value = _state.value.copy(isLoading = true)
                        notifyState()
                    }
                    Player.STATE_ENDED -> onSongComplete()
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // 播放中途出错（解码失败/数据流中断）：停止并提示
                val msg = friendlyError(error)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "播放错误: ${error.errorCodeName}",
                    lastError = msg,
                )
                player.stop()
                notifyState()
            }
        })
    }

    private fun notifyState() {
        val s = _state.value
        onStateChanged?.invoke(s)
        updateFloatingCapsule(s)
    }

    /** 系统悬浮胶囊联动（参考 App：播放时显示/更新，关闭开关则隐藏） */
    private fun updateFloatingCapsule(s: PlayerState) {
        val song = s.currentSong
        if (!SettingsStore.isFloatingCapsuleEnabled()) {
            if (com.aesthetic.music.service.FloatCapsuleManager.isShowing()) {
                com.aesthetic.music.service.FloatCapsuleManager.hide()
            }
            return
        }
        if (song == null) return
        if (s.isPlaying || s.isLoading) {
            com.aesthetic.music.service.FloatCapsuleManager.show(
                context = appContext,
                title = song.title,
                artist = song.artist,
                coverUrl = song.artwork,
                isPlaying = s.isPlaying,
                onPlayPause = { toggle() },
                onTap = {},
            )
        } else {
            com.aesthetic.music.service.FloatCapsuleManager.updatePlayState(false)
        }
    }

    /** 启动系统媒体通知服务（播放时通知栏显示媒体控制） */
    private fun startPlaybackService() {
        try {
            com.aesthetic.music.service.PlaybackService.ensureChannelExists(appContext)
            val intent = android.content.Intent(appContext, com.aesthetic.music.service.PlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (_: Exception) {
        }
    }

    /** UI 消费完错误后调用，防止重复弹提示 */
    fun consumeError() {
        if (_state.value.lastError != null) {
            _state.value = _state.value.copy(lastError = null)
        }
    }

    /** 把底层异常翻译成用户可读提示（参考 App 的 _friendlyError） */
    private fun friendlyError(e: Throwable): String {
        val s = e.message ?: e.toString()
        return when {
            s.contains("404") || s.contains("版权") || s.contains("未获取到播放地址") || s.contains("空直链") ->
                "音源获取失败：可能是版权限制或无资源，换一首试试"
            s.contains("Socket") || s.contains("Connection") || s.contains("Failed host lookup") ||
                s.contains("timeout") || s.contains("网络") || s.contains("Connect") ->
                "网络异常：音源下载失败，请检查网络后重试"
            else -> "播放失败：音源可能失效，请重试或切换音质"
        }
    }

    // ==================== 断点续播 ====================

    /** App 启动时恢复上次的队列与进度（不自动出声，点播放即从断点继续） */
    fun restoreSession() {
        if (_state.value.queue.isNotEmpty()) return
        val session = PlayerSessionStore.load()
        if (session.queue.isEmpty()) return
        pendingSeekMs = session.positionMs
        _state.value = _state.value.copy(
            queue = session.queue,
            currentIndex = session.currentIndex.coerceIn(0, session.queue.lastIndex),
            positionMs = session.positionMs,
        )
        notifyState()
    }

    private fun saveSession() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        PlayerSessionStore.save(
            com.aesthetic.music.data.settings.PlayerSession(
                queue = s.queue,
                currentIndex = s.currentIndex,
                positionMs = player.currentPosition.takeIf { it > 0 } ?: s.positionMs,
            )
        )
    }

    // ==================== 队列与播放控制 ====================

    /** 播放一组歌曲（替换队列，从 index 开始） */
    fun playQueue(songs: List<Song>, index: Int = 0) {
        if (songs.isEmpty()) return
        val idx = index.coerceIn(0, songs.lastIndex)
        pendingSeekMs = 0
        _state.value = _state.value.copy(queue = songs, currentIndex = idx, isLoading = true)
        saveSession()
        playCurrent()
    }

    /** 播放单曲（替换队列） */
    fun playSingle(song: Song) = playQueue(listOf(song), 0)

    /** 添加到队列末尾（不立即播放） */
    fun addToQueue(song: Song) {
        _state.value = _state.value.copy(queue = _state.value.queue + song)
        saveSession()
        notifyState()
    }

    /** 点击队列项播放 */
    fun playQueueItem(index: Int) {
        val s = _state.value
        if (index !in s.queue.indices) return
        pendingSeekMs = 0
        _state.value = s.copy(currentIndex = index, isLoading = true)
        saveSession()
        playCurrent()
    }

    /** 从队列移除（参考 App 的 removeFromQueue：删当前项自动播下一项） */
    fun removeFromQueue(index: Int) {
        val s = _state.value
        if (index !in s.queue.indices) return
        val newQueue = s.queue.toMutableList().apply { removeAt(index) }
        var newIndex = s.currentIndex
        when {
            index < s.currentIndex -> newIndex--
            index == s.currentIndex -> {
                if (newIndex >= newQueue.size) newIndex = newQueue.size - 1
                if (newIndex >= 0) {
                    _state.value = s.copy(queue = newQueue, currentIndex = newIndex, isLoading = true)
                    saveSession()
                    playCurrent()
                    return
                } else {
                    player.stop()
                    PlayerSessionStore.clear()
                    _state.value = PlayerState(playMode = _state.value.playMode, speed = _state.value.speed)
                    notifyState()
                    return
                }
            }
        }
        _state.value = s.copy(queue = newQueue, currentIndex = newIndex)
        saveSession()
        notifyState()
    }

    /** 清空队列并停止 */
    fun clearQueue() {
        player.stop()
        PlayerSessionStore.clear()
        _state.value = PlayerState(playMode = _state.value.playMode, speed = _state.value.speed)
        notifyState()
    }

    private fun playCurrent() {
        val s = _state.value
        val song = s.currentSong ?: return
        scope.launch {
            // 重置歌词/进度
            _state.value = _state.value.copy(lyrics = emptyList(), positionMs = 0, errorMessage = null)
            val result = repository.getSongUrl(song, SettingsStore.getQuality().value)
            result.onSuccess { data ->
                val url = data.url
                if (url.isNullOrBlank()) {
                    val msg = "音源获取失败：可能是版权限制或无资源，换一首试试"
                    _state.value = _state.value.copy(isLoading = false, lastError = msg)
                    notifyState()
                    return@launch
                }

                // 缓存命中播本地秒开，未命中播在线 + 后台缓存
                val cached = withContext(Dispatchers.IO) {
                    AudioCacheService.getCachedPath(appContext, song.platform, song.id)
                }
                var playPath = url
                if (cached != null) {
                    playPath = cached
                } else {
                    AudioCacheService.cacheAudio(
                        context = appContext,
                        platformCode = song.platform,
                        songId = song.id,
                        url = url,
                        name = song.title,
                        artist = song.artist,
                        scope = scope,
                    ) { localPath ->
                        if (localPath != null) {
                            android.util.Log.d("PlayerManager", "后台缓存完成: $localPath")
                        }
                    }
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(playPath)
                    .setMediaId(song.id)
                    .setTag(song)
                    .build()
                player.setMediaItem(mediaItem)
                player.setPlaybackSpeed(_state.value.speed)
                player.setVolume(1f)
                player.prepare()
                if (pendingSeekMs > 0) {
                    player.seekTo(pendingSeekMs.coerceAtLeast(0))
                    _state.value = _state.value.copy(positionMs = pendingSeekMs)
                    pendingSeekMs = 0
                }
                startPlaybackService()
                player.play()
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, lastError = friendlyError(e))
                notifyState()
            }

            loadLyrics(song)
        }
    }

    private suspend fun loadLyrics(song: Song) {
        repository.getLyric(song).onSuccess { d ->
            val original = parseLrc(d.lyric)
            val translated = parseLrc(d.translated)
            val merged = mergeTranslation(original, translated)
            _state.value = _state.value.copy(lyrics = merged)
        }
    }

    // ==================== 播放模式 ====================

    fun togglePlayMode() {
        val next = when (_state.value.playMode) {
            PlayMode.SEQUENCE -> PlayMode.REPEAT
            PlayMode.REPEAT -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SEQUENCE
        }
        _state.value = _state.value.copy(playMode = next)
        notifyState()
    }

    private fun onSongComplete() {
        when (_state.value.playMode) {
            PlayMode.SEQUENCE -> next()
            PlayMode.REPEAT -> {
                player.seekTo(0)
                player.play()
            }
            PlayMode.SHUFFLE -> {
                val s = _state.value
                if (s.queue.size > 1) {
                    _state.value = s.copy(currentIndex = (0 until s.queue.size).random(), isLoading = true)
                    saveSession()
                    playCurrent()
                }
            }
        }
    }

    // ==================== 基础控制 ====================

    fun toggle() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount == 0) {
                _state.value.currentSong?.let {
                    _state.value = _state.value.copy(isLoading = true)
                    playCurrent()
                }
            } else {
                player.play()
            }
        }
    }

    fun next() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val idx = if (s.playMode == PlayMode.SHUFFLE && s.queue.size > 1) {
            (0 until s.queue.size).random()
        } else {
            (s.currentIndex + 1) % s.queue.size
        }
        pendingSeekMs = 0
        _state.value = s.copy(currentIndex = idx, isLoading = true)
        saveSession()
        playCurrent()
    }

    fun previous() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val idx = (s.currentIndex - 1 + s.queue.size) % s.queue.size
        pendingSeekMs = 0
        _state.value = s.copy(currentIndex = idx, isLoading = true)
        saveSession()
        playCurrent()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
        saveSession()
    }

    /** 歌词行点击跳转（参考 App：onTap lyric → seekTo(line.time)） */
    fun seekToLyric(line: LyricLine) {
        seekTo(line.timeMs)
    }

    /** 开始轮询播放进度（同时做统计与断点保存） */
    fun startProgress() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            var lastTick = SystemClock.elapsedRealtime()
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val delta = now - lastTick
                lastTick = now
                val s = _state.value
                val pos = player.currentPosition.coerceAtLeast(0)
                _state.value = s.copy(positionMs = pos)
                if (s.isPlaying && s.currentSong != null && pos > 0) {
                    StatsTracker.tick(s.currentSong!!, delta.coerceAtMost(2000), s.durationMs)
                }
                if (now - lastSessionSaveAt > 5000) {
                    lastSessionSaveAt = now
                    saveSession()
                }
                delay(500)
            }
        }
    }

    fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    // ==================== 倍速与睡眠定时 ====================

    fun setSpeed(speed: Float) {
        val v = speed.coerceIn(0.5f, 2f)
        player.setPlaybackSpeed(v)
        SettingsStore.setPlaybackSpeed(v)
        _state.value = _state.value.copy(speed = v)
        notifyState()
    }

    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) return
        sleepJob?.cancel()
        player.setVolume(1f)
        val total = minutes * 60_000L
        _state.value = _state.value.copy(sleepRemainingMs = total)
        notifyState()
        sleepJob = scope.launch {
            var remain = total
            while (isActive && remain > 0) {
                delay(1000)
                remain -= 1000
                _state.value = _state.value.copy(sleepRemainingMs = remain.coerceAtLeast(0))
                // 最后 10 秒线性淡出
                if (remain in 1..10_000) {
                    player.setVolume((remain / 10_000f).coerceIn(0f, 1f))
                }
                if (remain <= 0) {
                    player.setVolume(1f)
                    player.pause()
                    _state.value = _state.value.copy(sleepRemainingMs = 0)
                    notifyState()
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        player.setVolume(1f)
        _state.value = _state.value.copy(sleepRemainingMs = 0)
        notifyState()
    }

    fun release() {
        StatsTracker.flushNow()
        scope.launch(Dispatchers.Main) { player.release() }
    }

    companion object {
        @Volatile
        private var instance: PlayerManager? = null

        fun get(context: Context): PlayerManager =
            instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
    }
}
