package com.aesthetic.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.aesthetic.music.R
import com.aesthetic.music.player.MediaCardFavorites
import com.aesthetic.music.player.PlayerManager
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var foregroundedWithId = -1

    private val cardScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cardStateJob: Job? = null

    override fun onCreate() {
        // 先保证通知渠道存在，再走 Media3 前台逻辑，避免超时崩溃
        ensureChannelExists(this)
        super.onCreate()
        val player = PlayerManager.get(this).sessionPlayer
        // 明确提供会话的打开入口：通知/锁屏/小米超级岛点击时走这条 PendingIntent，
        // 系统不再按包名自行拼 Intent（部分 ROM 会因此报 “click open app” 并卡住）。
        val session = MediaSession.Builder(this, player)
            .setSessionActivity(openAppPendingIntent(this))
            .build()
        mediaSession = session
        addSession(session)

        runCatching { setMediaNotificationProvider(MelosMediaNotificationProvider(this)) }
            .onFailure { LogReporter.w("PlaybackService", "设置自定义媒体通知失败", it) }

        instance = this
        startCardStateWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_FAVORITE -> {
                val song = PlayerManager.get(this).state.value.currentSong
                if (song != null) {
                    MediaCardFavorites.toggle(song)
                    refreshCard(this)
                }
                return START_NOT_STICKY
            }
            ACTION_PLAY_PAUSE -> {
                val p = mediaSession?.player
                if (p != null) {
                    if (p.isPlaying) p.pause() else p.play()
                }
                return START_NOT_STICKY
            }
            ACTION_PREVIOUS -> {
                mediaSession?.player?.seekToPreviousMediaItem()
                return START_NOT_STICKY
            }
            ACTION_NEXT -> {
                mediaSession?.player?.seekToNextMediaItem()
                return START_NOT_STICKY
            }
        }
        // startForeground 每次调用都会真实发一条通知，重复调用会撞上系统的
        // 通知入队速率上限（Package enqueue rate ... Shedding），这里只在还没进前台时调。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && foregroundedWithId != NOTIFICATION_ID) {
            try {
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
                foregroundedWithId = NOTIFICATION_ID
            } catch (_: Exception) {
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    internal fun buildForegroundNotification(): Notification {
        val session = mediaSession
        val song = PlayerManager.get(this).state.value.currentSong
        val isPlaying = session?.player?.isPlaying ?: PlayerManager.get(this).state.value.isPlaying
        val isLoading = PlayerManager.get(this).state.value.isLoading
        val isOngoing = isPlaying || isLoading
        val contentIntent = openAppPendingIntent(this)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(song?.title ?: "Melos")
            .setContentText(song?.artist ?: "本地播放")
            .setOngoing(isOngoing)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

        MediaCardArtwork.cached(this, song?.artwork)?.let { builder.setLargeIcon(it) }

        if (session != null) {
            val isFav = MediaCardFavorites.isFavorite(song)
            val favIntent = PendingIntent.getService(
                this,
                0,
                Intent(this, PlaybackService::class.java).apply { action = ACTION_TOGGLE_FAVORITE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                if (isFav) "取消收藏" else "收藏",
                favIntent,
            )

            val prevIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, PlaybackService::class.java).apply { action = ACTION_PREVIOUS },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val playIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val nextIntent = PendingIntent.getService(
                this,
                3,
                Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(androidx.media3.session.R.drawable.media3_icon_previous, "上一首", prevIntent)
            builder.addAction(
                if (isPlaying) androidx.media3.session.R.drawable.media3_icon_pause
                else androidx.media3.session.R.drawable.media3_icon_play,
                if (isPlaying) "暂停" else "播放",
                playIntent,
            )
            builder.addAction(androidx.media3.session.R.drawable.media3_icon_next, "下一首", nextIntent)

            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(1, 2, 3)
            )
        }
        return builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        cardStateJob?.cancel()
        cardScope.cancel()
        if (instance === this) instance = null
        mediaSession?.let {
            removeSession(it)
            it.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun startCardStateWatcher() {
        cardStateJob?.cancel()
        cardStateJob = cardScope.launch {
            val manager = PlayerManager.get(this@PlaybackService)
            var lastSignature: String? = null
            manager.state.collect { state ->
                val song = state.currentSong
                val signature = "${song?.platform}:${song?.id}|loading=${state.isLoading}|playing=${state.isPlaying}"
                if (signature == lastSignature) return@collect
                lastSignature = signature

                // startForeground 本身也会发一条通知，所以只在"还没进前台"时调一次；
                // 之后的状态更新统一交给 refreshCard（它自带去重与节流）。
                if ((state.isPlaying || state.isLoading) &&
                    foregroundedWithId != NOTIFICATION_ID &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ) {
                    try {
                        startForeground(NOTIFICATION_ID, buildForegroundNotification())
                        foregroundedWithId = NOTIFICATION_ID
                    } catch (e: Exception) {
                        LogReporter.w("PlaybackService", "保持前台服务失败", e)
                    }
                }

                MediaCardArtwork.prefetch(this@PlaybackService, song?.artwork) {
                    refreshCard(this@PlaybackService)
                }
                refreshCard(this@PlaybackService)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "media_session"
        private const val CHANNEL_NAME = "媒体播放"

        const val NOTIFICATION_ID: Int = DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID

        @Volatile
        private var instance: PlaybackService? = null

        @JvmStatic
        fun isRunning(): Boolean = instance != null

        @Volatile
        private var actionFactory: MediaNotification.ActionFactory? = null

        /**
         * 上一次真正发出去的通知内容签名。
         *
         * 系统对同一应用有"通知入队速率"上限（约 5 条/秒），超了就丢弃并打
         * `Package enqueue rate is ... Shedding`。切歌瞬间会连着触发
         * 状态变化 → startForeground、封面加载完成 → refreshCard、健康检查 → refreshCard，
         * 短时间能攒到 6 条以上，被丢掉的那几条还会让灵动岛反复重建。
         *
         * 这里做两层拦：内容没变直接不发（绝大多数重复调用都是这种），
         * 内容变了也至少间隔 [NOTIFY_MIN_INTERVAL_MS] 才发。
         */
        @Volatile
        private var lastPostedSignature: String? = null

        @Volatile
        private var lastPostedAt = 0L

        private const val NOTIFY_MIN_INTERVAL_MS = 400L

        /** 节流用的延后任务令牌，重发前先按它撤掉上一个，避免堆积 */
        private val POST_TOKEN = Any()

        /** 主线程 Handler：refreshCard 的线程兜底（见那里的说明） */
        private val mainHandler = android.os.Handler(Looper.getMainLooper())

        /**
         * 打开 App 的唯一入口 PendingIntent：媒体会话、通知正文共用。
         *
         * 带 SINGLE_TOP | CLEAR_TOP 让点击落在已有任务栈上而不是重建 Activity；
         * 部分 ROM（如小米超级岛）点击媒体卡片时若拿不到会话的活动入口，会自己按包名
         * 拼 Intent，表现为反复 "onIslandClick: click open app" 卡住。
         */
        @JvmStatic
        fun openAppPendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, com.aesthetic.music.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** 幂等地创建媒体通知渠道，避免与时序竞态导致 startForeground 失败 */
        @JvmStatic
        fun ensureChannelExists(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val manager = context.getSystemService(NotificationManager::class.java) ?: return
                    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                        manager.createNotificationChannel(
                            NotificationChannel(
                                CHANNEL_ID,
                                CHANNEL_NAME,
                                NotificationManager.IMPORTANCE_LOW,
                            ).apply { description = "播放控制通知" }
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        const val ACTION_TOGGLE_FAVORITE = "com.aesthetic.music.action.TOGGLE_FAVORITE"
        const val ACTION_PLAY_PAUSE = "com.aesthetic.music.action.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.aesthetic.music.action.PREVIOUS"
        const val ACTION_NEXT = "com.aesthetic.music.action.NEXT"

        @JvmStatic
        fun refreshCard(context: Context) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post { refreshCard(context.applicationContext) }
                return
            }
            val service = instance ?: return
            val session = service.mediaSession ?: return
            val factory = actionFactory

            // 通知内容签名：只有它变了才值得发一条新通知
            val state = PlayerManager.get(service).state.value
            val song = state.currentSong
            val signature = buildString {
                append(song?.platform).append(':').append(song?.id)
                append('|').append(song?.title)
                append('|').append(song?.artist)
                append("|playing=").append(state.isPlaying)
                append("|loading=").append(state.isLoading)
                append("|fav=").append(MediaCardFavorites.isFavorite(song))
                append("|art=").append(MediaCardArtwork.cached(service, song?.artwork) != null)
            }
            if (signature == lastPostedSignature) return
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastPostedAt < NOTIFY_MIN_INTERVAL_MS) {
                // 太密了：把这次延后到窗口末尾再发，避免和下一次抖动叠在一起
                mainHandler.removeCallbacksAndMessages(POST_TOKEN)
                mainHandler.postAtTime(
                    Runnable { runCatching { refreshCard(service.applicationContext) } },
                    POST_TOKEN,
                    lastPostedAt + NOTIFY_MIN_INTERVAL_MS,
                )
                return
            }

            runCatching {
                val notification = if (factory != null) {
                    MelosMediaNotificationProvider(service).createNotification(
                        session,
                        ImmutableList.of(),
                        factory,
                        MediaNotification.Provider.Callback { },
                    ).notification
                } else {
                    service.buildForegroundNotification()
                }
                val manager = NotificationManagerCompat.from(service)
                if (manager.areNotificationsEnabled()) {
                    manager.notify(NOTIFICATION_ID, notification)
                }
                lastPostedSignature = signature
                lastPostedAt = now
            }.onFailure { LogReporter.w("PlaybackService", "刷新媒体卡片失败", it) }
        }
    }

    fun rememberActionFactory(factory: MediaNotification.ActionFactory) {
        actionFactory = factory
    }
}
