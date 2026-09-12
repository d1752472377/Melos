package com.aesthetic.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.aesthetic.music.R
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.player.currentLyricIndex
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 系统媒体通知服务（Media3 MediaSessionService）。
 *
 * 修复（对应闪退 Crash: ForegroundServiceDidNotStartInTimeException）：
 *  - onCreate 尽早创建媒体通知渠道（Channel）。
 *  - **onStartCommand 里显式 startForeground() 作为兜底**：Media3 一旦播放态就绪会
 *    自动把通知升级为完整媒体通知，但在其完成前主线程可能因初始化而晚于 Android
 *    限定的前台超时，导致系统抛 ForegroundServiceDidNotStartInTimeException。
 *    这里先上一个小通知进入前台（幂等，Media3 会覆盖），从根上消除该崩溃。
 *  - MediaButtonReceiver 已在 AndroidManifest 注册，保证 media button 能绑定到本服务。
 *
 * 本次新增（设置 → 媒体卡片）：
 *  - 装配自定义 [MelosMediaNotificationProvider]：卡片带收藏按钮、按钮位置可选、可显示歌词；
 *  - 接管"上一首/下一首"播放命令（`onPlayerCommandRequest`，1.4.1 已标注 @Deprecated 但仍由
 *    MediaSessionImpl 派发）：本 App 的 timeline 只有一首（单曲 setMediaItem 模式），
 *    系统默认命令是 no-op，转发到 PlayerManager 的队列逻辑后，蓝牙/耳机线的切歌才真正可用；
 *  - 暴露 [refreshCard] 给设置页/卡片动作重建通知（改设置立即生效）。
 *
 * 注意：不能 release 共享的 PlayerManager.player（应用级单例 App 还要用）。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var foregroundedWithId = -1

    /** 歌词行跟随播放滚动：定时比对当前歌词句，变了才重绘通知 */
    private val cardScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lyricWatchJob: Job? = null
    private var lastLyricLine: String? = null

    override fun onCreate() {
        // 先保证通知渠道存在，再走 Media3 前台逻辑，避免超时崩溃
        ensureChannelExists(this)
        super.onCreate()
        val player = PlayerManager.get(this).player
        // media3 1.4.1 的 MediaSession.Builder 没有 (context, player, callback) 三参构造，
        // MediaSession.Callback 也没有可覆写的 onSeekToNext/PreviousMediaItem（切歌命令
        // 由 player 的 timeline 可用性决定，session 不提供拦截点）——此前那次"修复"编不过。
        // 现解法：用 onPlayerCommandRequest 拦截"上一条/下一条"播放命令（卡片与蓝牙共用该命令），
        // 转发到 PlayerManager 的队列逻辑，从而让卡片/蓝牙的切歌真正可用。
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                /**
                 * 接管"上一条/下一条"播放命令。
                 *
                 * 本 App 的 timeline 只有一首（单曲 setMediaItem 模式），系统默认认为
                 * hasNextMediaItem() = false，于是卡片/蓝牙/耳机线的下一首是死键。
                 * 这里把命令转发给 PlayerManager 的队列逻辑（顺序/循环/随机都在那边），
                 * 顺带把"蓝牙切歌无效"这个老问题一起修掉。
                 */
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int,
                ): Int {
                    val manager = PlayerManager.get(this@PlaybackService)
                    when (playerCommand) {
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_NEXT,
                        -> {
                            manager.next()
                            return SessionResult.RESULT_SUCCESS
                        }
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        -> {
                            manager.previous()
                            return SessionResult.RESULT_SUCCESS
                        }
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            })
            .build()

        // 自定义媒体卡片（收藏按钮 / 按钮位置 / 歌词显示）
        runCatching { setMediaNotificationProvider(MelosMediaNotificationProvider(this)) }

        instance = this
        startLyricWatcher()
    }

    /**
     * 兜底前台化：Media3 有自己的前台逻辑（自动 startForeground / 更新通知），
     * 但为防其尚未完成时超出系统时限，这里先同步 startForeground 一次。
     * 后续 Media3 的 MediaNotificationProvider 会在此基础上更新为完整媒体通知。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                if (foregroundedWithId != NOTIFICATION_ID) {
                    startForeground(NOTIFICATION_ID, buildIdleNotification())
                    foregroundedWithId = NOTIFICATION_ID
                }
            } catch (_: Exception) {
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun buildIdleNotification(): Notification {
        val song = PlayerManager.get(this).state.value.currentSong
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.aesthetic.music.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(song?.title ?: "Melos")
            .setContentText(song?.artist ?: "本地播放")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
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
        lyricWatchJob?.cancel()
        if (instance === this) instance = null
        // 不能 release 共享的 PlayerManager.player（App 单例还要用）
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    /** 每 500ms 检查当前歌词句，变化时重建通知（只在播放中轮询，暂停即停，不白耗电） */
    private fun startLyricWatcher() {
        lyricWatchJob?.cancel()
        lyricWatchJob = cardScope.launch {
            val manager = PlayerManager.get(this@PlaybackService)
            while (isActive) {
                delay(LYRIC_WATCH_INTERVAL_MS)
                if (!SettingsStore.readPrefs().mediaCardShowLyric) continue
                if (!manager.state.value.isPlaying) continue
                val line = currentLyricLine()
                if (line != lastLyricLine) {
                    lastLyricLine = line
                    refreshCard(this@PlaybackService)
                }
            }
        }
    }

    private fun currentLyricLine(): String? {
        val manager = PlayerManager.get(this)
        val state = manager.state.value
        if (state.lyrics.isEmpty()) return null
        val idx = currentLyricIndex(state.lyrics, manager.progress.value.positionMs)
        return state.lyrics.getOrNull(idx)?.text?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val CHANNEL_ID = "media_session"
        private const val CHANNEL_NAME = "媒体播放"

        /**
         * 兜底通知 id = 1001，与 Media3 默认媒体通知 id 保持一致是设计意图：
         * Media3 接管后会用完整媒体通知原地覆盖这个兜底通知。
         * 注：media3 1.4.1 的 MediaSessionService.NOTIFICATION_ID 非 Kotlin 可见常量，
         * 只能手写字面量（升级 media3 后应换回公开常量引用）。
         */
        const val NOTIFICATION_ID: Int = 1001

        private const val LYRIC_WATCH_INTERVAL_MS = 500L

        /** 当前服务实例（未运行时为 null：此时没有通知需要刷新） */
        @Volatile
        private var instance: PlaybackService? = null

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

        /**
         * 按最新设置重建媒体卡片。
         *
         * 由设置页（改收藏按钮/歌词/按钮位置）与卡片收藏动作后调用；
         * 服务没在跑就什么都不做（没有通知就没有需要更新的东西）。
         */
        @JvmStatic
        fun refreshCard(context: Context) {
            val service = instance ?: return
            val session = service.mediaSession ?: return
            runCatching {
                val notification = MelosMediaNotificationProvider(service).createNotification(
                    session,
                    ImmutableList.of(),
                    MelosCardActionFactory(service),
                    MediaNotification.Provider.Callback { },
                )
                // 与服务前台通知同 id：等于原地更新这条通知，不会多出一条
                val manager = NotificationManagerCompat.from(service)
                if (manager.areNotificationsEnabled()) {
                    manager.notify(notification.notificationId, notification.notification)
                }
            }
        }
    }
}
