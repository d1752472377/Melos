package com.aesthetic.music.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 共享圆角与间距 token。
 *
 * 只用于共享 primitives / 新代码；页面级特殊视觉仍可使用字面量，
 * 不要求把现有硬编码一次性全量替换（避免为了统一造成视觉回归）。
 */
object AppRadius {
    val xs = 8.dp
    val sm = 10.dp
    val md = 12.dp
    val lg = 14.dp
    val xl = 16.dp
    val card = 20.dp
    val hero = 24.dp
    val pill = 9999.dp
}

object AppSpacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val md = 10.dp
    val lg = 12.dp
    val xl = 14.dp
    val xxl = 16.dp
    val xxxl = 18.dp
    val page = 20.dp
    val section = 24.dp
}
