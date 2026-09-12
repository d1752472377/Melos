package com.aesthetic.music.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.UserNotice
import com.aesthetic.music.player.MediaCardFavorites
import com.aesthetic.music.player.PlayerManager

/**
 * 通知栏媒体卡片的按钮动作。
 *
 * 为什么不走媒体按钮 / 播放命令：media3 内部把通知动作做成"带 Player 命令的 PendingIntent"，
 * 但那个 PendingIntent 的构造细节（SessionToken + 命令 Bundle）没有公开 API，
 * 自己造很容易在个别 ROM 上失效。这里改成一个显式、可读的自家通道：
 * PendingIntent → [MediaCardActionReceiver]（本应用内广播）→ 调 PlayerManager / 收藏。
 *
 * 好处：通知初次构建（media3 触发）与"改设置后立即重绘"（[PlaybackService.refreshCard] 触发）
 * 走的是同一套动作实现，不会出现"刚装上有效、改一次设置按钮就失灵"的割裂。
 */
object MediaCardActions {

    const val ACTION_TOGGLE_PLAY = "com.aesthetic.music.card.TOGGLE_PLAY"
    const val ACTION_PREVIOUS = "com.aesthetic.music.card.PREVIOUS"
    const val ACTION_NEXT = "com.aesthetic.music.card.NEXT"
    const val ACTION_TOGGLE_FAVORITE = "com.aesthetic.music.card.TOGGLE_FAVORITE"

    /** 由接收器调用：执行动作并刷新卡片 */
    fun handle(context: Context, action: String?) {
        when (action) {
            ACTION_TOGGLE_PLAY -> {
                PlayerManager.get(context).toggle()
                // 播放/暂停是即时状态，立刻重绘让按钮图标马上切换
                PlaybackService.refreshCard(context)
            }
            ACTION_PREVIOUS -> switchTrack(context, next = false)
            ACTION_NEXT -> switchTrack(context, next = true)
            ACTION_TOGGLE_FAVORITE -> {
                toggleFavorite(context)
                // 收藏爱心要马上变
                PlaybackService.refreshCard(context)
            }
            else -> return
        }
    }

    /**
     * 切歌：交给 PlayerManager，卡片**不在这里重绘**。
     *
     * 为什么不在切歌后立刻重绘：切歌要先解析新歌直链（网络请求，可能几百毫秒到几秒），
     * 立刻重绘只会把"上一首"的信息再画一遍，看起来就是"点了没反应 / 切不动"。
     * 由 [PlaybackService] 监听播放状态（isLoading / 当前歌变化）在恰当的时机重绘，
     * 卡片上会先出现"正在加载…"，加载完再换成新歌信息。
     *
     * 队列只有一首时明确告知：此前会"重新播放同一首"，用户感受就是切歌坏了。
     */
    fun switchTrack(context: Context, next: Boolean) {
        val manager = PlayerManager.get(context)
        val queueSize = manager.state.value.queue.size
        if (queueSize <= 1) {
            UserNotice.post("当前队列只有一首歌，无法切换")
            return
        }
        if (next) manager.next() else manager.previous()
    }

    fun toggleFavorite(context: Context) {
        val song: Song? = PlayerManager.get(context).state.value.currentSong
        if (song == null) return
        val nowFavorite = MediaCardFavorites.toggle(song)
        UserNotice.post(if (nowFavorite) "已收藏「${song.title}」" else "已取消收藏「${song.title}」")
    }
}

/** 卡片动作接收器（仅本应用内 PendingIntent 触发，不导出） */
class MediaCardActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        MediaCardActions.handle(context, intent?.action)
    }
}

/**
 * 卡片通知的 ActionFactory。
 *
 * 这里曾经有个坑：`createMediaAction` 无论传进来什么播放命令，都返回"播放/暂停"动作。
 * 也就是说 media3（或 ROM 自己在重建媒体通知时）只要按"下一首"来建按钮，拿到手的就是播放/暂停 ——
 * 这正是"媒体控制处切换不太好使"的一类成因。现在按命令精确映射。
 */
class MelosCardActionFactory(private val context: Context) : MediaNotification.ActionFactory {

    override fun createMediaAction(
        mediaSession: MediaSession,
        icon: IconCompat,
        title: CharSequence,
        command: Int,
    ): NotificationCompat.Action = customAction(icon, title, actionOfCommand(command))

    override fun createCustomAction(
        mediaSession: MediaSession,
        icon: IconCompat,
        title: CharSequence,
        action: String,
        extras: Bundle,
    ): NotificationCompat.Action = customAction(icon, title, action)

    override fun createCustomActionFromCustomCommandButton(
        mediaSession: MediaSession,
        customCommandButton: CommandButton,
    ): NotificationCompat.Action = throw UnsupportedOperationException("卡片不使用自定义布局按钮")

    override fun createMediaActionPendingIntent(mediaSession: MediaSession, command: Long): PendingIntent =
        pendingIntent(actionOfCommand(command.toInt()))

    fun action(icon: IconCompat, title: CharSequence, action: String): NotificationCompat.Action =
        customAction(icon, title, action)

    /** 播放命令 → 自家动作：只认三件事，其余一律退回播放/暂停（至少不会点了没反应） */
    private fun actionOfCommand(command: Int): String = when (command) {
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> MediaCardActions.ACTION_NEXT
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        -> MediaCardActions.ACTION_PREVIOUS
        else -> MediaCardActions.ACTION_TOGGLE_PLAY
    }

    private fun customAction(icon: IconCompat, title: CharSequence, action: String): NotificationCompat.Action =
        NotificationCompat.Action.Builder(icon, title, pendingIntent(action)).build()

    private fun pendingIntent(action: String): PendingIntent {
        val intent = Intent(context, MediaCardActionReceiver::class.java).setAction(action)
        // requestCode 用 action 的 hash：不同按钮拿到不同的 PendingIntent，
        // 否则 PendingIntent 复用会把所有按钮都变成同一个动作
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
