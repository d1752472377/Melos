package com.aesthetic.music.ui.player.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive

/**
 * 高精度平滑播放时间插值器（借鉴瓜子音乐 rememberSmoothTime 实现）
 * 解决底层播放器进度刷新（200~500ms 一次）导致歌词逐字动画跳帧的问题。
 * 利用 Compose 的 withFrameNanos 在 60Hz / 120Hz 刷新率下实现丝滑插值。
 */
@Composable
fun rememberSmoothTime(rawPositionMs: Long, isPlaying: Boolean): State<Long> {
    val smoothTime = remember { mutableLongStateOf(rawPositionMs) }

    LaunchedEffect(isPlaying, rawPositionMs) {
        if (!isPlaying) {
            smoothTime.longValue = rawPositionMs
            return@LaunchedEffect
        }

        var lastFrameNanos = 0L
        var basePosition = rawPositionMs
        var lastRawPosition = rawPositionMs

        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = frameNanos
                }

                // 播放器发生 Seek 或跃变（偏差超过 1000ms 或时间倒流）时立刻重置基准
                if (rawPositionMs != lastRawPosition || kotlin.math.abs(rawPositionMs - basePosition) > 1000L) {
                    lastRawPosition = rawPositionMs
                    basePosition = rawPositionMs
                    lastFrameNanos = frameNanos
                }

                val elapsedMs = (frameNanos - lastFrameNanos) / 1_000_000L
                // 限制单帧最大漂移 1000ms，防止系统挂起恢复后异常冲刺
                val interpolated = basePosition + elapsedMs.coerceIn(0L, 1000L)
                smoothTime.longValue = interpolated.coerceAtMost(rawPositionMs + 1000L)
            }
        }
    }

    return smoothTime
}

/**
 * 逐字演唱字词的物理浮起与微缩放曲线（Word Lift）
 * 正在演唱的字产生一个温和的抛物线起伏 (1.0f -> 1.10f -> 1.0f)
 */
fun calculateWordLiftScale(currentTimeMs: Long, wordStartMs: Long, wordEndMs: Long): Float {
    if (currentTimeMs < wordStartMs || currentTimeMs > wordEndMs) return 1.0f
    val duration = (wordEndMs - wordStartMs).coerceAtLeast(1L)
    val progress = ((currentTimeMs - wordStartMs).toFloat() / duration).coerceIn(0f, 1f)
    // 抛物线跳动
    val bounce = kotlin.math.sin(progress * Math.PI.toFloat())
    return 1.0f + bounce * 0.10f
}

/**
 * 计算单个词内的演唱进度 (0f ~ 1f)
 */
fun calculateWordProgress(currentTimeMs: Long, wordStartMs: Long, wordEndMs: Long): Float {
    if (currentTimeMs <= wordStartMs) return 0f
    if (currentTimeMs >= wordEndMs) return 1f
    val duration = (wordEndMs - wordStartMs).coerceAtLeast(1L)
    return ((currentTimeMs - wordStartMs).toFloat() / duration).coerceIn(0f, 1f)
}
