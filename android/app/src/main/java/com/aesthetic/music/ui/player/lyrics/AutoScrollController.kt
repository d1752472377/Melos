package com.aesthetic.music.ui.player.lyrics

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
 *  - isUserControlling == true  → 用户正在拖动 / fling，或刚松手处于冷却期
 *  - autoScrollTo              → 冷却/拖动时静默跳过，绝不抢用户手势
 *  - forceScrollTo             → 用户主动 seek 时调用，忽略冷却
 *
 * isUserControlling / isAutoScrolling 都是 Compose State：冷却到期时 UI 会
 * 自动重组（指示线消失、高亮回到当前句），不再依赖 activeIndex 碰巧变化。
 */
class AutoScrollController internal constructor(
    internal val listState: LazyListState,
    private val scope: CoroutineScope,   // ← 必须来自 rememberCoroutineScope()
    private val cooldownMs: Long,
) {
    private var autoScrollJob: Job? = null
    private var cooldownJob: Job? = null

    // 每次发起滚动递增；旧 Job 在 finally 里发现 token 已变就不再复位标志，
    // 避免"旧 Job 的 finally 把新 Job 的 isAutoScrolling 清掉"的竞争。
    private var scrollToken = 0

    var isAutoScrolling by mutableStateOf(false)
        private set

    var isUserControlling by mutableStateOf(false)
        private set

    /**
     * 冷却自然结束的计数，供 UI 补做一次在冷却期被跳过的跟随。
     * 只有冷却真正到期才递增；[releaseUserControl]（[forceScrollTo] 内部调用）
     * 不递增，否则强制滚动会被"跟回当前句"的滚动立刻覆盖。
     */
    var followResumeTick by mutableIntStateOf(0)
        private set

    /** 用户开始拖动（或 fling 开始接手）时调用 */
    fun onUserScrollStart() {
        cooldownJob?.cancel()
        cooldownJob = null
        autoScrollJob?.cancel()
        isUserControlling = true
    }

    /** 拖动与 fling 都结束后调用：冷却从这里开始计时，而不是从手指按下时 */
    fun onUserScrollEnd() {
        cooldownJob?.cancel()
        cooldownJob = scope.launch {
            delay(cooldownMs)
            isUserControlling = false
            followResumeTick++
        }
    }

    /** 主动结束用户控制，立即恢复自动跟随（如点击"回到播放"） */
    fun releaseUserControl() {
        cooldownJob?.cancel()
        cooldownJob = null
        isUserControlling = false
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
        val token = ++scrollToken
        autoScrollJob?.cancel()
        // 同步置位，避免"滚动已开始但标志还没置 true"的窗口被误判成用户滚动
        isAutoScrolling = true
        autoScrollJob = scope.launch {
            try {
                if (animate) {
                    listState.animateScrollToItem(index, offset)
                } else {
                    listState.scrollToItem(index, offset)
                }
            } finally {
                if (token == scrollToken) isAutoScrolling = false
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
 * 自动监听 listState 的"用户正在滚动"信号：
 *  - 拖动开始或 fling 期间 → onUserScrollStart()（立刻取消自动跟随）
 *  - 拖动 + fling 全部结束 → onUserScrollEnd()，冷却 cooldownMs 后恢复跟随
 *
 * 自动滚动本身也会让 isScrollInProgress 变 true，所以用 isAutoScrolling 排除；
 * 否则自动滚动会被当成用户操作，反过来把自动跟随打断。
 */
@Composable
fun AutoScrollController.BindUserInteraction() {
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(this) {
        snapshotFlow { isDragged || (listState.isScrollInProgress && !isAutoScrolling) }
            .distinctUntilChanged()
            .drop(1) // 初始值不是用户操作，跳过
            .collect { userActive ->
                if (userActive) onUserScrollStart() else onUserScrollEnd()
            }
    }
}

/**
 * 当前正穿过「视口指示线」的 item index。
 *
 * item.offset 的坐标系以内容起点为 0，而 contentPadding 只在绘制时叠加
 * （见 LazyListMeasuredItem.place 里的 visualOffset）。所以视口顶端对应
 * viewportStartOffset，视口可见区间的长度是 viewportEndOffset - viewportStartOffset。
 * 这里先按比例算出指示线在屏幕上的位置，再换算回 item.offset 的坐标系，
 * 不要在调用方直接把「屏幕高度 × 比例」当成 offset 比较。
 */
fun LazyListLayoutInfo.centerItemIndex(
    fallback: Int = 0,
    anchorRatio: Float = 0f,
): Int {
    val visible = visibleItemsInfo
    if (visible.isEmpty()) return fallback
    val viewportSpan = (viewportEndOffset - viewportStartOffset).coerceAtLeast(1)
    // +1px：指示线的 dp→px 取整和 contentPadding 的取整各算一次，可能差 1px；
    // 偏置 1px 让正好落在两行交界处的指示线稳定归给下面那行，不会在行之间跳。
    val anchorY = viewportStartOffset + (viewportSpan * anchorRatio).roundToInt() + 1
    return visible.firstOrNull { anchorY in it.offset until (it.offset + it.size) }?.index
        ?: visible.minByOrNull { abs((it.offset + it.size / 2) - anchorY) }?.index
        ?: fallback
}
