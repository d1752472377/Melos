package com.aesthetic.music.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.aesthetic.music.MainActivity
import com.aesthetic.music.R
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MediaCardButtonPosition
import com.aesthetic.music.data.settings.MediaCardLyricPosition
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.player.MediaCardFavorites
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.player.currentLyricIndex
import com.google.common.collect.ImmutableList

/**
 * 通知栏媒体卡片（自定义 MediaNotification.Provider）。
 *
 * 为什么自己写而不是用 media3 默认实现：默认卡片只有传输控制，
 * 而设置页要求"收藏按钮 / 按钮位置 / 显示歌词 / 歌词位置"四项可配置，
 * 默认实现没有这些开关点。
 *
 * 实现要点：
 * - 播放/上一首/下一首用 [MediaNotification.ActionFactory.createMediaAction] 走 Player 命令，
 *   切歌命令由 [PlaybackService] 的 `onPlayerCommandRequest` 兜住（本 App 的 timeline 只有一首，
 *   系统默认的"下一条"是 no-op，必须自己接管，否则卡片上的下一首是死键）；
 * - 收藏用 [MediaNotification.ActionFactory.createCustomAction] 自定义命令，
 *   在 [PlaybackService] 的 `onCustomCommand` 里处理，状态与 App 内 ♡ 共享同一份收藏数据；
 * - 歌词取当前播放位置对应的那一句，由 PlaybackService 定时触发重绘（见 refreshCard）。
 */
class MelosMediaNotificationProvider(private val context: Context) : MediaNotification.Provider {

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val prefs = SettingsStore.readPrefs()
        val manager = PlayerManager.get(context)
        val player = mediaSession.player
        val state = manager.state.value
        // media3 1.4 起 MediaItem.tag 已移到 localConfiguration.tag
        val song: Song? = (player.currentMediaItem?.localConfiguration?.tag as? Song)
            ?: state.currentSong
        val isPlaying = player.isPlaying

        val iconRes = { res: Int -> IconCompat.createWithResource(context, res) }

        val playPauseIcon = iconRes(
            if (isPlaying) androidx.media3.session.R.drawable.media3_icon_pause
            else androidx.media3.session.R.drawable.media3_icon_play
        )
        val favorite = MediaCardFavorites.isFavorite(song)
        val favoriteIcon = iconRes(
            if (favorite) androidx.media3.session.R.drawable.media3_icon_heart_filled
            else androidx.media3.session.R.drawable.media3_icon_heart_unfilled
        )

        // 动作走自家工厂（MediaCardActionReceiver → PlayerManager），
        // 既避开 media3 内部 PendingIntent 细节，也保证"首次构建"与"改设置后重绘"完全一致
        val factory = if (actionFactory is MelosCardActionFactory) actionFactory
        else MelosCardActionFactory(context)

        val prevAction = factory.action(
            iconRes(androidx.media3.session.R.drawable.media3_icon_previous),
            "上一首",
            MediaCardActions.ACTION_PREVIOUS,
        )
        val playAction = factory.action(
            playPauseIcon,
            if (isPlaying) "暂停" else "播放",
            MediaCardActions.ACTION_TOGGLE_PLAY,
        )
        val nextAction = factory.action(
            iconRes(androidx.media3.session.R.drawable.media3_icon_next),
            "下一首",
            MediaCardActions.ACTION_NEXT,
        )
        val favoriteAction = factory.action(
            favoriteIcon,
            if (favorite) "取消收藏" else "收藏",
            MediaCardActions.ACTION_TOGGLE_FAVORITE,
        )

        // 按钮顺序：收藏按钮可在播放控制左侧或右侧
        val actions = mutableListOf<NotificationCompat.Action>()
        var favoriteIndex = -1
        if (prefs.mediaCardFavorite && prefs.mediaCardButtonPosition == MediaCardButtonPosition.LEFT) {
            favoriteIndex = actions.size
            actions += favoriteAction
        }
        val prevIndex = actions.size
        actions += prevAction
        val playIndex = actions.size
        actions += playAction
        val nextIndex = actions.size
        actions += nextAction
        if (prefs.mediaCardFavorite && prefs.mediaCardButtonPosition == MediaCardButtonPosition.RIGHT) {
            favoriteIndex = actions.size
            actions += favoriteAction
        }

        // 折叠态最多 3 个：开收藏时优先"收藏 + 播放"，否则显示完整传输控制
        val compact = when {
            favoriteIndex < 0 -> intArrayOf(prevIndex, playIndex, nextIndex)
            prefs.mediaCardButtonPosition == MediaCardButtonPosition.LEFT ->
                intArrayOf(favoriteIndex, playIndex)
            else -> intArrayOf(playIndex, favoriteIndex)
        }

        val lyricLine = if (prefs.mediaCardShowLyric) currentLyricLine(manager) else null
        val artist = song?.artist?.takeIf { it.isNotBlank() } ?: "未知歌手"

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
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(*compact)
            )

        // 歌词位置：在上 = 用副标题行；在下 = 用正文行（正文让给歌词时，歌手挪到副标题）
        when {
            lyricLine != null && prefs.mediaCardLyricPosition == MediaCardLyricPosition.ABOVE -> {
                builder.setSubText(lyricLine)
                builder.setContentText(artist)
            }
            lyricLine != null -> {
                builder.setContentText(lyricLine)
                builder.setSubText(artist)
            }
            else -> builder.setContentText(artist)
        }

        actions.forEach { builder.addAction(it) }

        return MediaNotification(PlaybackService.NOTIFICATION_ID, builder.build())
    }

    /**
     * 通知栏里的自定义命令。
     *
     * 卡片上的按钮走自家广播（见 [MediaCardActions]），本方法处理的是 media3 自己派发过来的
     * 自定义命令（MediaNotificationManager 会调用它）；两者都落到同一套动作上，行为一致。
     */
    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean {
        if (action.startsWith("com.aesthetic.music.card.")) {
            MediaCardActions.handle(context, action)
            return true
        }
        return false
    }

    /** 当前播放位置对应的歌词句（没有歌词/没在播时返回 null，卡片不显示歌词行） */
    private fun currentLyricLine(manager: PlayerManager): String? {
        val state = manager.state.value
        if (state.lyrics.isEmpty()) return null
        val position = manager.progress.value.positionMs
        val idx = currentLyricIndex(state.lyrics, position)
        return state.lyrics.getOrNull(idx)?.text?.trim()?.takeIf { it.isNotEmpty() }
    }
}
