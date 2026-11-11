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
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.PlayerSessionStore
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.UserPrefs
import com.aesthetic.music.service.LogReporter
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

enum class PlayMode { SEQUENCE, REPEAT, SHUFFLE }

private class EqRenderersFactory(
    context: Context,
    private val eqProcessor: AudioProcessor,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf(eqProcessor))
        .build()
}

private const val FADE_STEP_MS = 30L

private const val SERVICE_HEALTH_CHECK_MS = 15_000L

data class PlaybackProgress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

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

@androidx.compose.runtime.Immutable
data class LyricMetaSpec(
    val isKaraoke: Boolean = false,
    val formatType: String = "LRC",
    val loadChannel: String = "在线",
    val matchSource: String = "本地",
    val totalLines: Int = 0,
)

@androidx.compose.runtime.Immutable
data class PlayerState(
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val lyrics: List<LyricLine> = emptyList(),
    /** 作词/作曲等制作信息，来自歌词头部的元数据行；没有就是空列表 */
    val lyricCredits: List<String> = emptyList(),
    val syncedLyrics: SyncedLyrics? = null,
    val lyricsLoading: Boolean = false,
    val isLoading: Boolean = false,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val errorMessage: String? = null,
    val lastError: String? = null,
    val speed: Float = 1f,
    val sleepRemainingMs: Long = 0,
    val audioSpec: AudioRealtimeSpec = AudioRealtimeSpec(),
    val lyricSpec: LyricMetaSpec = LyricMetaSpec(),
) {
    val currentSong: Song? get() = queue.getOrNull(currentIndex)
}

class PlayerManager private constructor(private val appContext: Context) {

