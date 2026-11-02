package com.aesthetic.music.player

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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

import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.aesthetic.music.data.source.SourceManager

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

/** 实时音频技术规格（对应播放页与详情面板） */
@androidx.compose.runtime.Immutable
data class AudioRealtimeSpec(
    val format: String = "RAW",
    val sampleRateHz: Int = 44100,
    val bitDepth: Int = 16,
    val bitrateKbps: Int = 0,
    val channelCount: Int = 2,
    val durationMs: Long = 0,
    val decodeMode: String = "PCM 直通（未解码）",
    val decoderName: String = "—",
    val qualityRequested: String = "标准",
    val sourceChannel: String = "LX 插件",
    val sourcePlugin: String = "内置音源",
) {
    val sampleRateFormatted: String get() = if (sampleRateHz >= 1000) {
        if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000} kHz"
        else "%.1f kHz".format(sampleRateHz / 1000.0)
    } else "$sampleRateHz Hz"

    val bitDepthFormatted: String get() = if (bitDepth > 0) "$bitDepth bit" else "—"

    val bitrateFormatted: String get() = if (bitrateKbps > 0) "$bitrateKbps kbps" else "—"

    val channelCountFormatted: String get() = when (channelCount) {
        1 -> "单声道"
        2 -> "立体声"
        else -> if (channelCount > 2) "$channelCount 声道" else "立体声"
    }

    val durationFormatted: String get() {
        val totalSec = (durationMs / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }
}

