package com.aesthetic.music.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.aesthetic.music.MainActivity
import com.aesthetic.music.R
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MediaCardLyricPosition
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.source.SourceManager
import com.aesthetic.music.player.MediaCardFavorites
import com.aesthetic.music.player.MediaCardInfo
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
 * ⚠ 代价（本次修复）：media3 的默认 Provider 自带封面加载（BitmapLoader），
 * 换掉它以后封面就没人设了 —— 卡片、锁屏、HyperOS 超级岛同时变成"啥也没有"。
 * 所以这里必须自己 `setLargeIcon`，见 [MediaCardArtwork]。
 *
 * 卡片内容（都可从「设置 → 媒体卡片」开关）：
 * 封面大图 · 歌名 · 歌手 · `专辑 · 音质 · 音源` · 歌词行 · 进度条 · 四个按钮。
 *
 * Android 16（API 36）额外走官方「实时活动（Live Updates）」：
 * `setRequestPromotedOngoing(true)` 让正在播放的媒体通知被提升为状态栏胶囊/锁屏强调卡
 * （HyperOS 3 等系统把它画在灵动岛位置），`setShortCriticalText` 给胶囊一行短文案，
 * 点击即展开完整卡片 —— 这是目前**唯一有公开文档**的"灵动岛式"系统能力。
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
        val tagSong = player.currentMediaItem?.localConfiguration?.tag as? Song
        // 切歌瞬间的取舍：PlayerManager 按下"下一首"会立刻把 currentIndex 指向新歌（isLoading=true），
        // 但 player 上仍然挂着上一首的 MediaItem。此时若信 player，卡片会停在上一首不动，
        // 用户看到的就是"切了但没反应"（实测反馈"媒体控制处切换不太好使"）。
        // 所以加载中一律以"App 认定的当前歌"为准。
        val song: Song? = if (state.isLoading) state.currentSong ?: tagSong else tagSong ?: state.currentSong
        val isPlaying = player.isPlaying
        val isLoading = state.isLoading && song != null

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

        // 按钮顺序：**三个播放控制永远排在最前**，收藏按钮永远排最后。
        // 用户实测反馈：「有上一首，没有下一首」——原因就是收藏按钮被放到了最左/最前，
        // 而系统媒体卡片只渲染前几个按钮，于是"下一首"被挤出行外。
        // 这里把顺序钉死（顺序规则由 MediaCardInfo.actionOrder 统一给出，并有单测覆盖），
        // 收藏按钮的位置设置也据此取消：位置可调换来的代价是播放控制消失，不值得。
        val actions = mutableListOf<NotificationCompat.Action>()
        for (kind in MediaCardInfo.actionOrder(showFavorite = prefs.mediaCardFavorite)) {
            actions += when (kind) {
                MediaCardInfo.ActionKind.PREVIOUS -> prevAction
                MediaCardInfo.ActionKind.PLAY_PAUSE -> playAction
                MediaCardInfo.ActionKind.NEXT -> nextAction
                MediaCardInfo.ActionKind.FAVORITE -> favoriteAction
            }
        }

        // 折叠态固定显示前三个（= 上一首 / 播放暂停 / 下一首）：
        // 展开行里第 4 个收藏按钮不参与折叠态，避免它挤掉"下一首"
        val compact = MediaCardInfo.compactIndices(actions.size)

        // 切歌加载中不显示上一首的歌词：那会让"已经切过去了"这件事看起来没发生
        val lyricLine = if (prefs.mediaCardShowLyric && !isLoading) currentLyricLine(manager) else null
        val artist = MediaCardInfo.artistLine(song?.artist)
        val infoLine = if (prefs.mediaCardAlbum && song != null) {
            MediaCardInfo.infoLine(
                album = song.album,
                quality = SettingsStore.getQuality().value,
                sourceName = lastSourceLabel(),
                showAlbum = true,
            )
        } else {
            null
        }

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
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(*compact)
            )

        // 封面：卡片/锁屏/超级岛都读这里。取不到（未缓存或加载失败）时不设，卡片照常显示文字
        val artwork = if (prefs.mediaCardCover) MediaCardArtwork.cached(context, song?.artwork) else null
        if (artwork != null) {
            builder.setLargeIcon(artwork)
            if (prefs.mediaCardColorized) {
                MediaCardArtwork.accentColor(artwork)?.let { accent ->
                    builder.setColorized(true).setColor(accent)
                }
            }
        }

        // 信息行与歌词行争夺"副标题/正文"两个位置：
        // 歌词在上时正文留给信息行，歌词在下时正文是歌词、副标题留歌手，没有歌词就按信息行排
        when {
            // 取链中：明确写"正在加载"，用户按了下一首能立刻看到反馈（不是"点了没反应"）
            isLoading -> {
                builder.setContentText("正在加载…")
                builder.setSubText(artist)
            }
            lyricLine != null && prefs.mediaCardLyricPosition == MediaCardLyricPosition.ABOVE -> {
                builder.setSubText(lyricLine)
                builder.setContentText(infoLine ?: artist)
            }
            lyricLine != null -> {
                builder.setContentText(lyricLine)
                builder.setSubText(artist)
            }
            infoLine != null -> {
                builder.setContentText(infoLine)
                builder.setSubText(artist)
            }
            else -> {
                builder.setContentText(artist)
            }
        }

        // 进度：系统媒体卡片主要用 MediaSession 的 PlaybackState 画进度条，
        // 但锁屏紧凑态与部分 ROM（MIUI/HyperOS）读的是通知自身的 progress，两边都设
        if (prefs.mediaCardProgress) {
            val progress = manager.progress.value
            val duration = player.duration.takeIf { it > 0 } ?: progress.durationMs
            if (duration > 0) {
                builder.setProgress(
                    1000,
                    (MediaCardInfo.progressFraction(progress.positionMs, duration) * 1000).toInt(),
                    false,
                )
            }
        }

        actions.forEach { builder.addAction(it) }

        applyLiveUpdate(builder, prefs.mediaCardLiveUpdate, lyricLine, song?.title)

        return MediaNotification(PlaybackService.NOTIFICATION_ID, builder.build())
    }

    /**
     * Android 16 实时活动（官方公开 API）。
     *
     * 为什么要判断 `canPostPromotedNotifications()`：系统可以全局关闭"实时活动"，
     * 关掉后强行 requestPromotedOngoing 不会生效，这里就没必要设置（也就不必假装支持）。
     *
     * 低版本系统 / 旧 ROM 上这些调用完全跳过，卡片行为与之前一致。
     */
    private fun applyLiveUpdate(
        builder: NotificationCompat.Builder,
        enabled: Boolean,
        lyricLine: String?,
        title: String?,
    ) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        try {
            // 先问系统：用户关掉全局"实时活动"后系统不允许提升，那就不设置（也不假装支持）
            if (!NotificationManagerCompat.from(context).canPostPromotedNotifications()) return
            builder.setRequestPromotedOngoing(true)
            MediaCardInfo.shortCriticalText(lyricLine, title)?.let { builder.setShortCriticalText(it) }
        } catch (_: Exception) {
            // 个别 ROM 的实现可能不认识这两个方法；失败就当没开，卡片行为不受影响
        }
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

    /** 音源展示：优先"正在使用的音源"（插件名/内置直连），退到平台名 */
    private fun lastSourceLabel(): String? =
        SourceManager.lastSourceName.value?.takeIf { it.isNotBlank() }
}
