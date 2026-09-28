package com.aesthetic.music.ui.components

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.abs

/**
 * LazyColumn 中线/居中数学的统一收口。
 *
 * 铁律：任何"视口中线命中检测"必须用 viewportStartOffset/EndOffset，
 * 不能用 halfHeightPx —— contentPadding 会污染 item.offset 坐标系。
 */

/** 视口中线在内容坐标系里的 Y（已含 contentPadding 的影响） */
val LazyListLayoutInfo.viewportCenterY: Int
    get() = (viewportStartOffset + viewportEndOffset) / 2

/** 视口高度（px） */
val LazyListLayoutInfo.viewportHeightPx: Int
    get() = viewportEndOffset - viewportStartOffset

/** 当前正穿过视口中线的 item index */
fun LazyListLayoutInfo.centerItemIndex(
    fallback: Int = 0,
    anchorRatio: Float = 0f,   // 0..1，指示线相对视口的高度比例
): Int {
    val visible = visibleItemsInfo
    if (visible.isEmpty()) return fallback
    val anchorY = (viewportSize.height * anchorRatio).toInt()
    return visible.firstOrNull { anchorY in it.offset until (it.offset + it.size) }?.index
        ?: visible.minByOrNull { abs((it.offset + it.size / 2) - anchorY) }?.index
        ?: fallback
}

/**
 * 让 [index] 行精确居中所需的 scrollOffset。
 * 返回负值，表示把该行向下推（相对其顶部）。
 *
 * @param lineHeightPx 该行实际渲染高度；未知时传 0，会在下一帧被修正
 */
fun LazyListState.centeredScrollOffset(index: Int, lineHeightPx: Int): Int {
    val info = layoutInfo
    val viewportH = info.viewportHeightPx
    // 已滚动位置下，目标行顶部距内容原点约等于 item.offset；
    // 要让其中线对齐视口中线，偏移 = 视口中线 - (itemTop + lineH/2)
    // 由于 scrollToItem 的 scrollOffset 是相对 item 顶部的，
    // 这里只用"视口半高 - 行半高"即可，item.offset 会被框架自动处理。
    // scrollToItem/animateScrollToItem 的 scrollOffset = 距 item 顶部的偏移。
    // 要让 item 中线对齐视口中线：scrollOffset = viewportH/2 - lineH/2（始终 >= 0）。
    return (viewportH / 2 - lineHeightPx / 2).coerceAtLeast(0)
}