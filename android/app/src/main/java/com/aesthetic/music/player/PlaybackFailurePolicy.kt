package com.aesthetic.music.player

object PlaybackFailurePolicy {

    const val MAX_CONSECUTIVE_FAILURES = 5

    const val SKIP_DELAY_MS = 1_200L

    fun shouldSkipToNext(queueSize: Int, consecutiveFailures: Int): Boolean =
        queueSize > 1 && consecutiveFailures < MAX_CONSECUTIVE_FAILURES

    fun skipMessage(title: String?, reason: String): String =
        if (!title.isNullOrBlank()) "「$title」$reason，已跳到下一首"
        else "当前歌曲$reason，已跳到下一首"

    fun giveUpMessage(reason: String): String =
        "连续多首$reason，已暂停。请检查网络或切换音质后重试"
}
