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
import com.aesthetic.music.R
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
 *
 * 修复（对应闪退 Crash: ForegroundServiceDidNotStartInTimeException）：
 *  - onCreate 尽早创建媒体通知渠道（Channel）。
 *  - **onStartCommand 里显式 startForeground() 作为兜底**：Media3 一旦播放态就绪会
 *    自动把通知升级为完整媒体通知，但在其完成前主线程可能因初始化而晚于 Android
 *    限定的前台超时，导致系统抛 ForegroundServiceDidNotStartInTimeException。
 *    这里先上一个小通知进入前台（幂等，Media3 会覆盖），从根上消除该崩溃。
 *  - MediaButtonReceiver 已在 AndroidManifest 注册，保证 media button 能绑定到本服务。
 *
 * 本次修改（"卡片上所有控制都不好使"）：
 *  - **删掉 `onPlayerCommandRequest` 覆写**。它是 media3 1.4 已 @Deprecated 的回调，而按媒体命令的
 *    实际分发流程（`MediaSessionStub` / `MediaSessionLegacyStub`：回调返回 SUCCESS 只是"放行"，
 *    命令随后仍会下发给 player），把切歌放在回调里执行等于多押一条随时可能不触发的路径。
 *    现在切歌在 [com.aesthetic.music.player.MelosSessionPlayer] 里执行——命令到 player 就切，
 *    与锁屏/蓝牙/超级岛/通知按钮共用同一条路径，且不会切两首。
 *  - 删除歌词行与进度的定时轮询：它们每 500ms ~ 3s 重建一次通知，是"点了没反应"的常见外部成因
 *    （通知被高频重建时，系统可能丢弃正在进行的点击），而系统媒体卡片的进度本来就来自会话状态。
 *  - 卡片内容/按钮的构造见 [MelosMediaNotificationProvider]。
 *
 * 注意：不能 release 共享的 PlayerManager.player（应用级单例 App 还要用）。
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
        // 用 sessionPlayer（不是裸 ExoPlayer）：它会按 App 自己的队列向系统声明
        // "还有上一首/下一首"，并在收到切歌命令时执行队列切歌（详见 MelosSessionPlayer）
        val player = PlayerManager.get(this).sessionPlayer
        mediaSession = MediaSession.Builder(this, player).build()

        // 自定义媒体卡片（封面 / 歌名 / 歌手 / 专辑·音质·音源）
        runCatching { setMediaNotificationProvider(MelosMediaNotificationProvider(this)) }

        instance = this
        startCardStateWatcher()
    }

    /**
     * 兜底前台化：Media3 有自己的前台逻辑（自动 startForeground / 更新通知），
     * 但为防其尚未完成时超出系统时限，这里先同步 startForeground 一次。
     * 后续 Media3 的 MediaNotificationProvider 会在此基础上更新为完整媒体通知。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        cardStateJob?.cancel()
        // 作用域整体取消：否则协程（含封面加载回调）会在服务销毁后继续持有引用
        cardScope.cancel()
        if (instance === this) instance = null
        // 不能 release 共享的 PlayerManager.player（App 单例还要用）
        mediaSession?.release()
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

        /**
         * 按最新播放状态重建媒体卡片。
         *
         * 服务没在跑就什么都不做（没有通知就没有需要更新的东西）。
         * ActionFactory 还没拿到时也直接返回：宁可用 media3 刚发的那条通知，
         * 也不要发一条没有按钮（或按钮是自家广播）的通知——那等于把控制弄没。
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
            val factory = actionFactory ?: return
            runCatching {
                val notification = MelosMediaNotificationProvider(service).createNotification(
                    session,
                    ImmutableList.of(),
                    factory,
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

    /** 由 [MelosMediaNotificationProvider] 在 media3 调用时登记（见那里的说明） */
    fun rememberActionFactory(factory: MediaNotification.ActionFactory) {
        actionFactory = factory
    }
}