    private val repository = MusicRepository()

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext)
            .setRenderersFactory(EqRenderersFactory(appContext, MelosEqualizer.processor))
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

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private var progressJob: Job? = null
    private var sleepJob: Job? = null
    private var pendingSeekMs: Long = 0
    private var statsSongKey: String? = null
    private var lastSessionSaveAt = 0L
    private var playGeneration = 0L
    private var playJob: Job? = null

    var onStateChanged: ((PlayerState) -> Unit)? = null

    @Volatile
    private var userPrefs: UserPrefs = SettingsStore.readPrefs()

    private var fadeJob: Job? = null

    private var pausedForRecording = false

    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    private val audioManager: AudioManager?
        get() = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val powerManager: PowerManager?
        get() = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val wifiManager: WifiManager?
        get() = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var transitionWakeLock: PowerManager.WakeLock? = null
    private var transitionWifiLock: WifiManager.WifiLock? = null

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

    @Volatile
    private var preloadedKey: String? = null

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

    private fun applyAudioFocusMode(prefs: UserPrefs) {
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
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
                        saveSidecarsAfterDownload(next)
                    } else {
                        if (preloadedKey == urlKey) preloadedKey = null
                    }
                }
            } catch (e: Exception) {
                if (preloadedKey == urlKey) preloadedKey = null
            }
        }
    }

    fun saveSidecarsAfterDownload(song: Song) {
        if (!userPrefs.downloadCoverLyrics) return
        scope.launch {
            runCatching {
                // 歌词落盘也是阻塞 IO，别占着主线程；封面在 saveCover 内部自己切 IO
                withContext(Dispatchers.IO) {
                    if (userPrefs.lyricOnline) {
                        repository.getLyric(song).getOrNull()?.let { d ->
                            LocalLyricStore.saveLocalLyric(appContext, song, d.lyric)
                        }
                    }
                }
                SongSidecarStore.saveCover(appContext, song)
            }
        }
    }

    var onPlaybackStarted: ((Song) -> Unit)? = null
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
        } catch (_: Throwable) {}

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    fadeIn()
                    val s = _state.value
                    val song = s.currentSong
                    val key = song?.let { it.platform + ":" + it.id }
                    if (key != null && key != statsSongKey) {
                        statsSongKey = key
                        com.aesthetic.music.service.StatsTracker.pauseSegment()
                        StatsTracker.onPlayStarted(song)
                        if (song != null) onPlaybackStarted?.invoke(song)
                    }
                    preloadNextIfEnabled()
                } else {
                    saveSession()
                    com.aesthetic.music.service.StatsTracker.pauseSegment()
                }
                notifyState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS &&
                    !playWhenReady &&
                    !userPrefs.resumeAfterInterrupt
                ) {
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
                val song = _state.value.currentSong
                LogReporter.e(
                    "PlayerManager",
                    "播放错误 song=${song?.platform}:${song?.id} code=${error.errorCodeName}",
                    error,
                )
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
        if (s.isPlaying || s.isLoading) {
            ensurePlaybackService()
        }
        onStateChanged?.invoke(s)
    }

    private fun startPlaybackService() {
        try {
            com.aesthetic.music.service.PlaybackService.ensureChannelExists(appContext)
            val intent = android.content.Intent(appContext, com.aesthetic.music.service.PlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (e: Exception) {
            LogReporter.w("PlayerManager", "启动播放服务失败", e)
        }
    }

    private fun ensurePlaybackService() {
        if (!com.aesthetic.music.service.PlaybackService.isRunning()) startPlaybackService()
    }

    fun consumeError() {
        if (_state.value.lastError != null) {
            _state.value = _state.value.copy(lastError = null)
        }
    }

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

    fun restoreSession() {
        if (_state.value.queue.isNotEmpty()) return
        scope.launch(Dispatchers.IO) {
            val session = PlayerSessionStore.load()
            withContext(Dispatchers.Main) {
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

    fun playQueue(songs: List<Song>, index: Int = 0) {
        if (songs.isEmpty()) return
        val idx = index.coerceIn(0, songs.lastIndex)
        pendingSeekMs = 0
        _state.value = _state.value.copy(queue = songs, currentIndex = idx, isLoading = true)
        saveSession(positionOverride = 0L)
        playCurrent()
    }

    fun playSingle(song: Song) = playQueue(listOf(song), 0)

    fun addToQueue(song: Song) {
        _state.value = _state.value.copy(queue = _state.value.queue + song)
        saveSession()
        notifyState()
    }

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

    fun playQueueItem(index: Int) {
        val s = _state.value
        if (index !in s.queue.indices) return
        pendingSeekMs = 0
        _state.value = s.copy(currentIndex = index, isLoading = true)
        saveSession(positionOverride = 0L)
        playCurrent()
    }

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
                    saveSession(positionOverride = 0L)
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

    fun clearQueue() {
        releaseTransitionLock()
        player.stop()
        PlayerSessionStore.clear()
        AudioCacheService.setProtectedPath(null)
        _state.value = PlayerState(playMode = _state.value.playMode, speed = _state.value.speed)
        notifyState()
    }

    private var consecutivePlayFailures = 0

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
        _progress.value = PlaybackProgress()
        _state.value = _state.value.copy(
            lyrics = emptyList(),
            lyricCredits = emptyList(),
            syncedLyrics = null,
            lyricsLoading = true,
            errorMessage = null,
            isLoading = true,
        )
        playJob?.cancel()
        playJob = scope.launch {
            val quality = SettingsStore.getQuality().value
            LogReporter.breadcrumb("Player", "play ${song.platform}:${song.id} quality=$quality")

            val cached = withContext(Dispatchers.IO) {
                AudioCacheService.getCachedPath(appContext, song.platform, song.id, quality)
            }
            if (gen != playGeneration) return@launch
            val urlKey = UrlCache.key(song.platform, song.id, quality)
            val onlineUrl: String? = if (cached != null) null else {
                //     "切歌必须联网"正是息屏放一两首就停的根因）
                val prefetched = UrlCache.get(urlKey)
                if (!prefetched.isNullOrBlank()) {
                    prefetched
                } else {
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
                                "取直链失败（第 ${attempt + 1} 次）：${r.exceptionOrNull()?.message}，" +
                                    "${URL_RESOLVE_RETRY_MS * (attempt + 1)}ms 后重试",
                            )
                            delay(URL_RESOLVE_RETRY_MS * (attempt + 1))
                        }
                    }
                    if (resolved == null && lastFailure != null && gen == playGeneration) {
                        LogReporter.w(
                            "PlayerManager",
                            "取直链失败 song=${song.platform}:${song.id} quality=$quality attempts=$URL_RESOLVE_ATTEMPTS",
                            lastFailure,
                        )
                        _state.value = _state.value.copy(isLoading = false, lastError = friendlyError(lastFailure!!))
                        notifyState()
                    }
                    resolved?.also { UrlCache.put(urlKey, it) }
                }
            }
            if (gen != playGeneration) return@launch
            if (gen != playGeneration) return@launch
            if (cached == null && onlineUrl.isNullOrBlank()) {
                setLyricsLoading(false, gen)
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
            AudioCacheService.setProtectedPath(cached)
            if (cached == null) {
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
                        saveSidecarsAfterDownload(song)
                    }
                }
            }

            // artworkData 必须给：Android 13+ 的媒体卡片用元数据里的图片字节画封面，
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
            player.setVolume(if (userPrefs.crossFade) 0f else 1f)
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
                clearLyrics(gen)
                return
            }

            var lastLyric: String? = null
            var lastTranslated: String? = null
            var lastYrc: String? = null
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
            clearLyrics(gen, keepLoadingState = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LogReporter.w("PlayerManager", "歌词加载失败 song=${song.platform}:${song.id}", e)
            clearLyrics(gen, keepLoadingState = true)
        } finally {
            setLyricsLoading(false, gen)
        }
    }

    private fun setLyricsLoading(loading: Boolean, gen: Long) {
        if (gen != playGeneration) return
        if (_state.value.lyricsLoading == loading) return
        _state.value = _state.value.copy(lyricsLoading = loading)
        notifyState()
    }

    fun ensureLyricsLoaded() {
        val song = _state.value.currentSong ?: return
        val s = _state.value
        if (s.lyrics.isNotEmpty() || s.lyricsLoading) return
        val prefs = userPrefs
        if (!prefs.lyricOnline && !prefs.lyricLocal) return
        val gen = playGeneration
        scope.launch { loadLyrics(song, gen) }
    }

    fun reloadLyrics() {
        val song = _state.value.currentSong ?: return
        val gen = playGeneration
        scope.launch { loadLyrics(song, gen) }
    }

    private fun clearLyrics(gen: Long, keepLoadingState: Boolean = false) {
        if (gen != playGeneration) return
        _state.value = _state.value.copy(
            lyrics = emptyList(),
            lyricCredits = emptyList(),
            syncedLyrics = null,
            lyricSpec = LyricMetaSpec(),
            lyricsLoading = if (keepLoadingState) _state.value.lyricsLoading else false,
        )
        notifyState()
    }

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
            // 作词/作曲这些行被正文过滤掉了，这里单独收集起来给界面固定展示
            lyricCredits = extractCredits(rawLyricIn),
            syncedLyrics = finalSynced,
            lyricSpec = lyricMeta,
        )
        notifyState()
    }

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
                    player.pause()
                    player.seekTo(0)
                    _progress.value = PlaybackProgress()
                    saveSession(positionOverride = 0L)
                } else {
                    next()
                }
            }
            PlayMode.REPEAT -> {
                statsSongKey = null
                com.aesthetic.music.service.StatsTracker.pauseSegment()
                player.seekTo(0)
                player.play()
            }
            PlayMode.SHUFFLE -> {
                val s = _state.value
                if (s.queue.size > 1) {
                    val nextIndex = (0 until s.queue.size).filterNot { it == s.currentIndex }.random()
                    _state.value = s.copy(currentIndex = nextIndex, isLoading = true)
                    saveSession(positionOverride = 0L)
                    playCurrent()
                } else {
                    statsSongKey = null
                    com.aesthetic.music.service.StatsTracker.pauseSegment()
                    player.seekTo(0)
                    player.play()
                }
            }
        }
    }

    fun toggle() {
        if (player.isPlaying) {
            LogReporter.breadcrumb("Player", "pause")
            fadeOutThenPause()
        } else {
            LogReporter.breadcrumb("Player", "resume")
            ensurePlaybackService()
            when (player.playbackState) {
                Player.STATE_IDLE -> if (player.mediaItemCount == 0) {
                    _state.value.currentSong?.let {
                        _state.value = _state.value.copy(isLoading = true)
                        playCurrent()
                    }
                } else {
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
        LogReporter.breadcrumb("Player", "next from=${s.currentIndex}")
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
        LogReporter.breadcrumb("Player", "previous from=${s.currentIndex}")
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

    fun seekToLyric(line: LyricLine) {
        seekTo(line.timeMs)
    }

    fun reloadCurrentForQuality() {
        val song = _state.value.currentSong ?: return
        if (song.id.isBlank()) return
        val position = player.currentPosition.takeIf { it > 0 } ?: _progress.value.positionMs
        pendingSeekMs = position
        _state.value = _state.value.copy(isLoading = true)
        playCurrent()
    }

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

    fun startProgress() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            var lastTick = SystemClock.elapsedRealtime()
            var lastServiceCheckAt = 0L
            while (isActive) {
                val s = _state.value
                if (!s.isPlaying) {
                    delay(1000)
                    continue
                }
                val now = SystemClock.elapsedRealtime()
                val delta = now - lastTick
                lastTick = now
                if (now - lastServiceCheckAt > SERVICE_HEALTH_CHECK_MS) {
                    lastServiceCheckAt = now
                    ensurePlaybackService()
                    com.aesthetic.music.service.PlaybackService.refreshCard(appContext)
                }
                val pos = player.currentPosition.coerceAtLeast(0)
                val dur = (player.duration.takeIf { it > 0 } ?: _progress.value.durationMs).coerceAtLeast(0)
                _progress.value = PlaybackProgress(
                    positionMs = pos,
                    durationMs = dur,
                )
                if (s.currentSong != null && pos > 0) {
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
        private const val URL_RESOLVE_ATTEMPTS = 3

        private const val URL_RESOLVE_RETRY_MS = 1_200L

        @Volatile
        private var instance: PlayerManager? = null

        fun get(context: Context): PlayerManager =
            instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
    }
}
