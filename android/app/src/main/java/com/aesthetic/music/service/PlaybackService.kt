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

/**
 * 系统媒体通知服务（Media3 MediaSessionService）。
 */
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
        val session = MediaSession.Builder(this, player).build()
        mediaSession = session
        addSession(session)

        // 自定义媒体卡片（封面 / 歌名 / 歌手 / 专辑·音质·音源 / 收藏按钮）
        runCatching { setMediaNotificationProvider(MelosMediaNotificationProvider(this)) }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (foregroundedWithId != NOTIFICATION_ID) {
                    startForeground(NOTIFICATION_ID, buildForegroundNotification())
                    foregroundedWithId = NOTIFICATION_ID
                }
            } catch (_: Exception) {
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    internal fun buildForegroundNotification(): Notification {
        val session = mediaSession
        val song = PlayerManager.get(this).state.value.currentSong
        val isPlaying = session?.player?.isPlaying ?: PlayerManager.get(this).state.value.isPlaying
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.aesthetic.music.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(song?.title ?: "Melos")
            .setContentText(song?.artist ?: "本地播放")
            .setOngoing(isPlaying)
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
        // App 被划掉时：如果没在播放则停掉服务
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        cardStateJob?.cancel()
        // 作用域整体取消：否则协程（含封面加载回调）会在服务销毁后继续持有引用
        cardScope.cancel()
        if (instance === this) instance = null
        mediaSession?.let {
            removeSession(it)
            it.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * 卡片跟随"当前歌 / 加载态 / 播放态"自动重绘。
     *
     * 为什么必须自己监听：media3 只在**媒体条目**变化时重建通知，而切歌的过程是
     * `currentIndex 立刻变 → isLoading=true → 解析直链 → 真正 setMediaItem`，
     * 前半段没有任何媒体条目变化，卡片会一直停在上一首（用户感受：切歌没反应）。
     * 这里监听 PlayerManager 的状态流，切歌瞬间就重绘（标题换成新歌 + "正在加载…"），
     * 加载完成后再重绘一次（换成信息行）。
     *
     * 顺带做封面预加载：新歌封面解码完成后回调重绘，避免"先出文字、封面几秒后才蹦出来"。
     *
     * 注意签名只含"歌 + 加载态 + 播放态"：进度变化不参与，否则 500ms 一次的状态流会把通知重建打成刷屏。
     */
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
                // 新歌封面预取：解码完再补一次重绘，把封面补上
                // （服务已销毁时 refreshCard 会因实例为空直接返回，这里无需再判断存活）
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

        /**
         * 通知 id：直接用 media3 默认媒体通知的 id（已核对源码：`DefaultMediaNotificationProvider`
         * 里 `DEFAULT_NOTIFICATION_ID = 1001`，且 `MediaNotificationManager` 就是用
         * `notification.notificationId` 去 `startForeground` / `notify` / `cancel`）。
         *
         * 两边同 id 才意味着"同一条通知"：我们的兜底通知（[buildIdleNotification]）会被 media3
         * 建好的完整媒体通知**原地覆盖**，而不是多出一条；[refreshCard] 手动刷新时也是在原地更新。
         */
        const val NOTIFICATION_ID: Int = DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID

        /** 当前服务实例（未运行时为 null：此时没有通知需要刷新） */
        @Volatile
        private var instance: PlaybackService? = null

        /**
         * media3 传给 Provider 的 ActionFactory（**库自己实现**，没有公开类可直接 new）。
         *
         * 卡片上的上一首/播放暂停/下一首就是用它生成的：点击后作为播放命令派发给会话，
         * 与锁屏/蓝牙/超级岛同一条路径。手动重建通知时必须复用同一个实例，
         * 否则只能自己造 PendingIntent —— 那正是"控制全都不好使"的老实现。
         */
        @Volatile
        private var actionFactory: MediaNotification.ActionFactory? = null

        /** 主线程 Handler：refreshCard 的线程兜底（见那里的说明） */
        private val mainHandler = android.os.Handler(Looper.getMainLooper())

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

        /**
         * 按最新播放状态重建媒体卡片。
         *
         * 服务没在跑就什么都不做（没有通知就没有需要更新的东西）。
         * 若 ActionFactory 尚未就绪，使用 buildForegroundNotification 兜底，
         * 确保通知栏始终呈现规范的 MediaStyle 媒体卡片。
         *
         * **必须在主线程调用**：`MediaSession` / `Player` 只在创建它们的线程（这里是主线程）上可访问，
         * 跨线程读 `currentMediaItem` 会抛异常。调用方分布在播放状态流、封面解码回调、设置项变更三处，
         * 与其要求每个调用方记住这条规则，不如在这里统一兜住：不在主线程就 post 回主线程。
         */
        @JvmStatic
        fun refreshCard(context: Context) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post { refreshCard(context.applicationContext) }
                return
            }
            val service = instance ?: return
            val session = service.mediaSession ?: return
            val factory = actionFactory
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
                // 与服务前台通知同 id：等于原地更新这条通知，不会多出一条
                val manager = NotificationManagerCompat.from(service)
                if (manager.areNotificationsEnabled()) {
                    manager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    /** 由 [MelosMediaNotificationProvider] 在 media3 调用时登记（见那里的说明） */
    fun rememberActionFactory(factory: MediaNotification.ActionFactory) {
        actionFactory = factory
    }
}
