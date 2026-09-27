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
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
 * 核心设计原则：
 * 1. 彻底移除引起画布打孔黑块的 BlendMode.DstIn，完美透出背景封面与磨砂氛围；
 * 2. 区分用户物理拖拽（collectIsDraggedAsState）与程序自动滚唱，杜绝浏览模式死锁与永远显示播放按钮的 bug；
 * 3. 采用视口 38% 黄金分割线对齐算法（animateScrollBy 相对差值），保证歌词精准居中聚焦；
 * 4. 浏览模式下的时间线指示器不拦截手势，用户在屏幕任意位置滑动均能 100% 极速跟手；
 * 5. 停顿 3.5 秒或点击「回到播放」/「▶」/单行歌词即刻精准复位跟唱。
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

    // 关键：仅监听用户真实的物理触摸拖拽行为
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
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

    // 用户拖动检测：仅由物理手势触发浏览模式
    LaunchedEffect(isDragged) {
        if (isDragged) {
            isUserBrowsing = true
            userBrowsingUntil = Long.MAX_VALUE // 拖拽中不倒计时
        } else if (isUserBrowsing) {
            // 手指松开，启动 3.5 秒保护期
            userBrowsingUntil = System.currentTimeMillis() + 3500L
        }
    }

    // 倒计时结束且列表不再惯性滑动时，退出浏览模式
    LaunchedEffect(isUserBrowsing, userBrowsingUntil) {
        if (isUserBrowsing && userBrowsingUntil != Long.MAX_VALUE) {
            while (isActive && isUserBrowsing) {
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
        val targetYPx = viewportHeightPx * 0.38f
        val targetYDp = with(density) { targetYPx.toDp() }
        val bottomPaddingDp = with(density) { (viewportHeightPx * 0.55f).toDp() }

        // 计算当前最接近黄金分割视准线的歌词下标（供时间线指示器使用）
        val centerIndex by remember {
            derivedStateOf {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@derivedStateOf activeIndex
                visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    kotlin.math.abs(itemCenter - targetYPx)
                }?.index ?: activeIndex
            }
        }

        val centerLine = lyrics.getOrNull(centerIndex)

        // 首次加载或切歌时，立即定位到当前进度所在行并居中
        LaunchedEffect(lyrics) {
            isUserBrowsing = false
            userBrowsingUntil = 0L
            val initialPos = currentPositionMs()
            val initialIdx = currentLyricIndex(lyrics, initialPos).coerceAtLeast(0)
            activeIndex = initialIdx
            if (initialIdx in lyrics.indices) {
                listState.scrollToItem(initialIdx)
                val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == initialIdx }
                if (targetItem != null) {
                    val delta = (targetItem.offset + targetItem.size / 2 - targetYPx)
                    if (kotlin.math.abs(delta) > 4f) {
                        listState.animateScrollBy(delta)
                    }
                }
            }
        }

        // 自动平滑跟唱：当不在手动浏览且不在拖拽时，顺畅跟唱滚动
        LaunchedEffect(activeIndex, isUserBrowsing, isDragged) {
            if (!isUserBrowsing && !isDragged && activeIndex in lyrics.indices) {
                listState.animateScrollToLyricIndex(activeIndex, targetYPx)
            }
        }

        // 歌词主列表（完全透明底色，不使用任何破坏 Window 画布的 BlendMode）
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = targetYDp, bottom = bottomPaddingDp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(
                items = lyrics,
                key = { index, line -> "$index:${line.timeMs}" },
            ) { index, line ->
                val isFocused = index == activeIndex
                val distance = kotlin.math.abs(index - activeIndex)

                // 字体平滑呼吸淡入淡出（近距清晰，远距柔和）
                val lineAlpha by animateFloatAsState(
                    targetValue = when {
                        isFocused -> 1f
                        distance == 1 -> 0.65f
                        distance == 2 -> 0.45f
                        distance == 3 -> 0.30f
                        else -> 0.18f
                    },
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    label = "alpha_$index"
                )

                val lineScale by animateFloatAsState(
                    targetValue = if (isFocused) 1.02f else 0.98f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
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
                                isUserBrowsing = false
                                userBrowsingUntil = 0L
                                onSeek(line.timeMs)
                                scope.launch {
                                    listState.animateScrollToLyricIndex(index, targetYPx)
                                }
                            },
                            onLongClick = onLineLongClick
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = line.text,
                        color = Color.White,
                        fontSize = if (isFocused) fontSizeSp.sp else (fontSizeSp - 3).coerceAtLeast(15).sp,
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                        lineHeight = if (isFocused) (fontSizeSp * 1.35f).sp else (fontSizeSp * 1.25f).sp,
                        textAlign = TextAlign.Start,
                    )

                    if (showTranslation && !line.translated.isNullOrBlank() && line.translated != line.text) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = line.translated,
                            color = if (isFocused) AmberLight.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.35f),
                            fontSize = if (isFocused) (fontSizeSp - 6).coerceAtLeast(13).sp else (fontSizeSp - 7).coerceAtLeast(12).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = if (isFocused) (fontSizeSp * 0.95f).sp else (fontSizeSp * 0.85f).sp,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }

        // 主流翻阅模式：中轴时间指示标线与快速播放按钮（仅在用户手动翻阅时出现）
        AnimatedVisibility(
            visible = isUserBrowsing && centerLine != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = (targetYDp - 16.dp).coerceAtLeast(0.dp))
        ) {
            // 注意：外层 Row 与虚线不添加任何 pointerInput，完全不阻挡手指上下滑动
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧时间戳胶囊
                val timeStr = formatLyricTime(centerLine?.timeMs ?: 0L)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = timeStr,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 中间半透明引导线
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .padding(horizontal = 8.dp)
                        .background(Color.White.copy(alpha = 0.20f))
                )

                // 右侧小圆形「▶」按键（仅该 32dp 按钮消费点击，其余区域全部透传）
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
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
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 右下角浮动「回到播放」胶囊（仅在用户翻阅偏离当前播放位置时显现）
        AnimatedVisibility(
            visible = isUserBrowsing && kotlin.math.abs(centerIndex - activeIndex) > 1,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
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
 * 相对实测像素差值滚动算法：将指定 index 的歌词中心点平滑滚动对齐至 targetYPx。
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
