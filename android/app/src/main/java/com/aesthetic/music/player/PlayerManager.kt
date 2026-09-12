package com.aesthetic.music.player

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.PlayerSessionStore
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.UserPrefs
import com.aesthetic.music.service.FloatCapsuleManager
import com.aesthetic.music.service.MediaCardArtwork
import com.aesthetic.music.service.StatsTracker
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import kotlinx.coroutines.CancellationException
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

/** 淡入淡出每步时长（10 步 ≈ 300ms，短到不影响手感，长到听不出突跳） */
private const val FADE_STEP_MS = 30L

/**
 * 高频播放进度：每 500ms 更新一次。
 * 与低频 [PlayerState] 分离——只有底部 Dock 进度环 / 播放页底部控件订阅它，
 * 进度跳动不再带动 AppShell 及整棵内容树重组。
 */
data class PlaybackProgress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/** 播放器全局状态（歌级/低频状态；不含高频进度，见 [PlaybackProgress]） */
// @Immutable：本类所有更新都走 copy() 整体替换、从不原地改集合，
// 标注后 Compose 可把接收它的子组件视为可跳过
@androidx.compose.runtime.Immutable
data class PlayerState(
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val lyrics: List<LyricLine> = emptyList(),
    val syncedLyrics: SyncedLyrics? = null,
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
        // handleAudioFocus=true：来电/导航/其他 App 播放时自动让出焦点（暂停或降音量）；
        // setHandleAudioBecomingNoisy(true)：拔耳机（AUDIO_BECOMING_NOISY）自动暂停。
        // 此前两者均未启用：通话中/拔耳机后音乐会继续外放
        ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setPlaybackSpeed(SettingsStore.getPlaybackSpeed())
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 交给 [androidx.media3.session.MediaSession] 的播放器（见 [MelosSessionPlayer]）。
     *
     * 为什么不能直接把 [player] 交给 MediaSession：ExoPlayer 的 timeline 里永远只有当前这一首，
     * 系统据此认为"没有下一首"，于是通知栏/锁屏/超级岛/蓝牙上的"下一首"被静默禁用。
     * 包装后按 App 自己的队列（[PlayerState.queue]）对系统声明可切歌，
     * 并且**切歌动作本身也由它执行**（命令一定会落到 player 上，不依赖已废弃的会话回调）。
     */
    val sessionPlayer: Player by lazy {
        MelosSessionPlayer(
            player,
            hasQueueNavigation = { _state.value.queue.size > 1 },
            onNext = { next() },
            onPrevious = { previous() },
        )
    }

    private val _state = MutableStateFlow(
        PlayerState(speed = SettingsStore.getPlaybackSpeed())
    )
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** 高频进度流：仅底部 Dock 进度环 / PlayerScreen 底部控件订阅 */
    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private var progressJob: Job? = null
    private var sleepJob: Job? = null
    private var pendingSeekMs: Long = 0
    private var statsSongKey: String? = null
    private var lastSessionSaveAt = 0L
    /** 上次悬浮胶囊展示签名（歌曲+播放状态），用于跳过重复的窗口刷新 */
    private var lastCapsuleSignature: String? = null
    /**
     * 播放请求代数：每次 playCurrent 递增。
     * 快速连点"上一首/下一首"时，旧请求（网络慢）返回后凭代数判断已过期，
     * 不再 setMediaItem 覆盖新歌，杜绝"UI 显示 B 实际播 A"的串台。
     */
    private var playGeneration = 0L
    /** 在途的取直链/播放任务：切歌时取消，避免过期请求继续耗流量 */
    private var playJob: Job? = null

    /** 外部监听（系统悬浮胶囊 / MediaSession 等） */
    var onStateChanged: ((PlayerState) -> Unit)? = null

    // ==================== 用户设置（设置页 → 播放/歌词/下载） ====================

    /** 当前设置快照：播放链路每处判断都读它，改设置后由 [applyUserPrefs] 刷新生效 */
    @Volatile
    private var userPrefs: UserPrefs = SettingsStore.readPrefs()

    /** 淡入淡出用的音量渐变任务（切换歌曲时取消上一个） */
    private var fadeJob: Job? = null

    /** 因麦克风被占用而暂停：占用结束后按需自动恢复 */
    private var pausedForRecording = false

    /** 录音占用监听（仅「混合播放」开启时注册） */
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    private val audioManager: AudioManager?
        get() = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** 已预加载的下一首 key，避免同一首反复预取 */
    private var preloadedKey: String? = null

    /**
     * 应用设置页的变更（主题之外的播放/歌词/下载项）。
     *
     * 只在"需要真的动播放器"的地方生效：
     * - 混合播放 → 重新注册音频属性（是否独占焦点）
     * - 歌词来源/翻译 → 重新加载当前歌曲歌词
     * 其余（预加载、淡入淡出、下载策略）在各自的执行点读 [userPrefs]，无需重启播放。
     */
    fun applyUserPrefs(prefs: UserPrefs) {
        val old = userPrefs
        userPrefs = prefs
        applyAudioFocusMode(prefs)
        if (old.lyricOnline != prefs.lyricOnline ||
            old.lyricLocal != prefs.lyricLocal ||
            old.lyricTranslation != prefs.lyricTranslation
        ) {
            _state.value.currentSong?.let { song ->
                val gen = playGeneration
                scope.launch { loadLyrics(song, gen) }
            }
        }
    }

    /**
     * 混合播放：开启后不再独占音频焦点（与其他 App 同时出声），改为监听麦克风占用；
     * 关闭时回到 ExoPlayer 默认的焦点让出行为（来电/其他播放器会暂停本 App）。
     */
    private fun applyAudioFocusMode(prefs: UserPrefs) {
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // handleAudioFocus = !mixWithOthers
                !prefs.mixWithOthers,
            )
        }
        if (prefs.mixWithOthers) registerRecordingWatcher() else unregisterRecordingWatcher()
    }

    private fun registerRecordingWatcher() {
        if (recordingCallback != null) return
        val cb = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                val recording = configs.isNotEmpty()
                if (recording) {
                    // 麦克风被占用（通话/录音/语音助手）：混合播放模式下必须让路，否则会串音
                    if (player.isPlaying) {
                        pausedForRecording = true
                        runCatching { player.pause() }
                    }
                } else if (pausedForRecording) {
                    pausedForRecording = false
                    runCatching { player.play() }
                }
            }
        }
        recordingCallback = cb
        runCatching { audioManager?.registerAudioRecordingCallback(cb, Handler(Looper.getMainLooper())) }
    }

    private fun unregisterRecordingWatcher() {
        val cb = recordingCallback ?: return
        recordingCallback = null
        pausedForRecording = false
        runCatching { audioManager?.unregisterAudioRecordingCallback(cb) }
    }

    /** 淡入：播放开始时从静音升到正常音量（关闭淡入淡出则直接满音量） */
    private fun fadeIn() {
        if (!userPrefs.crossFade) {
            player.volume = 1f
            return
        }
        fadeJob?.cancel()
        player.volume = 0f
        fadeJob = scope.launch {
            val steps = 10
            for (i in 1..steps) {
                player.volume = i / steps.toFloat()
                delay(FADE_STEP_MS)
            }
            player.volume = 1f
        }
    }

    /** 淡出后暂停：避免"啪"的一声直接断掉 */
    private fun fadeOutThenPause() {        if (!userPrefs.crossFade) {
            player.pause()
            return
        }
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val from = player.volume
            val steps = 8
            for (i in 1..steps) {
                player.volume = from * (1f - i / steps.toFloat())
                delay(FADE_STEP_MS)
            }
            player.volume = 0f
            player.pause()
            player.volume = 1f
        }
    }

    /** 预加载下一首：把下一首音频后台缓存下来，切歌时直接命中本地（弱网/走动时更稳） */
    private fun preloadNextIfEnabled() {
        if (!userPrefs.preloadNext) return
        val s = _state.value
        if (s.queue.size <= 1) return
        val next = s.queue.getOrNull(s.currentIndex + 1) ?: return
        if (next.id.isBlank()) return
        val key = next.platform + ":" + next.id
        if (key == preloadedKey) return
        if (!DownloadPolicy.allowed(appContext, userPrefs, manual = false)) return
        preloadedKey = key
        scope.launch {
            val quality = SettingsStore.getQuality().value
            val cached = withContext(Dispatchers.IO) {
                AudioCacheService.getCachedPath(appContext, next.platform, next.id, quality)
            }
            if (cached != null) return@launch
            val url = repository.getSongUrl(next, quality).getOrNull()?.url ?: return@launch
            AudioCacheService.cacheAudio(
                context = appContext,
                platformCode = next.platform,
                songId = next.id,
                quality = quality,
                url = url,
                name = next.title,
                artist = next.artist,
                scope = scope,
            ) { path -> if (path != null) saveSidecars(next) }
        }
    }

    /**
     * 下载后补封面与歌词副文件（「下载与缓存 → 封面与歌词」开启时）。
     * 放在缓存成功之后做，避免为了副文件多做一次无谓的网络请求。
     */
    private fun saveSidecars(song: Song) {
        if (!userPrefs.downloadCoverLyrics) return
        scope.launch {
            runCatching {
                if (userPrefs.lyricOnline) {
                    repository.getLyric(song).getOrNull()?.let { d ->
                        LocalLyricStore.saveLocalLyric(appContext, song, d.lyric)
                    }
                }
                SongSidecarStore.saveCover(appContext, song)
            }
        }
    }

    /** 歌曲真正开始播放后回调（写"最近播放"等用）。
     *  此前"点击即写历史"：取直链失败的歌也会入历史，统计口径被污染 */
    var onPlaybackStarted: ((Song) -> Unit)? = null
    /** 一首歌完整播完（FM 漂移信号；不区分模式，单曲循环/切歌也各计一次） */
    var onSongCompleted: ((Song) -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    // 淡入：从静音升到正常音量，避免每次起播"啪"一声
                    fadeIn()
                    val s = _state.value
                    val song = s.currentSong
                    val key = song?.let { it.platform + ":" + it.id }
                    if (key != null && key != statsSongKey) {
                        statsSongKey = key
                        StatsTracker.onPlayStarted(song)
                        // 真正起播才写"最近播放"（此前"点击即写历史"，取直链失败的歌也入历史）
                        if (song != null) onPlaybackStarted?.invoke(song)
                    }
                    // 起播后预取下一首（设置里可关）
                    preloadNextIfEnabled()
                } else {
                    // 暂停瞬间立即落一次断点：轮询循环暂停时降频不再保存，这里保证暂停位置不丢
                    saveSession()
                }
                notifyState()
            }

            /**
             * 音频焦点丢失 → 实现「打断自动恢复」开关。
             *
             * 已核对的 API 事实：media3 1.4.1 的 Player 只有
             * PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS，没有 *_AUDIO_FOCUS_GAIN，
             * 所以无法在"焦点恢复"那一刻判断是系统恢复还是用户点的。
             * 换个更稳的做法：关掉开关时，把系统因焦点丢失产生的暂停"转成"用户暂停
             * ——ExoPlayer 只对"自己因焦点丢失暂停"的播放做自动恢复，
             * 一旦收到显式 pause() 就不会再自己续播，正好等于"打断后不自动恢复"。
             * 开关打开时不做任何事，即保持 ExoPlayer 默认的自动恢复行为。
             */
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS &&
                    !playWhenReady &&
                    !userPrefs.resumeAfterInterrupt
                ) {
                    // 显式暂停：焦点恢复后停在暂停态，等用户手动点播放
                    runCatching { player.pause() }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        val duration = player.duration.takeIf { it > 0 } ?: _progress.value.durationMs
                        _progress.value = _progress.value.copy(durationMs = duration)
                        _state.value = _state.value.copy(isLoading = false)
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

    /** 系统悬浮胶囊联动（参考 App：播放时显示/更新，关闭开关则隐藏）。
     *  节流：仅在"歌曲或播放状态真正变化"时才触碰悬浮窗；
     *  进度轮询等高频状态更新不会引发 WindowManager 操作。 */
    private fun updateFloatingCapsule(s: PlayerState) {
        val song = s.currentSong
        if (!SettingsStore.isFloatingCapsuleEnabled()) {
            if (lastCapsuleSignature != null || FloatCapsuleManager.isShowing()) {
                lastCapsuleSignature = null
                FloatCapsuleManager.hide()
            }
            return
        }
        if (song == null) return
        if (s.isPlaying || s.isLoading) {
            val signature = "${song.platform}:${song.id}|playing=${s.isPlaying}"
            // 同一首歌同一播放状态：悬浮窗已在展示，跳过重复刷新
            if (signature == lastCapsuleSignature && FloatCapsuleManager.isShowing()) return
            lastCapsuleSignature = signature
            FloatCapsuleManager.show(
                context = appContext,
                title = song.title,
                artist = song.artist,
                coverUrl = song.artwork,
                isPlaying = s.isPlaying,
                onPlayPause = { toggle() },
                onTap = {},
            )
        } else {
            // 暂停：只把按钮图标切为"播放"，无窗口操作
            lastCapsuleSignature = null
            if (FloatCapsuleManager.isShowing()) {
                FloatCapsuleManager.updatePlayState(false)
            }
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

    /** App 启动时恢复上次的队列与进度（不自动出声，点播放即从断点继续）。
     *  队列 JSON 的磁盘读取与 Gson 解析在 IO 线程完成，避免冷启动主线程卡顿。 */
    fun restoreSession() {
        if (_state.value.queue.isNotEmpty()) return
        scope.launch(Dispatchers.IO) {
            val session = PlayerSessionStore.load()
            withContext(Dispatchers.Main) {
                // 解析期间用户可能已手动开始播放，此时丢弃旧断点
                if (_state.value.queue.isNotEmpty()) return@withContext
                if (session.queue.isEmpty()) return@withContext
                pendingSeekMs = session.positionMs
                _progress.value = PlaybackProgress(positionMs = session.positionMs, durationMs = _progress.value.durationMs)
                _state.value = _state.value.copy(
                    queue = session.queue,
                    currentIndex = session.currentIndex.coerceIn(0, session.queue.lastIndex),
                )
                notifyState()
            }
        }
    }

    /** 保存断点。positionOverride 供切歌路径显式传 0：
     *  此前 next/previous/playQueue 都是"先 saveSession 再 playCurrent"，存下的是
     *  "新歌 index + 旧歌进度"，进程在其后 ≤5s 被杀则新歌恢复到旧歌位置（常近曲尾），
     *  进 READY 即触发 STATE_ENDED 的暂停逻辑 */
    private fun saveSession(positionOverride: Long? = null) {
        val s = _state.value
        if (s.queue.isEmpty()) return
        PlayerSessionStore.save(
            com.aesthetic.music.data.settings.PlayerSession(
                queue = s.queue,
                currentIndex = s.currentIndex,
                positionMs = positionOverride ?: player.currentPosition.takeIf { it > 0 } ?: _progress.value.positionMs,
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
        saveSession(positionOverride = 0L) // 新歌从头播，不能存下旧歌进度
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

    /** 下一首播放（插入到当前播放歌曲之后） */
    fun playNext(song: Song) {
        val s = _state.value
        if (s.queue.isEmpty()) {
            playSingle(song)
            return
        }
        val targetIdx = (s.currentIndex + 1).coerceAtMost(s.queue.size)
        val newQueue = s.queue.toMutableList().apply { add(targetIdx, song) }
        _state.value = s.copy(queue = newQueue)
        saveSession()
        notifyState()
    }

    /** 点击队列项播放 */
    fun playQueueItem(index: Int) {
        val s = _state.value
        if (index !in s.queue.indices) return
        pendingSeekMs = 0
        _state.value = s.copy(currentIndex = index, isLoading = true)
        saveSession(positionOverride = 0L)
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
                    saveSession(positionOverride = 0L) // 切到新歌，从头播
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
        AudioCacheService.setProtectedPath(null)
        _state.value = PlayerState(playMode = _state.value.playMode, speed = _state.value.speed)
        notifyState()
    }

    private fun playCurrent() {
        val s = _state.value
        val song = s.currentSong ?: return
        val gen = ++playGeneration
        // 重置歌词/进度
        _progress.value = PlaybackProgress()
        _state.value = _state.value.copy(lyrics = emptyList(), syncedLyrics = null, errorMessage = null, isLoading = true)
        // 取消上一首的在途取链请求（此前只靠代数丢弃结果，旧请求继续跑完白白耗流量）
        playJob?.cancel()
        playJob = scope.launch {
            val quality = SettingsStore.getQuality().value

            // 先查本地缓存：命中直接播本地，不碰网络。
            // 此前是"先联网取直链、成功后才查缓存"——飞行模式/直链接口故障时
            // 已完整缓存的歌照样播不了，离线缓存价值为零
            val cached = withContext(Dispatchers.IO) {
                AudioCacheService.getCachedPath(appContext, song.platform, song.id, quality)
            }
            if (gen != playGeneration) return@launch
            val onlineUrl: String? = if (cached != null) null else {
                val r = repository.getSongUrl(song, quality)
                if (r.isFailure) {
                    if (gen == playGeneration) {
                        _state.value = _state.value.copy(isLoading = false, lastError = friendlyError(r.exceptionOrNull()!!))
                        notifyState()
                    }
                }
                r.getOrNull()?.url
            }
            if (gen != playGeneration) return@launch
            if (cached == null && onlineUrl.isNullOrBlank()) return@launch // 错误已提示

            val playPath = cached ?: onlineUrl!!
            // 受保护路径 = 正在播放的本地缓存文件；在线播放时上一首的缓存文件不再受保护
            AudioCacheService.setProtectedPath(cached)
            if (cached == null) {
                // 未命中：后台缓存（不阻塞播放）。上方 395 行已保证 onlineUrl 非空，这里再兜一层
                val downloadUrl = onlineUrl ?: return@launch
                AudioCacheService.cacheAudio(
                    context = appContext,
                    platformCode = song.platform,
                    songId = song.id,
                    quality = quality,
                    url = downloadUrl,
                    name = song.title,
                    artist = song.artist,
                    scope = scope,
                ) { localPath ->
                    if (localPath != null) {
                        android.util.Log.d("PlayerManager", "后台缓存完成: $localPath")
                        // 「下载与缓存 → 封面与歌词」开启时补存封面/歌词副文件
                        saveSidecars(song)
                    }
                }
            }

            // MediaMetadata：通知栏/锁屏/蓝牙 AVRCP 的歌名歌手封面（此前为空，媒体通知全是空壳）
            // artworkData 必须给：Android 13+ 的媒体卡片用元数据里的图片字节画封面，
            // 只给 https 的 artworkUri 时 SystemUI（另一个进程）取不到，卡片就是"空的"。
            // 这里只做 best-effort（封面已缓存才有字节），不做等待：为了封面拖慢起播不值得。
            val artworkBytes = MediaCardArtwork.artworkBytes(appContext, song.artwork)
            val mediaItem = MediaItem.Builder()
                .setUri(playPath)
                .setMediaId(song.id)
                .setTag(song)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.artwork?.let { runCatching { Uri.parse(it) }.getOrNull() })
                        .apply {
                            if (artworkBytes != null) {
                                setArtworkData(
                                    artworkBytes,
                                    MediaMetadata.PICTURE_TYPE_FRONT_COVER,
                                )
                            }
                        }
                        .build()
                )
                .build()
            player.setMediaItem(mediaItem)
            player.setPlaybackSpeed(_state.value.speed)
            player.setVolume(1f)
            player.prepare()
            if (pendingSeekMs > 0) {
                player.seekTo(pendingSeekMs.coerceAtLeast(0))
                _progress.value = PlaybackProgress(positionMs = pendingSeekMs, durationMs = _progress.value.durationMs)
                pendingSeekMs = 0
            }
            startPlaybackService()
            player.play()

            if (gen != playGeneration) return@launch
            loadLyrics(song, gen)
        }
    }

    /**
     * 加载当前歌曲歌词。
     *
     * 歌词来源按设置走（设置 → 歌词显示）：
     * - 两个来源都关 = 不显示歌词，且不发起任何请求；
     * - 开本地 = 先找本地歌词文件（缓存歌曲同目录 .lrc，或 filesDir/lyrics 下的已保存歌词），命中就离线用；
     * - 开在线 = 走仓库取歌词（失败/为空时不覆盖已有歌词，避免"网络抖一下歌词就空"）。
     */
    private suspend fun loadLyrics(song: Song, gen: Long) {
        val prefs = userPrefs
        if (!prefs.lyricOnline && !prefs.lyricLocal) {
            clearLyrics(gen)
            return
        }
        if (prefs.lyricLocal) {
            val local = withContext(Dispatchers.IO) {
                val cached = AudioCacheService.getCachedPath(
                    appContext, song.platform, song.id, SettingsStore.getQuality().value,
                )
                LocalLyricStore.loadLocalLyric(appContext, song, cached)
            }
            if (!local.isNullOrBlank()) {
                applyLyrics(local, null, gen)
                return
            }
        }
        if (!prefs.lyricOnline) {
            // 只开本地却没有本地文件：清空而不是联网（用户的目标就是纯离线）
            clearLyrics(gen)
            return
        }
        repository.getLyric(song).onSuccess { d ->
            applyLyrics(d.lyric, d.translated, gen)
        }
    }

    /** 清空歌词（来源被关掉，或本地没有可用歌词） */
    private fun clearLyrics(gen: Long) {
        if (gen != playGeneration) return
        _state.value = _state.value.copy(lyrics = emptyList(), syncedLyrics = null)
        notifyState()
    }

    /** 解析并落地歌词：关闭「翻译歌词」时丢弃译文，只保留原文 */
    private fun applyLyrics(rawLyricIn: String?, rawTranslatedIn: String?, gen: Long) {
        if (gen != playGeneration) return
        val rawLyric = rawLyricIn ?: ""
        val rawTranslated = if (userPrefs.lyricTranslation) rawTranslatedIn else null
        val original = parseLrc(rawLyric)
        val translated = if (rawTranslated.isNullOrBlank()) emptyList() else parseLrc(rawTranslated)
        val merged = mergeTranslation(original, translated)

        val contentToParse = if (!rawTranslated.isNullOrBlank() && !rawLyric.contains(rawTranslated)) {
            "$rawLyric\n$rawTranslated"
        } else {
            rawLyric
        }
        val parsedSynced = if (contentToParse.isNotBlank()) {
            try {
                AutoParser().parse(contentToParse)
            } catch (e: Exception) {
                null
            }
        } else null

        val finalSynced = when {
            parsedSynced != null && parsedSynced.lines.isNotEmpty() -> {
                if (merged.any { it.translated != null } && parsedSynced.lines.none { it.translation != null }) {
                    val updatedLines = parsedSynced.lines.map { line ->
                        val match = merged.find { kotlin.math.abs(it.timeMs - line.start) < 1000 }
                        if (match?.translated != null) {
                            when (line) {
                                is KaraokeLine.MainKaraokeLine -> line.copy(translation = match.translated)
                                is KaraokeLine.AccompanimentKaraokeLine -> line.copy(translation = match.translated)
                                is SyncedLine -> line.copy(translation = match.translated)
                                else -> line
                            }
                        } else line
                    }
                    parsedSynced.copy(lines = updatedLines)
                } else {
                    parsedSynced
                }
            }
            merged.isNotEmpty() -> {
                SyncedLyrics(
                    lines = merged.mapIndexed { idx, item ->
                        val nextStart = merged.getOrNull(idx + 1)?.timeMs?.toInt() ?: (item.timeMs.toInt() + 5000)
                        SyncedLine(
                            content = item.text,
                            start = item.timeMs.toInt(),
                            end = nextStart,
                            translation = item.translated
                        )
                    }
                )
            }
            else -> null
        }

        _state.value = _state.value.copy(
            lyrics = merged,
            syncedLyrics = finalSynced
        )
        notifyState()
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
        val completedSong = _state.value.currentSong
        if (completedSong != null) {
            onSongCompleted?.invoke(completedSong)
        }
        when (_state.value.playMode) {
            PlayMode.SEQUENCE -> {
                val s = _state.value
                if (s.currentIndex >= s.queue.lastIndex && s.currentIndex >= 0) {
                    // 顺序播放播完队列最后一首：停止（回到开头），不再无限循环回第一首
                    // 先 seek 回 0 再存断点：此前先存"整曲时长"，下次恢复 seek 到曲尾立刻 ENDED
                    player.pause()
                    player.seekTo(0)
                    _progress.value = PlaybackProgress()
                    saveSession(positionOverride = 0L)
                } else {
                    next()
                }
            }
            PlayMode.REPEAT -> {
                // 单曲循环重播：重置计数键，让本次重播被统计为一次新播放
                // （此前 isPlaying 变化 + 同键拦截导致单曲循环用户的播放次数系统性漏计）
                statsSongKey = null
                player.seekTo(0)
                player.play()
            }
            PlayMode.SHUFFLE -> {
                val s = _state.value
                if (s.queue.size > 1) {
                    // 随机下一首：避免随机结果还是当前曲
                    val nextIndex = (0 until s.queue.size).filterNot { it == s.currentIndex }.random()
                    _state.value = s.copy(currentIndex = nextIndex, isLoading = true)
                    saveSession(positionOverride = 0L)
                    playCurrent()
                } else {
                    // 单曲队列 + 随机：按循环处理（此前什么都不做，播完直接卡死）
                    statsSongKey = null
                    player.seekTo(0)
                    player.play()
                }
            }
        }
    }

    // ==================== 基础控制 ====================

    fun toggle() {
        if (player.isPlaying) {
            // 淡出后暂停（关闭「淡入淡出」时直接 pause）
            fadeOutThenPause()
        } else {
            when (player.playbackState) {
                // 从未起播：正常走取链播放
                Player.STATE_IDLE -> if (player.mediaItemCount == 0) {
                    _state.value.currentSong?.let {
                        _state.value = _state.value.copy(isLoading = true)
                        playCurrent()
                    }
                } else {
                    // 播放出错后 stop() 会留在 IDLE 但 mediaItemCount=1：
                    // 只 play() 不带 prepare() 永远不出声（此前出错后播放键/通知栏/蓝牙全失灵），
                    // 且同曲重试需要重置计数键以计入一次新播放
                    statsSongKey = null
                    _state.value = _state.value.copy(isLoading = true)
                    player.prepare()
                    player.play()
                }
                else -> player.play()
            }
        }
    }

    fun next() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val idx = if (s.playMode == PlayMode.SHUFFLE && s.queue.size > 1) {
            (0 until s.queue.size).filterNot { it == s.currentIndex }.random()
        } else {
            (s.currentIndex + 1) % s.queue.size
        }
        pendingSeekMs = 0
        _state.value = s.copy(currentIndex = idx, isLoading = true)
        saveSession(positionOverride = 0L)
        playCurrent()
    }

    fun previous() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val idx = (s.currentIndex - 1 + s.queue.size) % s.queue.size
        pendingSeekMs = 0
        _state.value = s.copy(currentIndex = idx, isLoading = true)
        saveSession(positionOverride = 0L)
        playCurrent()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _progress.value = _progress.value.copy(positionMs = positionMs)
        saveSession()
    }

    /** 歌词行点击跳转（参考 App：onTap lyric → seekTo(line.time)） */
    fun seekToLyric(line: LyricLine) {
        seekTo(line.timeMs)
    }

    /** 开始轮询播放进度（同时做统计与断点保存）。
     *  进度只写 [progress] 流，不再 copy 整个 PlayerState：
     *  播放期间 AppShell 与各内容页不会每 500ms 被拖着重组一次。
     *  暂停/无歌时降频空转：此前轮询一旦启动永不停止，暂停下也每 500ms 读位置、
     *  每 5s 整队列 Gson 序列化落盘，大队列下持续空耗 CPU/IO/电。
     *  暂停瞬间的断点由 onIsPlayingChanged 负责保存，这里暂停时不再周期写。 */
    fun startProgress() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            var lastTick = SystemClock.elapsedRealtime()
            while (isActive) {
                val s = _state.value
                if (!s.isPlaying) {
                    // 暂停/未起播：低频空转即可，不做任何 IO
                    delay(1000)
                    continue
                }
                val now = SystemClock.elapsedRealtime()
                val delta = now - lastTick
                lastTick = now
                val pos = player.currentPosition.coerceAtLeast(0)
                val dur = (player.duration.takeIf { it > 0 } ?: _progress.value.durationMs).coerceAtLeast(0)
                _progress.value = PlaybackProgress(
                    positionMs = pos,
                    durationMs = dur,
                )
                if (s.currentSong != null && pos > 0) {
                    // 传 positionMs：单日听歌日记里要显示"最后 00:49"这样的播放时间点
                    StatsTracker.tick(
                        song = s.currentSong!!,
                        deltaMs = delta.coerceAtMost(2000),
                        durationMs = _progress.value.durationMs,
                        positionMs = pos,
                    )
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
        // 按 elapsedRealtime 截止时间计算剩余：此前 delay(1000) 逐秒自减，
        // delay 抖动会累积、息屏 doze 挂起会让"30 分钟"实际显著拉长
        sleepJob = scope.launch {
            val deadline = SystemClock.elapsedRealtime() + total
            while (isActive) {
                val remain = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                if (remain <= 0) {
                    player.setVolume(1f)
                    player.pause()
                    _state.value = _state.value.copy(sleepRemainingMs = 0)
                    notifyState()
                    return@launch
                }
                _state.value = _state.value.copy(sleepRemainingMs = remain)
                // 最后 10 秒线性淡出
                if (remain <= 10_000) {
                    player.setVolume((remain / 10_000f).coerceIn(0f, 1f))
                }
                delay(200)
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
