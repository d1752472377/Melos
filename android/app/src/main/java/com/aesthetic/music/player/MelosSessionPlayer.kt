package com.aesthetic.music.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * 交给 [androidx.media3.session.MediaSession] 的播放器包装：既向系统声明"队列里还有下一首/上一首"，
 * 也**自己执行**这次切歌。
 *
 * ## 为什么需要它
 * 本 App 的播放模型是**一次只往 ExoPlayer 里 set 一首歌**（每首歌的直链要先解析），
 * 真正的播放队列存在 [PlayerManager] 的 `state.queue` 里。而系统（通知栏媒体控件、锁屏、
 * HyperOS 超级岛、蓝牙、车机）判断"下一首能不能按"依据的是 **ExoPlayer 的 timeline**。
 * media3 1.4.1 的 `Util.getAvailableCommands()` 里写得很直白：
 *
 * ```
 * .addIf(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNextMediaItem && !isPlayingAd)
 * ```
 *
 * 单曲 timeline 下 `hasNextMediaItem()` 恒为 false，于是 `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM`
 * 根本不可用 → `MediaController.seekToNextMediaItem()` 在开头就 return，命令连会话都到不了，
 * 表现就是"有上一首、没有下一首"。
 *
 * ## 为什么切歌放在这里执行（而不是回调里）
 * 看 media3 1.4.1 的实现（`MediaSessionStub`/`MediaSessionLegacyStub`）：
 *
 * ```java
 * int resultCode = sessionImpl.onPlayerCommandRequestOnHandler(controller, command);
 * if (resultCode != RESULT_SUCCESS) return;   // 只有"非成功"才拒绝
 * task.run(controller);                        // 成功 → 命令继续下发给 player
 * ```
 *
 * 也就是说回调返回 SUCCESS 只是"放行"，player 上的方法**随后一定会被调用**。
 * 因此在回调里执行切歌、再在 player 上吞掉命令，是一条依赖 deprecated 回调的实现路径：
 * 回调一旦不触发（版本差异/Rom 差异）就完全失效。现在改成**在 player 层执行**——
 * 命令到达这里就切歌，一次且只有一次（回调不再做任何事，避免切两首）。
 *
 * 队列只有一首时不声明命令、也不执行：系统自己会把按钮置灰，而不是点了重播同一首。
 */
@Suppress("DEPRECATION") // 覆写 Player 的 deprecated seekToNext/seekToPrevious
class MelosSessionPlayer(
    player: Player,
    private val hasQueueNavigation: () -> Boolean,
    private val isLoading: () -> Boolean,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) : ForwardingPlayer(player) {

    /**
     * 当处于切歌/直链解析中（isLoading = true）时，对外伪装为 STATE_BUFFERING，
     * 防止 Media3 在单曲播放结束（STATE_ENDED）与下一首就绪之间的间隙误判为播放终止，
     * 进而导致后台前台服务被降级/关闭、网络请求被系统防火墙截断。
     */
    override fun getPlaybackState(): Int {
        if (isLoading()) return Player.STATE_BUFFERING
        return super.getPlaybackState()
    }

    override fun getPlayWhenReady(): Boolean {
        if (isLoading()) return true
        return super.getPlayWhenReady()
    }

    /** 对系统声明可用的播放命令（媒体控件的按钮可用性就是按这个算的） */
    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (!hasQueueNavigation()) return base
        return Player.Commands.Builder()
            .addAll(base)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            // 老式控件（部分 ROM / 蓝牙 AVRCP）走的是不带 MEDIA_ITEM 的两个命令，一并声明
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()
    }

    /** 系统控件据此决定"下一首/上一首"是否可点（以及 PlaybackStateCompat 的 skip 位） */
    override fun hasNextMediaItem(): Boolean = hasQueueNavigation() || super.hasNextMediaItem()

    override fun hasPreviousMediaItem(): Boolean = hasQueueNavigation() || super.hasPreviousMediaItem()

    // ===== 真正的切歌：交给 PlayerManager 的队列逻辑（顺序/循环/随机都在那边） =====
    //
    // 四个入口都要覆写：media3 的会话分发（MediaSessionStub / MediaSessionLegacyStub）
    // 会按控制器不同分别调用 seekToNext() 或 seekToNextMediaItem()，
    // 只覆写其中一个就会出现"某些控件能切、某些不能切"。
    // seekTo*Window 同理（老式播放列表语义，部分车机/遥控会用）。

    override fun seekToNextMediaItem() = switchToNext()

    override fun seekToPreviousMediaItem() = switchToPrevious()

    override fun seekToNext() = switchToNext()

    override fun seekToPrevious() = switchToPrevious()

    override fun seekToNextWindow() = switchToNext()

    override fun seekToPreviousWindow() = switchToPrevious()

    private fun switchToNext() {
        if (!hasQueueNavigation()) return
        onNext()
    }

    private fun switchToPrevious() {
        if (!hasQueueNavigation()) return
        onPrevious()
    }
}
