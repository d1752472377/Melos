package com.aesthetic.music.ui.player.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * 特性：
 * 1. 采用原生 LazyColumn 构建，彻底告别外部复杂多层插件，滑动零阻尼、惯性自然、满帧丝滑；
 * 2. 视口黄金分割居中自动平滑滚唱（Auto-Scroll）；
 * 3. 手指翻阅进入浏览模式（User Browsing Mode），屏幕中央显现时间定位标线与快速播放按钮（▶）；
 * 4. 停顿 3.5 秒或点击右下角浮动胶囊平滑自动回正；
 * 5. 点击单行即刻 Seek 跳转起播，长按单行呼出技术规格与歌词详情面板。
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
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleLyricMode?.invoke() })
                },
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

    // 当前实际播放时间高亮下标
    var activeIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(lyrics) {
        while (isActive) {
            val pos = currentPositionMs()
            val idx = currentLyricIndex(lyrics, pos)
            if (idx != activeIndex && idx in lyrics.indices) {
                activeIndex = idx
            }
            delay(150)
        }
    }

    // 用户手动翻阅保护：滑动时为 true，手指停稳后 3.5 秒恢复 false
    var isUserBrowsing by remember { mutableStateOf(false) }
    var lastScrollTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                isUserBrowsing = true
                lastScrollTime = System.currentTimeMillis()
            }
        }
    }

    // 翻阅倒计时保护与平滑回弹
    LaunchedEffect(isUserBrowsing, lastScrollTime) {
        if (isUserBrowsing) {
            while (isActive) {
                delay(300)
                if (!listState.isScrollInProgress && (System.currentTimeMillis() - lastScrollTime) >= 3500L) {
                    isUserBrowsing = false
                    break
                }
            }
        }
    }

    // 切歌（歌词列表更换）时回到顶部
    LaunchedEffect(lyrics) {
        listState.scrollToItem(0)
        isUserBrowsing = false
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleLyricMode?.invoke() }
                )
            }
    ) {
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val goldenOffsetPx = (viewportHeightPx * 0.38f).toInt()
        val goldenPaddingDp = with(density) { (viewportHeightPx * 0.38f).toDp() }

        // 视口中轴线对应的歌词行下标（供时间定位标线使用）
        val centerIndex by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@derivedStateOf activeIndex
                val targetCenter = goldenOffsetPx
                visibleItems.minByOrNull {
                    kotlin.math.abs((it.offset + it.size / 2) - targetCenter)
                }?.index ?: activeIndex
            }
        }

        val centerLine = lyrics.getOrNull(centerIndex)

        // 自动平滑跟唱滚动
        LaunchedEffect(activeIndex, isUserBrowsing) {
            if (!isUserBrowsing && !listState.isScrollInProgress && activeIndex in lyrics.indices) {
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
                if (item != null) {
                    val currentOffset = item.offset
                    val delta = currentOffset - goldenOffsetPx
                    if (kotlin.math.abs(delta) > 5) {
                        listState.animateScrollToItem(
                            index = activeIndex,
                            scrollOffset = -goldenOffsetPx
                        )
                    }
                } else {
                    listState.animateScrollToItem(
                        index = activeIndex,
                        scrollOffset = -goldenOffsetPx
                    )
                }
            }
        }

        // 歌词主列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        val topFade = 36.dp.toPx() / size.height
                        val bottomFade = 64.dp.toPx() / size.height
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                topFade to Color.Black,
                                1f - bottomFade to Color.Black,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                },
            contentPadding = PaddingValues(top = goldenPaddingDp, bottom = goldenPaddingDp + 60.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(
                items = lyrics,
                key = { index, line -> "$index:${line.timeMs}" },
            ) { index, line ->
                val isFocused = index == activeIndex
                val distance = kotlin.math.abs(index - activeIndex)

                // 字体尺寸与透明度渐变
                val lineAlpha by animateFloatAsState(
                    targetValue = when {
                        isFocused -> 1f
                        distance == 1 -> 0.58f
                        distance == 2 -> 0.40f
                        else -> 0.25f
                    },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                    label = "alpha_$index"
                )

                val lineScale by animateFloatAsState(
                    targetValue = if (isFocused) 1f else 0.96f,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
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
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                isUserBrowsing = false
                                onSeek(line.timeMs)
                            },
                            onLongClick = onLineLongClick
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = line.text,
                        color = Color.White,
                        fontSize = if (isFocused) fontSizeSp.sp else (fontSizeSp - 3).coerceAtLeast(14).sp,
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                        lineHeight = if (isFocused) (fontSizeSp * 1.35f).sp else (fontSizeSp * 1.25f).sp,
                        textAlign = TextAlign.Start,
                    )

                    if (showTranslation && !line.translated.isNullOrBlank() && line.translated != line.text) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = line.translated,
                            color = if (isFocused) AmberLight.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.32f),
                            fontSize = if (isFocused) 15.sp else 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }

        // 主流交互：用户翻阅时的中轴线时间指示器与播放跳转按钮
        AnimatedVisibility(
            visible = isUserBrowsing && centerLine != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = goldenPaddingDp - 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧时间戳
                val timeStr = formatLyricTime(centerLine?.timeMs ?: 0L)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = timeStr,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 中间虚线
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .padding(horizontal = 10.dp)
                        .background(Color.White.copy(alpha = 0.16f))
                )

                // 右侧定位播放图标按钮 (点击即从该句起播)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (centerLine != null) {
                                    isUserBrowsing = false
                                    onSeek(centerLine.timeMs)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "定位播放",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 右下角悬浮「回到当前」胶囊按钮（仅在浏览且脱离当前播放行时显现）
        AnimatedVisibility(
            visible = isUserBrowsing && kotlin.math.abs(centerIndex - activeIndex) > 1,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            isUserBrowsing = false
                            scope.launch {
                                listState.animateScrollToItem(
                                    index = activeIndex,
                                    scrollOffset = -goldenOffsetPx
                                )
                            }
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.VerticalAlignBottom,
                        contentDescription = "回到当前",
                        tint = AmberLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "回到播放",
                        color = Color.White.copy(alpha = 0.9f),
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
