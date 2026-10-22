package com.aesthetic.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「播放失败 → 跳过下一首」策略单测。
 *
 * 这条规则决定「会不会突然没声」：以前只有取链失败会跳，播放中途出错（直链过期/403/解码失败）
 * 是直接 stop，用户遇到一首没版权的歌整条播放链就断了。这里把规则钉死：
 * **能跳就跳，队列里只有一首或连续失败到上限才停。**
 */
class PlaybackFailurePolicyTest {

    @Test
    fun `队列有多首且失败次数未到上限时跳过`() {
        assertTrue(PlaybackFailurePolicy.shouldSkipToNext(queueSize = 2, consecutiveFailures = 1))
        assertTrue(PlaybackFailurePolicy.shouldSkipToNext(queueSize = 30, consecutiveFailures = 4))
    }

    @Test
    fun `队列只有一首时不跳`() {
        // 没得跳：继续"跳"只会把同一首重播一遍，等于死循环
        assertFalse(PlaybackFailurePolicy.shouldSkipToNext(queueSize = 1, consecutiveFailures = 1))
        assertFalse(PlaybackFailurePolicy.shouldSkipToNext(queueSize = 0, consecutiveFailures = 1))
    }

    @Test
    fun `连续失败到上限就停下`() {
        val max = PlaybackFailurePolicy.MAX_CONSECUTIVE_FAILURES
        assertTrue(PlaybackFailurePolicy.shouldSkipToNext(queueSize = 10, consecutiveFailures = max - 1))
        assertFalse(PlaybackFailurePolicy.shouldSkipToNext(queueSize = 10, consecutiveFailures = max))
        assertFalse(PlaybackFailurePolicy.shouldSkipToNext(queueSize = 10, consecutiveFailures = max + 1))
    }

    @Test
    fun `跳过提示带上歌名与原因`() {
        assertEquals(
            "「夜曲」无法播放，已跳到下一首",
            PlaybackFailurePolicy.skipMessage("夜曲", "无法播放"),
        )
        assertEquals(
            "「夜曲」未取到播放地址，已跳到下一首",
            PlaybackFailurePolicy.skipMessage("夜曲", "未取到播放地址"),
        )
        // 歌名缺失时也要给一句能看懂的话，不能出现「「」无法播放」
        assertEquals(
            "当前歌曲无法播放，已跳到下一首",
            PlaybackFailurePolicy.skipMessage(null, "无法播放"),
        )
        assertEquals(
            "当前歌曲无法播放，已跳到下一首",
            PlaybackFailurePolicy.skipMessage("   ", "无法播放"),
        )
    }

    @Test
    fun `彻底停下时的提示说明原因与下一步`() {
        val msg = PlaybackFailurePolicy.giveUpMessage("无法获取播放地址")
        assertTrue(msg.contains("连续多首"))
        assertTrue(msg.contains("无法获取播放地址"))
        assertTrue(msg.contains("检查网络") || msg.contains("切换音质"))
    }

    @Test
    fun `跳歌前的等待是有限且可感知的`() {
        // 太短会疯狂连跳（用户只看到歌名刷屏），太长会让"跳过"显得卡住
        assertTrue(PlaybackFailurePolicy.SKIP_DELAY_MS in 500L..3_000L)
    }
}
