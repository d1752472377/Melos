package com.aesthetic.music.ui.player.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.player.LyricLine
import com.aesthetic.music.player.currentLyricIndex
import com.aesthetic.music.ui.theme.AmberLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 主流现代音乐 App 风格歌词组件（MelosLyricsView）。
 *
 * 核心设计：
 * 1. 【触控极速响应】：在 PointerEventPass.Initial 层监听触碰，手指触碰瞬间立刻挂起一切自动滚动争抢，
 *    用户一次性随心上下滑动，绝对不会发生“得滑好几次才能翻”的卡手问题；
 * 2. 【正中心居中对齐】：视口 50% 正中央垂直居中（targetYPx = viewportHeight * 0.5f），当前播放歌词行居中高亮；
 * 3. 【指示线与歌词 100% 精准对应】：时间线严格落在视口正中心（50% 高度），动态物理拾取当前正跨过中线的歌词行，
 *    并在翻阅时将该候选句显著高亮，时间戳、标线与点击「▶」跳转起播的歌词绝对完全一致；
 * 4. 【透明背景】：无任何 BlendMode 破坏，完全透出播放页毛玻璃封面质感；
 * 5. 【平滑回弹】：翻阅停止 4 秒无操作或点击「回到播放」，顺畅滑回正在唱的歌词句。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MelosLyricsView(
    lyrics: List<LyricLine>,
    currentPositionMs: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 22,
    showTranslation: Boolean = true,
    onToggleLyricMode: (() -> Unit)? = null,
    onLineLongClick: (() -> Unit)? = null,
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onToggleLyricMode?.invoke() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无歌词",
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 用户触碰与手动翻阅保护状态
    var isTouching by remember { mutableStateOf(false) }
    var isUserBrowsing by remember { mutableStateOf(false) }
    var userBrowsingUntil by remember { mutableLongStateOf(0L) }

    // 当前实际播放时间高亮下标
    var activeIndex by remember { mutableIntStateOf(0) }

    // 实时轮询当前播放位置对应的歌词下标
    LaunchedEffect(lyrics) {
        while (isActive) {
            val pos = currentPositionMs()
            val idx = currentLyricIndex(lyrics, pos).coerceAtLeast(0)
            if (idx != activeIndex && idx in lyrics.indices) {
                activeIndex = idx
            }
            delay(100)
        }
    }

    // 翻阅静止 4 秒后平滑回正
    LaunchedEffect(isUserBrowsing, isTouching, userBrowsingUntil) {
        if (isUserBrowsing && !isTouching && userBrowsingUntil != Long.MAX_VALUE) {
            while (isActive && isUserBrowsing && !isTouching) {
                val remaining = userBrowsingUntil - System.currentTimeMillis()
                if (remaining <= 0L) {
                    if (!listState.isScrollInProgress) {
                        isUserBrowsing = false
                        break
                    }
                }
                delay(200)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        // 关键需求：垂直正中居中 (50%)
        val targetYPx = viewportHeightPx * 0.50f
        val targetYDp = with(density) { targetYPx.toDp() }

        // 计算当前正穿过屏幕中央指示线的歌词行下标（物理范围优先，再取距离最近，100% 精准对应）
        val centerIndex by remember {
            derivedStateOf {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@derivedStateOf activeIndex
                // 找到垂直像素区间直接覆盖 targetYPx 的那一行
                visibleItems.firstOrNull { item ->
                    targetYPx >= item.offset && targetYPx < (item.offset + item.size)
                }?.index ?: visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    kotlin.math.abs(itemCenter - targetYPx)
                }?.index ?: activeIndex
            }
        }

        val centerLine = lyrics.getOrNull(centerIndex)

        // 首次加载或切歌时，立即定位到当前进度所在行并居中
        LaunchedEffect(lyrics) {
            isUserBrowsing = false
            isTouching = false
            userBrowsingUntil = 0L
            val initialPos = currentPositionMs()
            val initialIdx = currentLyricIndex(lyrics, initialPos).coerceAtLeast(0)
            activeIndex = initialIdx
            if (initialIdx in lyrics.indices) {
                listState.scrollToItem(initialIdx)
                val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == initialIdx }
                if (targetItem != null) {
                    val delta = (targetItem.offset + targetItem.size / 2 - targetYPx)
                    if (kotlin.math.abs(delta) > 3f) {
                        listState.animateScrollBy(delta)
                    }
                }
            }
        }

        // 自动平滑跟唱：当不在手动浏览且手指未接触屏幕时，顺畅跟唱居中
        LaunchedEffect(activeIndex, isUserBrowsing, isTouching) {
            if (!isUserBrowsing && !isTouching && activeIndex in lyrics.indices) {
                listState.animateScrollToLyricIndex(activeIndex, targetYPx)
            }
        }

        // 歌词主列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // 在 Initial 阶段优先感知用户手指触碰，瞬间停止代码跟唱拉扯，保证一次性丝滑翻动
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val anyPressed = event.changes.any { it.pressed }
                            if (anyPressed) {
                                isTouching = true
                                isUserBrowsing = true
                                userBrowsingUntil = Long.MAX_VALUE
                            } else if (isTouching) {
                                isTouching = false
                                userBrowsingUntil = System.currentTimeMillis() + 4000L
                            }
                        }
                    }
                },
            contentPadding = PaddingValues(top = targetYDp - 24.dp, bottom = targetYDp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(
                items = lyrics,
                key = { index, line -> "$index:${line.timeMs}" },
            ) { index, line ->
                val isFocused = index == activeIndex
                // 在浏览模式下，正处于中心线所指的候选行特殊高亮，避免看错行
                val isBrowsingTarget = isUserBrowsing && index == centerIndex
                val distance = kotlin.math.abs(index - (if (isUserBrowsing) centerIndex else activeIndex))

                // 字体平滑呼吸淡入淡出（近距清晰，远距柔和）
                val lineAlpha by animateFloatAsState(
                    targetValue = when {
                        isFocused || isBrowsingTarget -> 1f
                        distance == 1 -> 0.62f
                        distance == 2 -> 0.42f
                        distance == 3 -> 0.28f
                        else -> 0.16f
                    },
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    label = "alpha_$index"
                )

                val lineScale by animateFloatAsState(
                    targetValue = if (isFocused || isBrowsingTarget) 1.02f else 0.98f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    label = "scale_$index"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = lineScale
                            scaleY = lineScale
                            alpha = lineAlpha
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (isUserBrowsing) {
                                    // 翻阅状态下点击单行，直接跳转至该行起播并恢复居中跟唱
                                    isUserBrowsing = false
                                    userBrowsingUntil = 0L
                                    onSeek(line.timeMs)
                                    scope.launch {
                                        listState.animateScrollToLyricIndex(index, targetYPx)
                                    }
                                } else {
                                    // 普通播放状态下轻点，按主流习惯切换封面/歌词
                                    onToggleLyricMode?.invoke()
                                }
                            },
                            onLongClick = onLineLongClick
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = line.text,
                        color = if (isBrowsingTarget && !isFocused) AmberLight.copy(alpha = 0.95f) else Color.White,
                        fontSize = if (isFocused || isBrowsingTarget) fontSizeSp.sp else (fontSizeSp - 3).coerceAtLeast(15).sp,
                        fontWeight = if (isFocused || isBrowsingTarget) FontWeight.Bold else FontWeight.SemiBold,
                        lineHeight = if (isFocused || isBrowsingTarget) (fontSizeSp * 1.35f).sp else (fontSizeSp * 1.25f).sp,
                        textAlign = TextAlign.Start,
                    )

                    if (showTranslation && !line.translated.isNullOrBlank() && line.translated != line.text) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = line.translated,
                            color = if (isFocused || isBrowsingTarget) AmberLight.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.35f),
                            fontSize = if (isFocused || isBrowsingTarget) (fontSizeSp - 6).coerceAtLeast(13).sp else (fontSizeSp - 7).coerceAtLeast(12).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = if (isFocused || isBrowsingTarget) (fontSizeSp * 0.95f).sp else (fontSizeSp * 0.85f).sp,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }

        // 主流翻阅模式：屏幕正中（50%）时间指示标线与快速播放按钮（仅在用户手动翻阅时出现）
        AnimatedVisibility(
            visible = isUserBrowsing && centerLine != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = (targetYDp - 16.dp).coerceAtLeast(0.dp))
        ) {
            // 注意：外层 Row 与虚线不添加任何 clickable/pointerInput，手势 100% 直通底层 LazyColumn
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧时间戳胶囊（严格显示当前正中选中的这行歌词的时间）
                val timeStr = formatLyricTime(centerLine?.timeMs ?: 0L)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = timeStr,
                        color = AmberLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 中间半透明引导线
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .padding(horizontal = 8.dp)
                        .background(Color.White.copy(alpha = 0.22f))
                )

                // 右侧小圆形「▶」按键（仅该 32dp 按钮消费点击，点击即精准跳到 centerLine 起播）
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AmberLight.copy(alpha = 0.85f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val target = centerLine
                                if (target != null) {
                                    isUserBrowsing = false
                                    userBrowsingUntil = 0L
                                    onSeek(target.timeMs)
                                    scope.launch {
                                        listState.animateScrollToLyricIndex(centerIndex, targetYPx)
                                    }
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "从此句播放",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 右下角浮动「回到播放」胶囊（仅在用户翻阅偏离当前播放位置时显现）
        AnimatedVisibility(
            visible = isUserBrowsing && kotlin.math.abs(centerIndex - activeIndex) > 1,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            isUserBrowsing = false
                            userBrowsingUntil = 0L
                            scope.launch {
                                listState.animateScrollToLyricIndex(activeIndex, targetYPx)
                            }
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.VerticalAlignBottom,
                        contentDescription = "回到播放",
                        tint = AmberLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "回到播放",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 相对实测像素差值滚动算法：将指定 index 的歌词中心点平滑滚动对齐至 targetYPx（垂直正中央）。
 * 彻底避免 offset 负数或 contentPadding 耦合引发的滚动失效。
 */
private suspend fun LazyListState.animateScrollToLyricIndex(index: Int, targetYPx: Float) {
    if (index !in 0 until layoutInfo.totalItemsCount) return
    val info = layoutInfo
    val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
    if (viewportHeight <= 0) return

    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item != null) {
        val itemCenter = item.offset + item.size / 2
        val delta = (itemCenter - targetYPx)
        if (kotlin.math.abs(delta) > 3f) {
            animateScrollBy(delta)
        }
    } else {
        scrollToItem(index)
        val after = layoutInfo
        val target = after.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val targetCenter = target.offset + target.size / 2
        val delta = (targetCenter - targetYPx)
        if (kotlin.math.abs(delta) > 3f) {
            animateScrollBy(delta)
        }
    }
}

/** 格式化毫秒为 mm:ss */
private fun formatLyricTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
