package com.aesthetic.music.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
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
            ACTION_TOGGLE_PLAY -> PlayerManager.get(context).toggle()
            ACTION_PREVIOUS -> PlayerManager.get(context).previous()
            ACTION_NEXT -> PlayerManager.get(context).next()
            ACTION_TOGGLE_FAVORITE -> toggleFavorite(context)
            else -> return
        }
        // 立刻重绘：收藏爱心要马上变，切歌后标题/歌词也要跟上（不等下一次播放状态回调）
        PlaybackService.refreshCard(context)
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
 * 卡片通知的 ActionFactory：全部按钮都走自定义动作。
 *
 * 这样通知里不存在"点了没反应"的播放命令（本 App 的 timeline 只有一首，
 * 系统默认的 next/previous 是 no-op），切歌语义完全由 PlayerManager 的队列决定。
 */
class MelosCardActionFactory(private val context: Context) : MediaNotification.ActionFactory {

    override fun createMediaAction(
        mediaSession: MediaSession,
        icon: IconCompat,
        title: CharSequence,
        command: Int,
    ): NotificationCompat.Action = customAction(icon, title, MediaCardActions.ACTION_TOGGLE_PLAY)

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
        pendingIntent(MediaCardActions.ACTION_TOGGLE_PLAY)

    fun action(icon: IconCompat, title: CharSequence, action: String): NotificationCompat.Action =
        customAction(icon, title, action)

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
