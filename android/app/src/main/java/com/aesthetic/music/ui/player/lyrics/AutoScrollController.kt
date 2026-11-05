package com.aesthetic.music.ui.player.lyrics

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.abs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 自动滚动控制器：统一管理"用户输入 vs 自动跟随"的竞争。
 *
 * 所有 scrollToItem / animateScrollToItem 必须走这里，禁止组件内直接调用。
 *
 * 重要：[scope] 必须是 Compose 提供的协程域（rememberCoroutineScope），
 * 因为 animateScrollToItem 需要 MonotonicFrameClock，只有 Compose scope 才携带它。
 * 自建 CoroutineScope(Dispatchers.Main) 会导致 IllegalStateException crash。
 *
 * 状态语义：
 *  - isUserControlling == true  → 用户正在拖动，或刚松手处于冷却期
 *  - autoScrollTo              → 冷却/拖动时静默跳过，绝不抢用户手势
 *  - forceScrollTo             → 用户主动 seek 时调用，忽略冷却
 */
class AutoScrollController internal constructor(
    internal val listState: LazyListState,
    private val scope: CoroutineScope,   // ← 必须来自 rememberCoroutineScope()
    private val cooldownMs: Long,
) {
    private var autoScrollJob: Job? = null
    private var cooldownJob: Job? = null

    @Volatile private var cooldownUntil: Long = 0L

    // 标记当前是否是自动滚动（非用户触发）：自动滚动期间不算 isUserControlling，
    // 避免高亮在动画帧中切换到 centerIndex 而产生"跳两行再闪回"的视觉抖动。
    @Volatile private var isAutoScrollActive: Boolean = false

    /**
     * 用户是否正在掌控滚动。
     * 注意：自动滚动时 isScrollInProgress=true，但不属于用户控制，需排除。
     */
    val isUserControlling: Boolean
        get() = (listState.isScrollInProgress && !isAutoScrollActive) ||
                System.currentTimeMillis() < cooldownUntil

    /** 由交互信号驱动：用户开始拖动时调用 */
    fun onUserInteraction() {
        autoScrollJob?.cancel()
        cooldownUntil = System.currentTimeMillis() + cooldownMs
        cooldownJob?.cancel()
        cooldownJob = scope.launch {
            while (isActive) {
                val remain = cooldownUntil - System.currentTimeMillis()
                if (remain <= 0L) break
                delay(remain.coerceAtMost(100L))
            }
        }
    }

    /** 主动结束用户控制，立即恢复自动跟随（如点击"回到播放"） */
    fun releaseUserControl() {
        cooldownUntil = 0L
        cooldownJob?.cancel()
    }

    /** 自动滚动：用户掌控期间静默跳过 */
    fun autoScrollTo(index: Int, offset: Int = 0, animate: Boolean = true) {
        if (isUserControlling) return
        launchScroll(index, offset, animate)
    }

    /** 强制滚动：用户主动 seek / 点"回到播放"时使用 */
    fun forceScrollTo(index: Int, offset: Int = 0, animate: Boolean = true) {
        releaseUserControl()
        launchScroll(index, offset, animate)
    }

    private fun launchScroll(index: Int, offset: Int, animate: Boolean) {
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            isAutoScrollActive = true
            try {
                if (animate) {
                    listState.animateScrollToItem(index, offset)
                } else {
                    listState.scrollToItem(index, offset)
                }
            } finally {
                isAutoScrollActive = false
            }
        }
    }
}

@Composable
fun rememberAutoScrollController(
    listState: LazyListState,
    cooldownMs: Long = 2500L,
): AutoScrollController {
    // rememberCoroutineScope() 提供的 scope 携带 MonotonicFrameClock，
    // animateScrollToItem 的帧动画必须依赖它，不能用自建的裸 scope。
    val scope = rememberCoroutineScope()
    return remember(listState, cooldownMs) {
        AutoScrollController(listState, scope, cooldownMs)
    }
}

/**
 * 自动监听 listState 的拖拽信号：
 *  - 手指按下拖动 → onUserInteraction()，冷却计时开始
 *  - 手指松开     → 冷却持续 cooldownMs 后自动恢复
 *
 * 注意：冷却倒计时逻辑已在 Controller 内部（onUserInteraction）启动，
 * 这里只负责把拖拽信号转发进去，不直接访问私有字段。
 */
@Composable
fun AutoScrollController.BindUserInteraction() {
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) {
            onUserInteraction()
        }
    }
}
/**
 * 当前正穿过「视口指示线」的 item index。
 *
 * 必须用 viewportStartOffset/EndOffset 判断，不能用半高估算：
 * contentPadding 会污染 item.offset 坐标系。
 */
fun LazyListLayoutInfo.centerItemIndex(
    fallback: Int = 0,
    anchorRatio: Float = 0f,
): Int {
    val visible = visibleItemsInfo
    if (visible.isEmpty()) return fallback
    val anchorY = (viewportSize.height * anchorRatio).toInt()
    return visible.firstOrNull { anchorY in it.offset until (it.offset + it.size) }?.index
        ?: visible.minByOrNull { abs((it.offset + it.size / 2) - anchorY) }?.index
        ?: fallback
}
