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

class AutoScrollController internal constructor(
    internal val listState: LazyListState,
    private val scope: CoroutineScope,
    private val cooldownMs: Long,
) {
    private var autoScrollJob: Job? = null
    private var cooldownJob: Job? = null

    private var scrollToken = 0

    var isAutoScrolling by mutableStateOf(false)
        private set

    var isUserControlling by mutableStateOf(false)
        private set

    var followResumeTick by mutableIntStateOf(0)
        private set

    fun onUserScrollStart() {
        cooldownJob?.cancel()
        cooldownJob = null
        autoScrollJob?.cancel()
        isUserControlling = true
    }

    fun onUserScrollEnd() {
        cooldownJob?.cancel()
        cooldownJob = scope.launch {
            delay(cooldownMs)
            isUserControlling = false
            followResumeTick++
        }
    }

    fun releaseUserControl() {
        cooldownJob?.cancel()
        cooldownJob = null
        isUserControlling = false
    }

    fun autoScrollTo(index: Int, offset: Int = 0, animate: Boolean = true) {
        if (isUserControlling) return
        launchScroll(index, offset, animate)
    }

    fun forceScrollTo(index: Int, offset: Int = 0, animate: Boolean = true) {
        releaseUserControl()
        launchScroll(index, offset, animate)
    }

    private fun launchScroll(index: Int, offset: Int, animate: Boolean) {
        val token = ++scrollToken
        autoScrollJob?.cancel()
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
    // animateScrollToItem 的帧动画必须依赖它，不能用自建的裸 scope。
    val scope = rememberCoroutineScope()
    return remember(listState, cooldownMs) {
        AutoScrollController(listState, scope, cooldownMs)
    }
}

@Composable
fun AutoScrollController.BindUserInteraction() {
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(this) {
        snapshotFlow { isDragged || (listState.isScrollInProgress && !isAutoScrolling) }
            .distinctUntilChanged()
            .drop(1)
            .collect { userActive ->
                if (userActive) onUserScrollStart() else onUserScrollEnd()
            }
    }
}

fun LazyListLayoutInfo.centerItemIndex(
    fallback: Int = 0,
    anchorRatio: Float = 0f,
): Int {
    val visible = visibleItemsInfo
    if (visible.isEmpty()) return fallback
    val viewportSpan = (viewportEndOffset - viewportStartOffset).coerceAtLeast(1)
    val anchorY = viewportStartOffset + (viewportSpan * anchorRatio).roundToInt() + 1
    return visible.firstOrNull { anchorY in it.offset until (it.offset + it.size) }?.index
        ?: visible.minByOrNull { abs((it.offset + it.size / 2) - anchorY) }?.index
        ?: fallback
}
