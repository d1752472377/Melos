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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.player.LyricLine
import com.aesthetic.music.player.karaokeSweepFraction
import com.aesthetic.music.ui.theme.AmberLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MelosLyricsView(
    lyrics: List<LyricLine>,
    activeIndexProvider: () -> Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    currentTimeMsProvider: (() -> Long)? = null,
    fontSizeSp: Int = 22,
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    /** 歌词是否正在加载：与「确实没有歌词」区分开，避免用户以为歌词坏了 */
    isLoading: Boolean = false,
    /** 空歌词时点一下重新拉取 */
    onRetryLyric: (() -> Unit)? = null,
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
                    onClick = {
                        // 空歌词时：优先重新拉取（这是用户最想要的），没有再退回切换封面
                        if (onRetryLyric != null && !isLoading) onRetryLyric() else onToggleLyricMode?.invoke()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.75f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "正在加载歌词…",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 15.sp
                    )
                } else {
                    Text(
                        text = "暂无歌词",
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 16.sp
                    )
                    if (onRetryLyric != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "点一下重新加载",
                            color = AmberLight.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val scrollCtl = rememberAutoScrollController(listState, cooldownMs = 2500L)
    scrollCtl.BindUserInteraction()

    val lineHeightsPx: SnapshotStateMap<Int, Int> = remember { mutableStateMapOf() }
    var activeIndex by remember { mutableIntStateOf(0) }

    // 用容器高度替代 BoxWithConstraints 的 maxHeight
    var containerHeightPx by remember { mutableIntStateOf(0) }
    // 活跃歌词定位：顶部留 30% 容器高，底部留 70%，歌词整体偏上显示
    val topPaddingDp: Dp = with(density) { (containerHeightPx * 0.30f).toDp() }
    val bottomPaddingDp: Dp = with(density) { (containerHeightPx * 0.70f).toDp() }

    LaunchedEffect(lyrics) {
        while (isActive) {
            val idx = activeIndexProvider().coerceIn(0, lyrics.lastIndex)
            if (idx != activeIndex) activeIndex = idx
            delay(80)
        }
    }

    // contentPadding 上下留半屏：scrollToItem(N, 0) 让 N 行顶部对齐内容区起点即视口中线
    fun offsetFor(@Suppress("UNUSED_PARAMETER") index: Int): Int = 0

    // 首次加载 / 切歌：瞬时定位
    LaunchedEffect(lyrics) {
        lineHeightsPx.clear()
        val initialIdx = activeIndexProvider().coerceIn(0, lyrics.lastIndex)
        activeIndex = initialIdx
        scrollCtl.forceScrollTo(initialIdx, offsetFor(initialIdx), animate = false)
    }

    // 播放中自动跟唱
    LaunchedEffect(activeIndex) {
        if (activeIndex in lyrics.indices) {
            scrollCtl.autoScrollTo(activeIndex, offsetFor(activeIndex), animate = true)
        }
    }

    val centerIndex by remember {
        derivedStateOf { listState.layoutInfo.centerItemIndex(fallback = activeIndex) }
    }
    val centerLine = lyrics.getOrNull(centerIndex)
    val isUserBrowsing = scrollCtl.isUserControlling

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 不对称 padding：顶部 30%，底部 70%，活跃歌词定位在屏幕偏上位置
            contentPadding = PaddingValues(top = topPaddingDp, bottom = bottomPaddingDp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(
                items = lyrics,
                key = { index, line -> "$index:${line.timeMs}" },
            ) { index, line ->
                val isFocused = index == activeIndex
                val isBrowsingTarget = isUserBrowsing && index == centerIndex
                val isHighlighted = if (isUserBrowsing) isBrowsingTarget else isFocused
                val distance = kotlin.math.abs(
                    index - (if (isUserBrowsing) centerIndex else activeIndex)
                )

                val lineAlpha by animateFloatAsState(
                    targetValue = when {
                        isHighlighted -> 1f
                        distance == 1 -> 0.60f
                        distance == 2 -> 0.38f
                        distance == 3 -> 0.22f
                        else -> 0.13f
                    },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "alpha_$index"
                )

                val lineScale by animateFloatAsState(
                    targetValue = if (isHighlighted) 1.02f else 0.97f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "scale_$index"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size -> lineHeightsPx[index] = size.height }
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
                                    onSeek(line.timeMs)
                                    scrollCtl.forceScrollTo(index, offsetFor(index), animate = true)
                                } else {
                                    onToggleLyricMode?.invoke()
                                }
                            },
                            onLongClick = onLineLongClick
                        )
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    if (showRomanization && !line.romanization.isNullOrBlank() && line.romanization != line.text) {
                        Text(
                            text = line.romanization,
                            color = if (isHighlighted) AmberLight.copy(alpha = 0.70f)
                            else Color.White.copy(alpha = 0.25f),
                            fontSize = if (isHighlighted) (fontSizeSp - 9).coerceAtLeast(11).sp
                            else (fontSizeSp - 11).coerceAtLeast(10).sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = (fontSizeSp * 0.85f).sp,
                            textAlign = TextAlign.Start,
                        )
                        Spacer(Modifier.height(2.dp))
                    }

                    if (line.isWordByWord && isHighlighted) {
                        val currentMs = currentTimeMsProvider?.invoke() ?: line.timeMs
                        KaraokeSweepLine(
                            line = line,
                            currentTimeMs = currentMs,
                            fontSizeSp = fontSizeSp,
                            sungColor = if (isFocused) Color.White else AmberLight,
                        )
                    } else {
                        Text(
                            text = line.text,
                            color = if (isHighlighted) {
                                if (isFocused) Color.White else AmberLight
                            } else Color.White,
                            fontSize = if (isHighlighted) fontSizeSp.sp
                            else (fontSizeSp - 3).coerceAtLeast(14).sp,
                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                            lineHeight = if (isHighlighted) (fontSizeSp * 1.38f).sp
                            else (fontSizeSp * 1.28f).sp,
                            textAlign = TextAlign.Start,
                        )
                    }

                    if (showTranslation && !line.translated.isNullOrBlank() &&
                        line.translated != line.text
                    ) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = line.translated,
                            color = if (isHighlighted) AmberLight.copy(alpha = 0.92f)
                            else Color.White.copy(alpha = 0.32f),
                            fontSize = if (isHighlighted) (fontSizeSp - 6).coerceAtLeast(13).sp
                            else (fontSizeSp - 8).coerceAtLeast(12).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = if (isHighlighted) (fontSizeSp * 0.95f).sp
                            else (fontSizeSp * 0.85f).sp,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }


        // 翻阅指示线（仅在用户手动滑动时出现，自动跟唱不触发）
        val topAnchorDp: Dp = with(LocalDensity.current) { (containerHeightPx * 0.30f).toDp() }
        AnimatedVisibility(
            visible = isUserBrowsing && centerLine != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = (topAnchorDp - 16.dp).coerceAtLeast(0.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = formatLyricTime(centerLine?.timeMs ?: 0L),
                        color = AmberLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .padding(horizontal = 8.dp)
                        .background(Color.White.copy(alpha = 0.22f))
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AmberLight.copy(alpha = 0.88f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val target = centerLine
                                if (target != null) {
                                    onSeek(target.timeMs)
                                    scrollCtl.forceScrollTo(
                                        centerIndex, offsetFor(centerIndex), animate = true
                                    )
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

        // 「回到播放」胶囊
        AnimatedVisibility(
            visible = isUserBrowsing && kotlin.math.abs(centerIndex - activeIndex) > 2,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            scrollCtl.forceScrollTo(activeIndex, offsetFor(activeIndex), animate = true)
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

private fun formatLyricTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

@Composable
private fun KaraokeSweepLine(
    line: LyricLine,
    currentTimeMs: Long,
    fontSizeSp: Int,
    sungColor: Color,
    modifier: Modifier = Modifier,
) {
    // 已唱占比按「字数」算（不是时间占比）：染色是沿文字从左往右擦过去的，
    // 用时间占比会在字宽不等时出现「字还没唱到、颜色已经盖过去」的错位。
    val fraction = karaokeSweepFraction(line, currentTimeMs)
    var fullWidthPx by remember(line.text, fontSizeSp) { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val style = TextStyle(
        fontSize = fontSizeSp.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = (fontSizeSp * 1.38f).sp,
        textAlign = TextAlign.Start,
    )

    Box(modifier = modifier.fillMaxWidth()) {
        // 底：未唱部分（暗色）
        Text(
            text = line.text,
            color = Color.White.copy(alpha = 0.45f),
            style = style,
            onTextLayout = { result ->
                if (result.size.width > 0 && result.size.width != fullWidthPx) {
                    fullWidthPx = result.size.width
                }
            },
        )
        // 面：已唱部分（亮色），用「限定宽度 + 裁切」实现逐字扫描
        if (fraction > 0f && fullWidthPx > 0) {
            val sungWidth = with(density) { (fullWidthPx * fraction).toDp() }
            Box(
                modifier = Modifier
                    .clipToBounds()
                    .requiredWidth(sungWidth.coerceAtLeast(0.5.dp)),
            ) {
                Text(
                    text = line.text,
                    color = sungColor,
                    style = style,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
