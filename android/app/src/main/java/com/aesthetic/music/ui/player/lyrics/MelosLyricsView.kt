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
 * 核心设计：
 * 1. 【正常播放模式】：当前正在唱的歌词行永远锁定在屏幕【垂直正正中央】（50% 视口高度），
 *    绝不会偏到底部或顶部；伴随播放平滑居中滑行动画；
 * 2. 【真正主流的翻阅模式】：仅在用户手指真实在屏幕上拖拽滑动浏览时，才浮现正中央定位时间线与「▶」按钮；
 *    松手停顿 2.5 秒或歌曲切句时立即平滑回正到正在唱的中心行；
 * 3. 【无手势冲突】：原生 LazyColumn 100% 顺畅跟手，单指轻轻一划即可上下流畅翻阅；
 * 4. 【点击与长按】：翻阅中点击任意行或点击「▶」直接精准跳转该句播放；普通状态下轻点切换封面；长按呼出技术规格与歌词面板。
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

    // 监听用户真实的拖拽手势
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var isUserBrowsing by remember { mutableStateOf(false) }
    var userBrowsingUntil by remember { mutableLongStateOf(0L) }

    // 当前实际播放时间高亮下标
    var activeIndex by remember { mutableIntStateOf(0) }

    // 实时高频捕获当前播放位置对应的歌词下标
    LaunchedEffect(lyrics) {
        while (isActive) {
            val pos = currentPositionMs()
            val idx = currentLyricIndex(lyrics, pos).coerceAtLeast(0)
            if (idx != activeIndex && idx in lyrics.indices) {
                activeIndex = idx
                // 如果切到了下一句且用户当前没有按着屏幕拖动，立刻退出浏览模式让歌词居中跟进
                if (!isDragged) {
                    isUserBrowsing = false
                    userBrowsingUntil = 0L
                }
            }
            delay(80)
        }
    }

    // 仅在真实物理拖拽发生时进入浏览模式
    LaunchedEffect(isDragged) {
        if (isDragged) {
            isUserBrowsing = true
            userBrowsingUntil = Long.MAX_VALUE
        } else if (isUserBrowsing) {
            // 松手后给 2.5 秒浏览保护，之后平滑回归居中跟唱
            userBrowsingUntil = System.currentTimeMillis() + 2500L
        }
    }

    // 松手 2.5 秒后平滑回正
    LaunchedEffect(isUserBrowsing, isDragged, userBrowsingUntil) {
        if (isUserBrowsing && !isDragged && userBrowsingUntil != Long.MAX_VALUE) {
            while (isActive && isUserBrowsing && !isDragged) {
                val remaining = userBrowsingUntil - System.currentTimeMillis()
                if (remaining <= 0L) {
                    isUserBrowsing = false
                    break
                }
                delay(150)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        // 关键几何计算：取视口正正中心 (50% 高度)
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val halfHeightDp = maxHeight / 2
        val targetYPx = viewportHeightPx * 0.50f
        val targetYDp = with(density) { targetYPx.toDp() }

        // 计算当前正穿过屏幕 50% 正中央指示线的歌词行下标
        val centerIndex by remember {
            derivedStateOf {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@derivedStateOf activeIndex
                // 优先拾取正跨过垂直中线的 Item
                visibleItems.firstOrNull { item ->
                    targetYPx >= item.offset && targetYPx < (item.offset + item.size)
                }?.index ?: visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    kotlin.math.abs(itemCenter - targetYPx)
                }?.index ?: activeIndex
            }
        }

        val centerLine = lyrics.getOrNull(centerIndex)

        // 首次加载或切歌时，立即定位到当前进度所在行并严格居中
        LaunchedEffect(lyrics) {
            isUserBrowsing = false
            userBrowsingUntil = 0L
            val initialPos = currentPositionMs()
            val initialIdx = currentLyricIndex(lyrics, initialPos).coerceAtLeast(0)
            activeIndex = initialIdx
            if (initialIdx in lyrics.indices) {
                listState.scrollToItem(initialIdx, scrollOffset = 0)
            }
        }

        // 自动平滑跟唱：只要不在手动拖拽浏览，正在唱的行必定通过官方 animateScrollToItem 稳稳居中于屏幕正中央！
        LaunchedEffect(activeIndex, isUserBrowsing) {
            if (!isUserBrowsing && activeIndex in lyrics.indices) {
                listState.animateScrollToItem(activeIndex, scrollOffset = 0)
            }
        }

        // 歌词主列表：通过上下对半留白 (halfHeightDp - 26.dp)，确保任一行 (包括第 1 行与末行) 在 scrollOffset=0 时均正居中！
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = (halfHeightDp - 26.dp).coerceAtLeast(0.dp), bottom = halfHeightDp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(
                items = lyrics,
                key = { index, line -> "$index:${line.timeMs}" },
            ) { index, line ->
                val isFocused = index == activeIndex
                val isBrowsingTarget = isUserBrowsing && index == centerIndex
                val isHighlighted = if (isUserBrowsing) isBrowsingTarget else isFocused
                val distance = kotlin.math.abs(index - (if (isUserBrowsing) centerIndex else activeIndex))

                // 字体平滑呼吸淡入淡出（当前居中句最清晰亮丽）
                val lineAlpha by animateFloatAsState(
                    targetValue = when {
                        isHighlighted -> 1f
                        distance == 1 -> 0.62f
                        distance == 2 -> 0.40f
                        distance == 3 -> 0.25f
                        else -> 0.15f
                    },
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    label = "alpha_$index"
                )

                val lineScale by animateFloatAsState(
                    targetValue = if (isHighlighted) 1.02f else 0.98f,
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
                                    // 翻阅时点击单行，直接 Seek 并居中起播
                                    isUserBrowsing = false
                                    userBrowsingUntil = 0L
                                    onSeek(line.timeMs)
                                    scope.launch {
                                        listState.animateScrollToItem(index, scrollOffset = 0)
                                    }
                                } else {
                                    // 普通播放时轻点，切换封面/歌词
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
                        color = if (isHighlighted) (if (isFocused) Color.White else AmberLight) else Color.White,
                        fontSize = if (isHighlighted) fontSizeSp.sp else (fontSizeSp - 3).coerceAtLeast(15).sp,
                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                        lineHeight = if (isHighlighted) (fontSizeSp * 1.35f).sp else (fontSizeSp * 1.25f).sp,
                        textAlign = TextAlign.Start,
                    )

                    if (showTranslation && !line.translated.isNullOrBlank() && line.translated != line.text) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = line.translated,
                            color = if (isHighlighted) AmberLight.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.35f),
                            fontSize = if (isHighlighted) (fontSizeSp - 6).coerceAtLeast(13).sp else (fontSizeSp - 7).coerceAtLeast(12).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = if (isHighlighted) (fontSizeSp * 0.95f).sp else (fontSizeSp * 0.85f).sp,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }

        // 主流翻阅模式：仅在用户拖拽翻阅时显现的中轴（50%）时间指示标线与播放按键
        AnimatedVisibility(
            visible = isUserBrowsing && centerLine != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = (targetYDp - 16.dp).coerceAtLeast(0.dp))
        ) {
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

                // 右侧小圆形「▶」按键（点击即从此句起播并平滑居中）
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AmberLight.copy(alpha = 0.90f))
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
                                        listState.animateScrollToItem(centerIndex, scrollOffset = 0)
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

        // 右下角浮动「回到播放」胶囊（仅在翻阅偏离正在播放的句子时出现）
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
                                listState.animateScrollToItem(activeIndex, scrollOffset = 0)
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

/** 格式化毫秒为 mm:ss */
private fun formatLyricTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
