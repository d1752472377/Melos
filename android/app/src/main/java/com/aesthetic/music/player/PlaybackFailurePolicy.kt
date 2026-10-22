package com.aesthetic.music.player

/**
 * 播放失败时的「跳过下一首」策略（纯逻辑，无 Android 依赖，便于单测）。
 *
 * ## 为什么要单独抽出来
 * 这个规则以前散在 `PlayerManager.playCurrent()` 里，而且**只覆盖了「取链失败」一种情况**：
 * 直链拿到了、但音频流 403/404/解码失败（`onPlayerError`）时是直接 `player.stop()`——
 * 用户看到的就是「某些歌没音源，放到那儿就停了，后面的歌也不放了」。
 *
 * 现在的规则只有一条：**能跳就跳**，跳不了才停。跳不了只有两种：
 *  1. 队列里只有一首（没得跳）；
 *  2. 已经连续失败到上限（网络断了 / 音源整体不可用，继续跳只是把队列空刷一遍）。
 *
 * 抽成纯函数的意义：这条规则决定「会不会突然没声」，属于必须被测试钉住的逻辑，
 * 而不是靠人肉在几千行的播放器里推导。
 */
object PlaybackFailurePolicy {

    /** 连续失败到多少次就真正停下（期间每首失败都自动跳下一首） */
    const val MAX_CONSECUTIVE_FAILURES = 5

    /** 失败后跳到下一首前的等待：让用户看清提示，也避免坏歌连续疯狂连跳 */
    const val SKIP_DELAY_MS = 1_200L

    /**
     * 是否应该跳到下一首。
     *
     * @param queueSize 当前队列长度
     * @param consecutiveFailures 已经连续失败了几首（**含本次**）
     */
    fun shouldSkipToNext(queueSize: Int, consecutiveFailures: Int): Boolean =
        queueSize > 1 && consecutiveFailures < MAX_CONSECUTIVE_FAILURES

    /**
     * 跳过时的提示文案。
     *
     * 为什么一定要提示：自动跳歌是"替用户做了决定"，如果静默跳过，
     * 用户只会觉得"这首歌怎么放了一半没了"。带歌名的提示能让他知道是这首的问题。
     */
    fun skipMessage(title: String?, reason: String): String =
        if (!title.isNullOrBlank()) "「$title」$reason，已跳到下一首"
        else "当前歌曲$reason，已跳到下一首"

    /** 彻底停下时的提示文案（连续多首都失败） */
    fun giveUpMessage(reason: String): String =
        "连续多首$reason，已暂停。请检查网络或切换音质后重试"
}
