package com.aesthetic.music.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

@Suppress("DEPRECATION")
class MelosSessionPlayer(
    player: Player,
    private val hasQueueNavigation: () -> Boolean,
    private val isLoading: () -> Boolean,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) : ForwardingPlayer(player) {

    override fun getPlaybackState(): Int {
        if (isLoading()) return Player.STATE_BUFFERING
        return super.getPlaybackState()
    }

    override fun getPlayWhenReady(): Boolean {
        if (isLoading()) return true
        return super.getPlayWhenReady()
    }

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (!hasQueueNavigation()) return base
        return Player.Commands.Builder()
            .addAll(base)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()
    }

    override fun hasNextMediaItem(): Boolean = hasQueueNavigation() || super.hasNextMediaItem()

    override fun hasPreviousMediaItem(): Boolean = hasQueueNavigation() || super.hasPreviousMediaItem()

    // 只覆写其中一个就会出现"某些控件能切、某些不能切"。

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
