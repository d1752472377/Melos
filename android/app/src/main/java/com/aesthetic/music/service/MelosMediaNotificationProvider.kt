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
import com.aesthetic.music.player.MediaCardFavorites
import com.aesthetic.music.player.MediaCardInfo
import com.aesthetic.music.player.PlayerManager
import com.google.common.collect.ImmutableList

class MelosMediaNotificationProvider(private val context: Context) : MediaNotification.Provider {

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        // 必须复用同一个实例，否则只能自己造 PendingIntent——那正是本次要摆脱的老路。
        (context as? PlaybackService)?.rememberActionFactory(actionFactory)

        val manager = PlayerManager.get(context)
        val player = mediaSession.player
        val state = manager.state.value
        val tagSong = player.currentMediaItem?.localConfiguration?.tag as? Song
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
            .setOngoing(isPlaying || (state.isLoading && song != null))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession))

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

        if (isLoading) {
            builder.setContentText("正在加载…").setSubText(artist)
        } else if (infoLine != null) {
            builder.setContentText(infoLine).setSubText(artist)
        } else {
            builder.setContentText(artist)
        }

        val isFav = MediaCardFavorites.isFavorite(song)
        val favoriteIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_TOGGLE_FAVORITE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val favoriteAction = NotificationCompat.Action.Builder(
            if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
            if (isFav) "取消收藏" else "收藏",
            favoriteIntent,
        ).build()
        builder.addAction(favoriteAction)

        val actions = buildTransportActions(mediaSession, actionFactory, isPlaying, iconRes)
        actions.forEach { builder.addAction(it) }

        val compactIndices = if (actions.size == 3) intArrayOf(1, 2, 3) else intArrayOf(0, 1)
        builder.setStyle(
            MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowActionsInCompactView(*compactIndices)
        )

        applyLiveUpdate(builder, song?.title)

        return MediaNotification(PlaybackService.NOTIFICATION_ID, builder.build())
    }

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

    private fun applyLiveUpdate(builder: NotificationCompat.Builder, title: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        try {
            if (!NotificationManagerCompat.from(context).canPostPromotedNotifications()) return
            builder.setRequestPromotedOngoing(true)
            MediaCardInfo.shortCriticalText(title)?.let { builder.setShortCriticalText(it) }
        } catch (_: Exception) {
        }
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean = false
}
