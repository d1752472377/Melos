package com.aesthetic.music.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.aesthetic.music.MainActivity
import com.aesthetic.music.R
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.source.SourceManager
import com.aesthetic.music.player.MediaCardInfo
import com.aesthetic.music.player.PlayerManager
import com.google.common.collect.ImmutableList

/**
 * 通知栏媒体卡片（自定义 MediaNotification.Provider）。
 *
 * ## 为什么自己写
 * media3 默认卡片只有传输控制、没有封面以外的信息行；
 * 这里保留卡片**内容**的自定义（封面、歌名、歌手、`专辑 · 音质 · 音源`），
 * 但**控制按钮不再自己造**（见下）。
 *
 * ## 本次重要修改：控制按钮改回 media3 官方通道
 * 此前为了"自己可控"，按钮用的是自家的显式广播（`MediaCardActionReceiver`），
 * 代价是绕开了 media3 的会话分发。实测反馈是**卡片上所有控制都不好使**。
 * 现在改用 `actionFactory.createMediaAction(session, icon, title, command)`——
 * media3 文档明确写着这类 action「will be handled by the library」：
 * 点击后作为**播放命令**派发给会话，与锁屏、蓝牙、耳机线、超级岛走的是同一条路径，
 * 也是系统媒体卡片唯一有官方保证的按钮形式。
 *
 * 配套改动：
 *  - 队列切歌由 [com.aesthetic.music.player.MelosSessionPlayer] 直接执行
 *    （命令最终一定落到 player 上，不再依赖已 deprecated 的 `onPlayerCommandRequest` 回调）；
 *  - 队列只有一首时**不添加**上一首/下一首按钮：不可用的按钮会显示出来但点了没反应，
 *    这正是"控制不好使"的观感来源之一。
 *
 * ## 已删除的卡片开关
 * 「显示歌词 / 歌词位置 / 播放进度 / 收藏按钮 / 封面取色 / 实时活动」这些开关全部去掉：
 *  - 歌词行与进度条在 Android 13+ 由系统媒体卡片自己按会话状态绘制，通知里的那份多数机型根本不看；
 *  - 收藏按钮与自家广播绑定，正是上面那条失效路径的一部分；
 *  - 封面取色（`setColorized`）只在很老的系统上有效果；
 *  - 真正需要"可配置"的东西本来就没有——卡片是系统 UI，App 只能提供数据。
 * 去掉后卡片只剩一件确定性的事：把当前播放的信息画对。
 *
 * Android 16（API 36）仍走官方「实时活动（Live Updates）」：
 * `setRequestPromotedOngoing(true)` 让正在播放的媒体通知被提升为状态栏胶囊/锁屏强调卡，
 * `setShortCriticalText` 给胶囊一行短文案（现在是歌名）。低版本系统完全跳过。
 */
class MelosMediaNotificationProvider(private val context: Context) : MediaNotification.Provider {

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        // media3 传进来的 ActionFactory 是**库自己实现**的（没有公开类可直接构造），
        // 它知道怎么把按钮变成会话命令。服务需要"手动重建通知"（切歌瞬间/封面解码完成）时
        // 必须复用同一个实例，否则只能自己造 PendingIntent——那正是本次要摆脱的老路。
        (context as? PlaybackService)?.rememberActionFactory(actionFactory)

        val manager = PlayerManager.get(context)
        val player = mediaSession.player
        val state = manager.state.value
        // media3 1.4 起 MediaItem.tag 已移到 localConfiguration.tag
        val tagSong = player.currentMediaItem?.localConfiguration?.tag as? Song
        // 切歌瞬间的取舍：PlayerManager 按下"下一首"会立刻把 currentIndex 指向新歌（isLoading=true），
        // 但 player 上仍然挂着上一首的 MediaItem。此时若信 player，卡片会停在上一首不动，
        // 用户看到的就是"切了但没反应"。所以加载中一律以"App 认定的当前歌"为准。
        val song: Song? = if (state.isLoading) state.currentSong ?: tagSong else tagSong ?: state.currentSong
        val isPlaying = player.isPlaying
        val isLoading = state.isLoading && song != null

