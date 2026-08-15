package com.aesthetic.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aesthetic.music.R
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.player.PlayerManager

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
 * 注意：不能 release 共享的 PlayerManager.player（应用级单例 App 还要用）。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var foregroundedWithId = -1

    override fun onCreate() {
        // 先保证通知渠道存在，再走 Media3 前台逻辑，避免超时崩溃
        ensureChannelExists(this)
        super.onCreate()
        SettingsStore.init(this)
        val player = PlayerManager.get(this).player
        mediaSession = MediaSession.Builder(this, player).build()
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
        // 不能 release 共享的 PlayerManager.player（App 单例还要用）
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "media_session"
        private const val CHANNEL_NAME = "媒体播放"
        private const val NOTIFICATION_ID = 1001

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
    }
}
