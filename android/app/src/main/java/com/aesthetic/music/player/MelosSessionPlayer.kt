package com.aesthetic.music.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * 交给 [androidx.media3.session.MediaSession] 的播放器包装：把"队列里还有下一首/上一首"这件事告诉系统。
 *
 * 为什么需要它（这是"卡片上没有下一首 / 切歌不好使"的真正原因）：
 *
 * 本 App 的播放模型是**一次只往 ExoPlayer 里 set 一首歌**（每首歌的直链要先解析），
 * 真正的播放队列存在 [PlayerManager] 的 `state.queue` 里。而系统（通知栏媒体控件、锁屏、
 * HyperOS 超级岛、蓝牙、车机）判断"下一首能不能按"依据的是 **ExoPlayer 的 timeline**。
 * media3 1.4.1 的 `Util.getAvailableCommands()` 里写得很直白：
 *
 * ```
 * .addIf(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPreviousMediaItem && !isPlayingAd)
 * .addIf(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNextMediaItem && !isPlayingAd)
 * ```
 *
 * 单曲 timeline 下 `hasNextMediaItem()` 恒为 false，于是 `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM`
 * 根本不可用 → `MediaController.seekToNextMediaItem()` 直接在开头就 return，
 * 命令连 MediaSession 都到不了，表现就是"有上一首、没有下一首"。
 * （对比：`COMMAND_SEEK_TO_PREVIOUS` 只要求 timeline 非空，所以上一首一直能用。）
 *
 * 包装后：
 *  - 队列里有多首歌时，对系统声明 next/previous 命令可用、且 hasNext/hasPrevious 为真
 *    → 系统媒体控件里的"下一首"变成可用，命令到达 `MediaSession.Callback.onPlayerCommandRequest`，
 *      由 [PlayerManager.next] / [PlayerManager.previous] 按 App 自己的队列（顺序/随机/循环）切歌；
 *  - 队列只有一首时不声明，系统自己就会把按钮置灰（而不是点了重播同一首）。
 *
 * 另外必须吞掉播放器自身的 seekToNext/seekToPrevious：真正的切歌已经由队列逻辑做了，
 * 若再让 ExoPlayer 执行一次"下一条"，轻则切两首、重则回到原曲。
 */
@Suppress("DEPRECATION") // 覆写 Player 的 deprecated seekToNext/seekToPrevious
class MelosSessionPlayer(
    player: Player,
    private val hasQueueNavigation: () -> Boolean,
) : ForwardingPlayer(player) {

    /** 对系统声明可用的播放命令（媒体控件的按钮可用性就是按这个算的） */
    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (!hasQueueNavigation()) return base
        return Player.Commands.Builder()
            .addAll(base)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            // 老式控件（部分 ROM / 蓝牙）走的是不带 MEDIA_ITEM 的两个命令，一并声明
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()
    }

    /** 系统控件据此决定"下一首"是否可点（以及 PlaybackStateCompat 的 ACTION_SKIP_TO_NEXT 位） */
    override fun hasNextMediaItem(): Boolean = hasQueueNavigation() || super.hasNextMediaItem()

    override fun hasPreviousMediaItem(): Boolean = hasQueueNavigation() || super.hasPreviousMediaItem()

    // ===== 真正的切歌由 PlayerManager 负责，播放器自身必须什么都不做 =====

    override fun seekToNextMediaItem() = Unit

    override fun seekToPreviousMediaItem() = Unit

    override fun seekToNext() = Unit

    override fun seekToPrevious() = Unit
}