        val iconRes = { res: Int -> IconCompat.createWithResource(context, res) }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, PlaybackService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setContentTitle(song?.title ?: "Melos")
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            // 立刻显示为前台服务通知，避免部分 ROM 上"已经开始播放但通知还没出现"
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession))

        // 封面：卡片/锁屏/超级岛都读这里。取不到（未缓存或加载失败）时不设，卡片照常显示文字。
        // 元数据里的 artworkData 也会给系统一份（见 PlayerManager），两条路互为兜底。
        MediaCardArtwork.cached(context, song?.artwork)?.let { builder.setLargeIcon(it) }

        val artist = MediaCardInfo.artistLine(song?.artist)
        val infoLine = if (song != null) {
            MediaCardInfo.infoLine(
                album = song.album,
                quality = com.aesthetic.music.data.settings.SettingsStore.getQuality().value,
                sourceName = SourceManager.lastSourceName.value?.takeIf { it.isNotBlank() },
            )
        } else {
            null
        }

        // 取链中：明确写"正在加载"，用户按了下一首能立刻看到反馈（不是"点了没反应"）
        if (isLoading) {
            builder.setContentText("正在加载…").setSubText(artist)
        } else if (infoLine != null) {
            builder.setContentText(infoLine).setSubText(artist)
        } else {
            builder.setContentText(artist)
        }

        val actions = buildTransportActions(mediaSession, actionFactory, isPlaying, iconRes)
        actions.forEach { builder.addAction(it) }
        // 折叠态显示全部按钮：现在最多三个（上一首 / 播放暂停 / 下一首），
        // 不存在"收藏按钮挤掉下一首"的老问题，也就不需要再挑下标
        if (actions.isNotEmpty()) {
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(*IntArray(actions.size) { it })
            )
        }

        applyLiveUpdate(builder, song?.title)

        return MediaNotification(PlaybackService.NOTIFICATION_ID, builder.build())
    }

    /**
     * 传输控制按钮：走 media3 官方 action 通道（点击作为播放命令派发给会话）。
     *
     * 顺序由 [MediaCardInfo.transportKinds] 统一给出（有单测钉住：播放控制永远在最前，
     * 队列只有一首时不画上一首/下一首这种点了没反应的假按钮）。
     */
    private fun buildTransportActions(
        mediaSession: MediaSession,
        actionFactory: MediaNotification.ActionFactory,
        isPlaying: Boolean,
        iconRes: (Int) -> IconCompat,
    ): List<NotificationCompat.Action> {
        val canSwitch = PlayerManager.get(context).state.value.queue.size > 1
        return MediaCardInfo.transportKinds(canSwitch).map { kind ->
            when (kind) {
                MediaCardInfo.ActionKind.PREVIOUS -> actionFactory.createMediaAction(
                    mediaSession,
                    iconRes(androidx.media3.session.R.drawable.media3_icon_previous),
                    "上一首",
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                )
                MediaCardInfo.ActionKind.PLAY_PAUSE -> actionFactory.createMediaAction(
                    mediaSession,
                    iconRes(
                        if (isPlaying) androidx.media3.session.R.drawable.media3_icon_pause
                        else androidx.media3.session.R.drawable.media3_icon_play
                    ),
                    if (isPlaying) "暂停" else "播放",
                    Player.COMMAND_PLAY_PAUSE,
                )
                MediaCardInfo.ActionKind.NEXT -> actionFactory.createMediaAction(
                    mediaSession,
                    iconRes(androidx.media3.session.R.drawable.media3_icon_next),
                    "下一首",
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                )
            }
        }
    }

    /**
     * Android 16 实时活动（官方公开 API）。
     *
     * 为什么要判断 `canPostPromotedNotifications()`：系统可以全局关闭"实时活动"，
     * 关掉后强行 requestPromotedOngoing 不会生效，这里就没必要设置（也就不必假装支持）。
     */
    private fun applyLiveUpdate(builder: NotificationCompat.Builder, title: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        try {
            if (!NotificationManagerCompat.from(context).canPostPromotedNotifications()) return
            builder.setRequestPromotedOngoing(true)
            MediaCardInfo.shortCriticalText(title)?.let { builder.setShortCriticalText(it) }
        } catch (_: Exception) {
            // 个别 ROM 的实现可能不认识这两个方法；失败就当没开，卡片行为不受影响
        }
    }

    /**
     * media3 派发过来的自定义命令。
     *
     * 卡片已经不再使用自定义动作（按钮全部走 `createMediaAction`），所以这里**什么也不处理**，
     * 统一返回 false 让 media3 按默认流程走。保留实现是接口要求（该方法无默认实现）。
     */
    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean = false
}