/** 歌词技术元数据与模式 */
@androidx.compose.runtime.Immutable
data class LyricMetaSpec(
    val isKaraoke: Boolean = false,
    val formatType: String = "LRC",
    val loadChannel: String = "在线",
    val matchSource: String = "本地",
    val totalLines: Int = 0,
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
    /**
     * 歌词是否正在加载。
     *
     * 为什么单独一个字段：歌词是一次独立的网络请求，比音频起播慢得多。
     * 此前歌词为空时 UI 一律显示「暂无歌词」，于是「还没加载完」和「这首歌确实没有歌词」
     * 长得一模一样——用户以为歌词坏了。有了这个标记，歌词页能区分
     * 「正在加载…」/「暂无歌词（点一下重试）」。
     */
    val lyricsLoading: Boolean = false,
    val isLoading: Boolean = false,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val errorMessage: String? = null,
    /** 最近一次播放错误（UI 弹提示；消费后清空，避免重复弹） */
    val lastError: String? = null,
    /** 倍速播放 0.5x - 2.0x */
    val speed: Float = 1f,
    /** 睡眠定时剩余毫秒；0 = 未开启 */
    val sleepRemainingMs: Long = 0,
    /** 实时音频技术规格 */
    val audioSpec: AudioRealtimeSpec = AudioRealtimeSpec(),
    /** 实时歌词元数据规格 */
    val lyricSpec: LyricMetaSpec = LyricMetaSpec(),
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
        // setHandleAudioBecomingNoisy(true)：拔耳机（AUDIO_BECOMING_NOISY）自动暂停；
        // setWakeMode(C.WAKE_MODE_NETWORK)：后台播放时持有 CPU WakeLock 和 WifiLock，防止息屏时音频流中断。
        ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
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
            isLoading = { _state.value.isLoading },
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
    /**
     * 播放请求代数：每次 playCurrent 递增。
     * 快速连点"上一首/下一首"时，旧请求（网络慢）返回后凭代数判断已过期，
     * 不再 setMediaItem 覆盖新歌，杜绝"UI 显示 B 实际播 A"的串台。
     */
    private var playGeneration = 0L
    /** 在途的取直链/播放任务：切歌时取消，避免过期请求继续耗流量 */
    private var playJob: Job? = null

    /** 外部监听（MediaSession 等） */
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

    private val powerManager: PowerManager?
        get() = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val wifiManager: WifiManager?
        get() = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var transitionWakeLock: PowerManager.WakeLock? = null
    private var transitionWifiLock: WifiManager.WifiLock? = null

    /**
     * 在切歌/解析直链过渡期获取锁（CPU WakeLock + WifiLock），
     * 避免手机锁屏或 App 切后台时系统进入 Deep Sleep（Doze），
     * 导致 QuickJS 执行脚本、OkHttp 调度网络请求被系统防火墙截断或超时（15s）。
     * 带 45 秒超时兜底，杜绝永久持锁耗电。
     */
    private fun acquireTransitionLock() {
        try {
            if (transitionWakeLock == null) {
                transitionWakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "melos:transition_wakelock",
                )?.apply { setReferenceCounted(false) }
            }
            transitionWakeLock?.acquire(45_000L)
        } catch (e: Exception) {
            android.util.Log.w("PlayerManager", "acquireTransitionLock failed: ${e.message}")
        }

        try {
            if (transitionWifiLock == null) {
                @Suppress("DEPRECATION")
                transitionWifiLock = wifiManager?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "melos:transition_wifilock",
                )?.apply { setReferenceCounted(false) }
            }
            transitionWifiLock?.acquire()
        } catch (e: Exception) {
            android.util.Log.w("PlayerManager", "acquireTransitionWifiLock failed: ${e.message}")
        }
    }

    private fun releaseTransitionLock() {
        try {
            if (transitionWakeLock?.isHeld == true) {
                transitionWakeLock?.release()
            }
        } catch (e: Exception) {
            android.util.Log.w("PlayerManager", "releaseTransitionWakeLock failed: ${e.message}")
        }

        try {
            if (transitionWifiLock?.isHeld == true) {
                transitionWifiLock?.release()
            }
        } catch (e: Exception) {
            android.util.Log.w("PlayerManager", "releaseTransitionWifiLock failed: ${e.message}")
        }
    }

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
    private fun fadeOutThenPause() {
        if (!userPrefs.crossFade) {
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

    /**
     * 预取下一首：**先解析直链进 [UrlCache]，再（可选）把音频缓存到本地**。
     *
     * 这两件事的取舍是息屏连播的关键：
     *  - 直链只是一个几百字节的 JSON，解析成本极低，**无条件预取**。
     *    这样切歌（通常发生在息屏时）不必再联网——后台网络被 Doze/ROM 掐断也不会停播。
     *  - 音频缓存要按「仅 WiFi / 仅边听边下」等策略放行，属于可选项；
     *    以前把两者绑在一起（if (!preloadNext) return），于是关掉"预加载下一首"的用户
     *    连直链都不会预取，息屏切歌就变成了"每首都要联网"。
     */
    private fun preloadNextIfEnabled() {
        val s = _state.value
        if (s.queue.size <= 1) return
        val nextIndex = (s.currentIndex + 1) % s.queue.size
        val next = s.queue.getOrNull(nextIndex) ?: return
        if (next.id.isBlank()) return
        val quality = SettingsStore.getQuality().value
        val urlKey = UrlCache.key(next.platform, next.id, quality)
        if (UrlCache.get(urlKey) != null) return
        if (urlKey == preloadedKey) return
        preloadedKey = urlKey
        scope.launch {
            try {
                // ① 直链预取（无条件）
                val r = repository.getSongUrl(next, quality)
                val url = r.getOrNull()?.url
                if (url.isNullOrBlank()) {
                    if (preloadedKey == urlKey) preloadedKey = null
                    return@launch
                }
                UrlCache.put(urlKey, url)

                // ② 音频缓存（受设置与下载策略约束）
                if (!userPrefs.preloadNext) return@launch
                if (!DownloadPolicy.allowed(appContext, userPrefs, manual = false)) return@launch
                val cached = withContext(Dispatchers.IO) {
                    AudioCacheService.getCachedPath(appContext, next.platform, next.id, quality)
                }
                if (cached != null) return@launch
                AudioCacheService.cacheAudio(
                    context = appContext,
                    platformCode = next.platform,
                    songId = next.id,
                    quality = quality,
                    url = url,
                    name = next.title,
                    artist = next.artist,
                    scope = scope,
                ) { path ->
                    if (path != null) {
                        saveSidecars(next)
                    } else {
                        if (preloadedKey == urlKey) preloadedKey = null
                    }
                }
            } catch (e: Exception) {
                if (preloadedKey == urlKey) preloadedKey = null
            }
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

    private var currentDecoderName: String? = null

    private fun updateAudioDecoder(decoderName: String) {
        currentDecoderName = decoderName
        updateAudioSpec(player.audioFormat)
    }

    private fun updateAudioSpec(format: Format?) {
        val s = _state.value
        val song = s.currentSong
        val duration = player.duration.takeIf { it > 0 } ?: _progress.value.durationMs

        val sampleMime = format?.sampleMimeType?.lowercase().orEmpty()
        val containerMime = format?.containerMimeType?.lowercase().orEmpty()

        val formatName = when {
            sampleMime.contains("flac") || containerMime.contains("flac") -> "FLAC"
            sampleMime.contains("mp4") || sampleMime.contains("aac") || containerMime.contains("aac") -> "AAC"
            sampleMime.contains("mpeg") || sampleMime.contains("mp3") || containerMime.contains("mp3") -> "MP3"
            sampleMime.contains("ogg") || sampleMime.contains("vorbis") -> "OGG"
            sampleMime.contains("opus") -> "OPUS"
            sampleMime.contains("wav") -> "WAV"
            sampleMime.contains("raw") || sampleMime.contains("pcm") -> "RAW"
            else -> {
                val q = SettingsStore.getQuality().value.lowercase()
                if (q.contains("master") || q.contains("hi-res")) "RAW"
                else if (q.contains("lossless") || q.contains("无损")) "FLAC"
                else "MP3"
            }
        }

        val sampleRate = format?.sampleRate?.takeIf { it > 0 } ?: when (SettingsStore.getQuality().value.lowercase()) {
            "master", "hires", "hi-res" -> 192000
            else -> 44100
        }

        val channelCount = format?.channelCount?.takeIf { it > 0 } ?: 2

        val bitDepth = when (format?.pcmEncoding) {
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
            C.ENCODING_PCM_16BIT -> 16
            else -> {
                val q = SettingsStore.getQuality().value.lowercase()
                if (q.contains("master") || q.contains("hi-res") || sampleRate >= 96000) 24
                else 16
            }
        }

        val bitrateKbps = if (format != null && format.bitrate > 0) {
            format.bitrate / 1000
        } else {
            // 实时估算比特率：如 RAW/无损：sampleRate * channels * bitDepth / 1000
            if (formatName == "RAW") {
                (sampleRate.toLong() * channelCount * bitDepth / 1000).toInt()
            } else if (formatName == "FLAC") {
                ((sampleRate.toLong() * channelCount * bitDepth / 1000) * 0.6).toInt()
            } else {
                when (SettingsStore.getQuality().value.lowercase()) {
                    "master" -> 9216
                    "hi-res", "hires" -> 4608
                    "lossless", "无损" -> 960
                    "high", "极高" -> 320
                    else -> 128
                }
            }
        }

        val decoder = currentDecoderName
        val decodeMode = when {
            decoder.isNullOrBlank() || decoder == "—" -> "PCM 直通（未解码）"
            decoder.contains("c2.android") || decoder.contains("ffmpeg") -> "软件解码"
            decoder.contains("c2.") || decoder.contains("omx", ignoreCase = true) || decoder.contains("qti") -> "硬件解码"
            else -> "PCM 直通（未解码）"
        }

        val qualityRequested = SettingsStore.getQuality().value.replaceFirstChar { it.uppercase() }
        val sourceChannel = "LX 插件"
        val sourcePlugin = SourceManager.lastSourceName.value ?: "长青 SVIP 音源(二改修复版)"

        val newSpec = AudioRealtimeSpec(
            format = formatName,
            sampleRateHz = sampleRate,
            bitDepth = bitDepth,
            bitrateKbps = bitrateKbps,
            channelCount = channelCount,
            durationMs = duration,
            decodeMode = decodeMode,
            decoderName = if (decodeMode.contains("直通")) "—" else (decoder ?: "—"),
            qualityRequested = qualityRequested,
            sourceChannel = sourceChannel,
            sourcePlugin = sourcePlugin,
        )

        _state.value = _state.value.copy(audioSpec = newSpec)
    }

    init {
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                updateAudioSpec(format)
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                updateAudioDecoder(decoderName)
            }
        })

        try {
            MelosEqualizer.init(appContext)
            MelosEqualizer.attachAudioSession(player.audioSessionId)
        } catch (_: Throwable) {}

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    try {
                        MelosEqualizer.attachAudioSession(player.audioSessionId)
                    } catch (_: Throwable) {}
                    // 淡入：从静音升到正常音量，避免每次起播"啪"一声
                    fadeIn()
                    val s = _state.value
                    val song = s.currentSong
                    val key = song?.let { it.platform + ":" + it.id }
                    if (key != null && key != statsSongKey) {
                        statsSongKey = key
                        // 切歌：把上一首歌的播放片段封段，避免两首歌的时长混进同一条事件
                        com.aesthetic.music.service.StatsTracker.pauseSegment()
                        StatsTracker.onPlayStarted(song)
                        // 真正起播才写"最近播放"（此前"点击即写历史"，取直链失败的歌也入历史）
                        if (song != null) onPlaybackStarted?.invoke(song)
                    }
                    // 起播后预取下一首（设置里可关）
                    preloadNextIfEnabled()
                } else {
                    // 暂停瞬间立即落一次断点：轮询循环暂停时降频不再保存，这里保证暂停位置不丢
                    saveSession()
                    // 暂停封段：这段播放时长立刻入待发队列（继续播同一首会开新片段，
                    // 服务端按相邻片段合并回完整播放次数）
                    com.aesthetic.music.service.StatsTracker.pauseSegment()
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
                        consecutivePlayFailures = 0
                        releaseTransitionLock()
                        val duration = player.duration.takeIf { it > 0 } ?: _progress.value.durationMs
                        _progress.value = _progress.value.copy(durationMs = duration)
                        _state.value = _state.value.copy(isLoading = false)
                        updateAudioSpec(player.audioFormat)
                        notifyState()
                    }
                    Player.STATE_BUFFERING -> {
                        _state.value = _state.value.copy(isLoading = true)
                        notifyState()
                    }
                    Player.STATE_ENDED -> {
                        acquireTransitionLock()
                        onSongComplete()
                    }
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                releaseTransitionLock()
                // 播放中途出错（解码失败 / 直链过期 / 数据流中断）：
                // **先尝试跳到下一首**，队列里没有别的歌（或连续失败太多次）才停下。
                // 以前这里直接 player.stop()，用户遇到「某首歌没音源」时整条播放链就断了，
                // 明明队列后面还有能放的歌。
                val gen = playGeneration
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "播放错误: ${error.errorCodeName}",
                )
                notifyState()
                if (!skipAfterFailure(gen, "无法播放")) {
                    _state.value = _state.value.copy(lastError = friendlyError(error))
                    notifyState()
                    player.stop()
                }
            }
        })
    }

    private fun notifyState() {
        val s = _state.value
        onStateChanged?.invoke(s)
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
        releaseTransitionLock()
        player.stop()
        PlayerSessionStore.clear()
        AudioCacheService.setProtectedPath(null)
        _state.value = PlayerState(playMode = _state.value.playMode, speed = _state.value.speed)
        notifyState()
    }

    private var consecutivePlayFailures = 0

    /**
     * 播放失败后**尝试跳到下一首**（取链失败与播放中途出错共用）。
     *
     * 用户要的是「一首放不了就自动放下一首」，而不是整条播放链停下——尤其是队列里
     * 只有一两个可用音源、某些歌根本没版权的时候，停下来等于把后面的歌一起废掉。
     *
     * 只有两种情况不跳：
     *  1. 队列里只有一首（没得跳）；
     *  2. 已经连续失败 [PlaybackFailurePolicy.MAX_CONSECUTIVE_FAILURES] 次——这时大概率是网络断了或
     *     音源整体不可用，继续跳只会把整个队列空刷一遍，不如停下给一句明确提示。
     *
     * @param gen 失败发生时的播放代数：等待期间用户可能已手动切歌，那就不该再跳
     * @param reason 失败原因（拼进提示文案，如「无法播放」「未取到播放地址」）
     * @return true = 已安排跳歌（调用方不要再报错/停止）；false = 跳不了，调用方自行收尾
     */
    private fun skipAfterFailure(gen: Long, reason: String): Boolean {
        val s = _state.value
        consecutivePlayFailures++
        if (!PlaybackFailurePolicy.shouldSkipToNext(s.queue.size, consecutivePlayFailures)) {
            return false
        }
        _state.value = _state.value.copy(
            isLoading = false,
            lastError = PlaybackFailurePolicy.skipMessage(s.currentSong?.title, reason),
        )
        notifyState()
        scope.launch {
            delay(PlaybackFailurePolicy.SKIP_DELAY_MS)
            if (gen == playGeneration) next()
        }
        return true
    }

    private fun playCurrent() {
        val s = _state.value
        val song = s.currentSong ?: run {
            releaseTransitionLock()
            return
        }
        val gen = ++playGeneration
        acquireTransitionLock()
        startPlaybackService()
        // 重置歌词/进度。歌词标记同时置「加载中」：否则播放页的 ensureLyricsLoaded 会
        // 在这个空窗期（歌词已清空、loadLyrics 还没开始）再发一次重复请求
        _progress.value = PlaybackProgress()
        _state.value = _state.value.copy(
            lyrics = emptyList(),
            syncedLyrics = null,
            lyricsLoading = true,
            errorMessage = null,
            isLoading = true,
        )
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
            val urlKey = UrlCache.key(song.platform, song.id, quality)
            val onlineUrl: String? = if (cached != null) null else {
                // ① 先用预取好的直链：息屏/后台切歌时这一步能完全避开网络
                //    （息屏后后台网络会被 Doze/ROM 省电策略限速甚至掐断，
                //     "切歌必须联网"正是息屏放一两首就停的根因）
                val prefetched = UrlCache.get(urlKey)
                if (!prefetched.isNullOrBlank()) {
                    prefetched
                } else {
                    // ② 没有预取就现场解析，并允许重试：
                    //    单次失败往往只是网络抖动，直接判"这首废了"再跳下一首会连锁失败
                    var resolved: String? = null
                    var lastFailure: Throwable? = null
                    for (attempt in 0 until URL_RESOLVE_ATTEMPTS) {
                        if (gen != playGeneration) return@launch
                        val r = repository.getSongUrl(song, quality)
                        val u = r.getOrNull()?.url
                        if (!u.isNullOrBlank()) {
                            resolved = u
                            break
                        }
                        lastFailure = r.exceptionOrNull()
                        if (attempt < URL_RESOLVE_ATTEMPTS - 1) {
                            android.util.Log.w(
                                "PlayerManager",
                                "取直链失败（第  次）：，ms 后重试",
                            )
                            delay(URL_RESOLVE_RETRY_MS * (attempt + 1))
                        }
                    }
                    if (resolved == null && lastFailure != null && gen == playGeneration) {
                        _state.value = _state.value.copy(isLoading = false, lastError = friendlyError(lastFailure!!))
                        notifyState()
                    }
                    resolved?.also { UrlCache.put(urlKey, it) }
                }
            }
            if (gen != playGeneration) return@launch
            if (gen != playGeneration) return@launch
            if (cached == null && onlineUrl.isNullOrBlank()) {
                // 播不了就不会有歌词：先把「歌词加载中」放掉，否则歌词页会一直转圈
                setLyricsLoading(false, gen)
                // 取链失败同样走「跳过下一首」：只有队列里没有别的歌、
                // 或连续失败到上限时才真正停下
                if (skipAfterFailure(gen, "未取到播放地址")) return@launch
                releaseTransitionLock()
                consecutivePlayFailures = 0
                _state.value = _state.value.copy(
                    isLoading = false,
                    lastError = PlaybackFailurePolicy.giveUpMessage("无法获取播放地址"),
                )
                notifyState()
                return@launch
            }

            val playPath = cached ?: onlineUrl!!
            // 受保护路径 = 正在播放的本地缓存文件；在线播放时上一首的缓存文件不再受保护
            AudioCacheService.setProtectedPath(cached)
            if (cached == null) {
                // 未命中：后台缓存（不阻塞播放）。上方已保证 onlineUrl 非空，这里再兜一层
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
     *
     * ## 为什么要「重试一次」+ [ensureLyricsLoaded]
     * 歌词请求和起播请求是并发的，首曲还常常撞上 App 冷启动（DNS / 音源插件预热）。
     * 只要第一次请求失败，这首歌在整次播放期间就再也没有歌词了——用户看到的现象是
     * 「打开歌词页没有歌词，切一首歌就有了」。所以这里：失败自动重试一次；
     * 另外 [ensureLyricsLoaded] 供 UI 在「歌词为空」时主动补拉。
     */
    private suspend fun loadLyrics(song: Song, gen: Long) {
        val prefs = userPrefs
        if (!prefs.lyricOnline && !prefs.lyricLocal) {
            clearLyrics(gen)
            return
        }
        setLyricsLoading(true, gen)
        try {
            if (prefs.lyricLocal) {
                val local = withContext(Dispatchers.IO) {
                    val cached = AudioCacheService.getCachedPath(
                        appContext, song.platform, song.id, SettingsStore.getQuality().value,
                    )
                    LocalLyricStore.loadLocalLyric(appContext, song, cached)
                }
                if (!local.isNullOrBlank()) {
                    applyLyrics(local, null, gen, isLocal = true)
                    return
                }
            }
            if (!prefs.lyricOnline) {
                // 只开本地却没有本地文件：清空而不是联网（用户的目标就是纯离线）
                clearLyrics(gen)
                return
            }

            var lastLyric: String? = null
            var lastTranslated: String? = null
            var lastYrc: String? = null
            // 最多两次：第一次失败（异常或空）时立刻再试一次，间隔 400ms
            repeat(2) { attempt ->
                if (gen != playGeneration) return
                val result = repository.getLyric(song)
                result.onSuccess { d ->
                    lastLyric = d.lyric
                    lastTranslated = d.translated
                    lastYrc = d.wordByWord
                }
                if (!lastLyric.isNullOrBlank()) {
                    applyLyrics(lastLyric, lastTranslated, gen, isLocal = false, rawWordByWord = lastYrc)
                    return
                }
                if (attempt == 0) {
                    android.util.Log.w("PlayerManager", "歌词为空，400ms 后重试：${song.title}")
                    delay(400)
                }
            }
            // 两次都没有：保持空（UI 会显示「暂无歌词 · 点一下重试」）
            clearLyrics(gen, keepLoadingState = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("PlayerManager", "歌词加载失败: ${e.message}")
            clearLyrics(gen, keepLoadingState = true)
        } finally {
            setLyricsLoading(false, gen)
        }
    }

    /** 歌词加载中标记（只对当前代有效，避免切歌后被旧请求改回去） */
    private fun setLyricsLoading(loading: Boolean, gen: Long) {
        if (gen != playGeneration) return
        if (_state.value.lyricsLoading == loading) return
        _state.value = _state.value.copy(lyricsLoading = loading)
        notifyState()
    }

    /**
     * UI 侧兜底：当前歌曲「没有歌词且不在加载中」时补拉一次。
     *
     * 播放页/歌词页在歌曲变化时调用。它解决的是「首曲歌词请求失败后就永久空白」的问题，
     * 也让「用户从队列里挑了一首没播过的歌」这种路径有歌词。
     */
    fun ensureLyricsLoaded() {
        val song = _state.value.currentSong ?: return
        val s = _state.value
        if (s.lyrics.isNotEmpty() || s.lyricsLoading) return
        val prefs = userPrefs
        if (!prefs.lyricOnline && !prefs.lyricLocal) return
        val gen = playGeneration
        scope.launch { loadLyrics(song, gen) }
    }

    /** 用户点「重新加载」：无视当前状态强制再拉一次 */
    fun reloadLyrics() {
        val song = _state.value.currentSong ?: return
        val gen = playGeneration
        scope.launch { loadLyrics(song, gen) }
    }

    /** 清空歌词（来源被关掉，或本地没有可用歌词） */
    private fun clearLyrics(gen: Long, keepLoadingState: Boolean = false) {
        if (gen != playGeneration) return
        _state.value = _state.value.copy(
            lyrics = emptyList(),
            syncedLyrics = null,
            lyricSpec = LyricMetaSpec(),
            lyricsLoading = if (keepLoadingState) _state.value.lyricsLoading else false,
        )
        notifyState()
    }

    /**
     * 解析并落地歌词。
     *
     * 逐字只认**真实返回的字时间**（网易云 YRC，见 [attachWordTimings]）：
     * 有就把字时间贴到 LRC 文本上，没有就保持逐句。
     * 不再给纯 LRC 补「按字数估算」的假字时间——那种估算的吐字速度与真实演唱对不上，
     * 反而会让人觉得「歌词和声音在句子内错位」。
     */
    private fun applyLyrics(
        rawLyricIn: String?,
        rawTranslatedIn: String?,
        gen: Long,
        isLocal: Boolean = false,
        rawWordByWord: String? = null,
    ) {
        if (gen != playGeneration) return
        val song = _state.value.currentSong
        val songTitle = song?.title ?: ""
        val songArtist = song?.artist ?: ""
        val original = parseAutoLyrics(rawLyricIn, songTitle, songArtist)
        val translated = if (rawTranslatedIn.isNullOrBlank()) emptyList() else parseAutoLyrics(rawTranslatedIn, songTitle, songArtist)
        val mergedBase = mergeTranslation(original, translated)
        // 只贴真实字时间（YRC）；没有就保持逐句
        val merged = attachWordTimings(mergedBase, rawWordByWord, songTitle, songArtist)

        val contentToParse = if (!rawTranslatedIn.isNullOrBlank() && !(rawLyricIn ?: "").contains(rawTranslatedIn)) {
            "${rawLyricIn ?: ""}\n$rawTranslatedIn"
        } else {
            rawLyricIn ?: ""
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

        val currentSong = _state.value.currentSong
        // 只有拿到真实字时间（YRC/KRC）才算 karaoke 格式；近似字时间不冒充 YRC
        val hasWordTiming = !rawWordByWord.isNullOrBlank() && merged.any { it.words.isNotEmpty() }
        val isKaraoke = hasWordTiming || (finalSynced != null && finalSynced.lines.any { it is KaraokeLine })
        val formatType = if (isKaraoke) "YRC" else "LRC"
        val loadChannel = if (isLocal) "本地" else "在线"
        val platformMap = mapOf(
            "netease" to "网易云音乐",
            "qq" to "QQ音乐",
            "kugou" to "酷狗音乐",
            "kuwo" to "酷我音乐",
            "migu" to "咪咕音乐",
            "bilibili" to "哔哩哔哩",
            "local" to "本地文件",
        )
        val matchSource = platformMap[currentSong?.platform] ?: (currentSong?.platform ?: "网易云音乐")
        val totalLines = finalSynced?.lines?.size ?: merged.size

        val lyricMeta = LyricMetaSpec(
            isKaraoke = isKaraoke,
            formatType = formatType,
            loadChannel = loadChannel,
            matchSource = matchSource,
            totalLines = totalLines,
        )

        _state.value = _state.value.copy(
            lyrics = merged,
            syncedLyrics = finalSynced,
            lyricSpec = lyricMeta,
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

    fun setPlayMode(mode: PlayMode) {
        if (_state.value.playMode == mode) return
        _state.value = _state.value.copy(playMode = mode)
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
                // 重播是新的一次播放：上一轮的片段封段，服务端会按相邻合并
                com.aesthetic.music.service.StatsTracker.pauseSegment()
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
                    com.aesthetic.music.service.StatsTracker.pauseSegment()
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
                    com.aesthetic.music.service.StatsTracker.pauseSegment()
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
        acquireTransitionLock()
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
        acquireTransitionLock()
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

    /**
     * 切换音质后「就地重播」当前歌曲。
     *
     * 为什么必须重取直链：音质是取链参数（standard/high/lossless 对应不同码率甚至不同容器），
     * 已经下发的音频流不会因为设置变化而变成无损。所以这里把当前进度记进 [pendingSeekMs]，
     * 再走一遍 [playCurrent]（重新取链 + 重新加载歌词），起播后会 seek 回原位置，
     * 听感上是「闪一下继续播」，而不是「从头再来」。
     *
     * 音质设置本身的持久化由调用方（设置页 / 播放页）写入 [SettingsStore]，
     * 这里只负责重播，避免两处都写设置导致状态分叉。
     */
    fun reloadCurrentForQuality() {
        val song = _state.value.currentSong ?: return
        if (song.id.isBlank()) return
        val position = player.currentPosition.takeIf { it > 0 } ?: _progress.value.positionMs
        pendingSeekMs = position
        _state.value = _state.value.copy(isLoading = true)
        playCurrent()
    }

    /**
     * 队列内拖动排序。
     *
     * 关键点：**当前播放项要跟着移动**。如果只挪列表不改 [currentIndex]，
     * 拖完之后「正在播放」会跳到别的歌上（听到的歌和卡片上的歌不一致），
     * 这是队列排序最容易踩的坑。
     */
    fun moveQueueItem(from: Int, to: Int) {
        val s = _state.value
        if (from !in s.queue.indices || to !in s.queue.indices || from == to) return
        val list = s.queue.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        val newIndex = when {
            s.currentIndex == from -> to
            from < s.currentIndex && to >= s.currentIndex -> s.currentIndex - 1
            from > s.currentIndex && to <= s.currentIndex -> s.currentIndex + 1
            else -> s.currentIndex
        }
        _state.value = s.copy(queue = list, currentIndex = newIndex)
        saveSession()
        notifyState()
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
        releaseTransitionLock()
        StatsTracker.flushNow()
        scope.launch(Dispatchers.Main) { player.release() }
    }

    companion object {
        /** 取直链的最多尝试次数（含首次）：单次失败多半是网络抖动，不该直接判这首废了 */
        private const val URL_RESOLVE_ATTEMPTS = 3

        /** 取直链重试的基准间隔（第 n 次重试等 n × 该值） */
        private const val URL_RESOLVE_RETRY_MS = 1_200L

        @Volatile
        private var instance: PlayerManager? = null

        fun get(context: Context): PlayerManager =
            instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
    }
}
