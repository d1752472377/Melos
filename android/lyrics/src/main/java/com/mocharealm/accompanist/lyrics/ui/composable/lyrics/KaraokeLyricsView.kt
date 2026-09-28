package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.utils.isRtl
import com.mocharealm.accompanist.lyrics.ui.utils.modifier.springPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

internal data class FocusState(
    val firstIndex: Int,
    val allIndices: List<Int>,
    val activeInterludeIndex: Int?,
    val activeIntro: Boolean
)

/**
 * A comprehensive lyrics view that supports Karaoke and Synced lyrics with advanced rendering.
 *
 * This composable handles:
 * - Scrolling and auto-scrolling to the current line
 * - Rendering karaoke lines with syllable-level timing and animations
 * - Rendering synced lines
 * - Displaying breathing dots during instrumental interludes
 * - Determining active and accompaniment lines
 *
 * @param listState The scroll state for the lazy list.
 * @param lyrics The lyrics data to display.
 * @param currentPosition A lambda returning the current playback position in milliseconds.
 * @param onLineClicked Callback when a line is clicked (seek to position).
 * @param onLinePressed Callback when a line is long-pressed (share/menu).
 * @param modifier The modifier to apply to the layout.
 * @param normalLineTextStyle The style for normal text lines.
 * @param accompanimentLineTextStyle The style for accompaniment/background vocals lines.
 * @param textColor The primary text color.
 * @param breathingDotsDefaults Styling defaults for the breathing dots.
 * @param blendMode The blend mode used for rendering text (e.g., [BlendMode.Plus] for glowing effects).
 * @param useBlurEffect Whether to apply blur effect to non-active lines.
 * @param offset The vertical padding/offset at the start and end of the list.
 * @param showDebugRectangles Debug flag to draw bounding boxes around glyphs.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun KaraokeLyricsView(
    listState: LazyListState,
    lyrics: SyncedLyrics,
    currentPosition: () -> Int,
    onLineClicked: (ISyncedLine) -> Unit,
    onLinePressed: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier,
    onSeek: ((Long) -> Unit)? = null,
    onToggleLyricMode: (() -> Unit)? = null,
    normalLineTextStyle: TextStyle = LocalTextStyle.current.copy(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        textMotion = TextMotion.Animated,
    ),
    accompanimentLineTextStyle: TextStyle = LocalTextStyle.current.copy(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textMotion = TextMotion.Animated,
    ),
    textColor: Color = Color.White,
    breathingDotsDefaults: KaraokeBreathingDotsDefaults = KaraokeBreathingDotsDefaults(),
    phoneticTextStyle: TextStyle = normalLineTextStyle.copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
//    TODO: expose it
//    verticalFadeBrush: Brush = Brush.verticalGradient(
//        0f to Color.White.copy(0f),
//        0.05f to Color.White,
//        0.6f to Color.White,
//        1f to Color.White.copy(0f)
//    ),
    blendMode: BlendMode = BlendMode.Plus,
    useBlurEffect: Boolean = false,
    showTranslation: Boolean = true,
    showPhonetic: Boolean = true,
    offset: Dp = 32.dp,
    keepAliveZone: Dp = 100.dp,
    blurDelta: Float = 0f,
    showDebugRectangles: Boolean = false
) {
    val density = LocalDensity.current
    val stableNormalTextStyle = remember(normalLineTextStyle) { normalLineTextStyle }
    val stableAccompanimentTextStyle =
        remember(accompanimentLineTextStyle) { accompanimentLineTextStyle }
    val stablePhoneticTextStyle = remember(phoneticTextStyle) { phoneticTextStyle }
    val stableOffset = remember(offset) { offset }
    val stableOffsetPx =
        remember(stableOffset) { with(density) { stableOffset.toPx().fastRoundToInt() } }
    val keepAliveZonePx = with(density) { keepAliveZone.toPx() }
    val stableBlendMode = remember(blendMode) { blendMode }

    val textMeasurer = rememberTextMeasurer()
    val layoutCache = remember { mutableStateMapOf<Int, List<SyllableLayout>>() }

    LaunchedEffect(
        lyrics,
        stableNormalTextStyle,
        stableAccompanimentTextStyle,
        stablePhoneticTextStyle
    ) {
        layoutCache.clear()
        withContext(Dispatchers.Default) {
            val normalStyle = stableNormalTextStyle.copy(textDirection = TextDirection.Content)
            val accompanimentStyle =
                stableAccompanimentTextStyle.copy(textDirection = TextDirection.Content)
            val phoneticStyle = stablePhoneticTextStyle.copy(textDirection = TextDirection.Content)

            val normalSpaceWidth = textMeasurer.measure(" ", normalStyle).size.width.toFloat()
            val accompanimentSpaceWidth =
                textMeasurer.measure(" ", accompanimentStyle).size.width.toFloat()

            lyrics.lines.forEachIndexed { index, line ->
                if (!isActive) return@forEachIndexed
                if (line is KaraokeLine) {
                    val style =
                        if (line is KaraokeLine.AccompanimentKaraokeLine) accompanimentStyle else normalStyle
                    val spaceWidth =
                        if (line is KaraokeLine.AccompanimentKaraokeLine) accompanimentSpaceWidth else normalSpaceWidth

                    val processedSyllables = if (line.alignment == KaraokeAlignment.End) {
                        line.syllables.dropLastWhile { it.content.isBlank() }
                    } else {
                        line.syllables
                    }

                    val layout = measureSyllablesAndDetermineAnimation(
                        syllables = processedSyllables,
                        textMeasurer = textMeasurer,
                        style = style,
                        phoneticStyle = phoneticStyle,
                        isAccompanimentLine = line is KaraokeLine.AccompanimentKaraokeLine,
                        spaceWidth = spaceWidth
                    )

                    withContext(Dispatchers.Main) {
                        layoutCache[index] = layout
                    }
                }
            }
        }
    }

    val currentTimeMs: () -> Int = currentPosition

    val timeProvider = remember { currentPosition }

    val accompanimentToMainMap = remember(lyrics.lines) {
        val map = mutableMapOf<Int, Int>()
        val mainLinesIndices = lyrics.lines.indices.filter { index ->
            val line = lyrics.lines[index]
            line !is KaraokeLine || line !is KaraokeLine.AccompanimentKaraokeLine
        }
        if (mainLinesIndices.isNotEmpty()) {
            lyrics.lines.forEachIndexed { index, line ->
                if (line is KaraokeLine && line is KaraokeLine.AccompanimentKaraokeLine) {
                    // Find the main line that is closest in time (either the one just before or just after)
                    val beforeIdx = mainLinesIndices.findLast { it <= index }
                    val afterIdx = mainLinesIndices.find { it >= index }

                    val anchorIndex = when {
                        beforeIdx != null && afterIdx != null -> {
                            val distBefore =
                                (line.start - lyrics.lines[beforeIdx].start).absoluteValue
                            val distAfter =
                                (lyrics.lines[afterIdx].start - line.start).absoluteValue
                            if (distBefore <= distAfter) beforeIdx else afterIdx
                        }

                        beforeIdx != null -> beforeIdx
                        afterIdx != null -> afterIdx
                        else -> mainLinesIndices.first()
                    }
                    map[index] = anchorIndex
                }
            }
        }
        map
    }
    val effectiveEndTimes = remember(lyrics.lines) {
        IntArray(lyrics.lines.size) { index ->
            val line = lyrics.lines[index]
            var maxEnd = line.end

            if (line is KaraokeLine.MainKaraokeLine) {
                line.accompanimentLines?.forEach { acc ->
                    if (acc.end > maxEnd) maxEnd = acc.end
                }
            }
            maxEnd
        }
    }

    val firstLine = lyrics.lines.firstOrNull()

    val haveDotsIntro by remember(firstLine) {
        derivedStateOf {
            if (firstLine == null) false
            else (firstLine.start > 5000)
        }
    }

    val lyricsFocusState by remember(lyrics, effectiveEndTimes, accompanimentToMainMap, haveDotsIntro) {
        derivedStateOf {
            val time = currentTimeMs()
            val activeIndex = lyrics.lines.indices.find { idx ->
                time >= lyrics.lines[idx].start && time < effectiveEndTimes[idx]
            }

            val first = if (activeIndex != null) {
                activeIndex
            } else {
                val nextIdx = lyrics.lines.indexOfFirst { it.start > time }
                if (nextIdx != -1) nextIdx else lyrics.lines.lastIndex
            }

            val base = lyrics.lines.indices.filter { index ->
                time >= lyrics.lines[index].start && time < effectiveEndTimes[index]
            }
            val result = base.toMutableSet()
            base.forEach { index ->
                val line = lyrics.lines.getOrNull(index)
                if (line is KaraokeLine && line is KaraokeLine.AccompanimentKaraokeLine) {
                    accompanimentToMainMap[index]?.let { result.add(it) }
                }
            }

            val activeInterludeIndex = lyrics.lines.indices.find { index ->
                val line = lyrics.lines[index]
                val previousLine = lyrics.lines.getOrNull(index - 1)
                previousLine != null && (line.start - previousLine.end > 5000) && time in previousLine.end..line.start
            }
            val activeIntro = haveDotsIntro && time in 0 until (firstLine?.start ?: 0)

            FocusState(first, result.toList().sorted(), activeInterludeIndex, activeIntro)
        }
    }

    val scrollInCode = remember { mutableStateOf(false) }

    // 记录用户手动翻阅歌词的保护期：当用户主动滑动列表时，顺延 3.5 秒；
    // 期间绝不抢夺用户手指或突然强行回拉，给予充裕阅读时间；时间过后歌曲唱到新句子时平滑跟进
    var userBrowsingUntil by remember { mutableStateOf(0L) }

    // 标记用户是否刚刚松手（用于触发独立的冷却倒计时协程）
    var userJustReleasedScroll by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !scrollInCode.value) {
                // 用户主动滑动：延长保护期，取消冷却
                userBrowsingUntil = System.currentTimeMillis() + 3500L
                userJustReleasedScroll = false
            } else if (!scrolling && !scrollInCode.value && userBrowsingUntil > 0L) {
                // 松手：触发冷却倒计时
                userJustReleasedScroll = true
            }
        }
    }

    // 松手后的独立冷却协程：到期后清除保护期
    LaunchedEffect(userJustReleasedScroll) {
        if (!userJustReleasedScroll) return@LaunchedEffect
        userJustReleasedScroll = false
        while (isActive) {
            delay(200)
            if (System.currentTimeMillis() >= userBrowsingUntil) {
                userBrowsingUntil = 0L
                break
            }
        }
    }

    val isManualScrolling by remember {
        derivedStateOf {
            listState.isScrollInProgress && !scrollInCode.value
        }
    }

    LaunchedEffect(
        layoutCache,
        stableOffsetPx,
    ) {
        androidx.compose.runtime.snapshotFlow { lyricsFocusState.firstIndex }
            .collect { firstIndex ->
                val now = System.currentTimeMillis()
                if (!scrollInCode.value && !listState.isScrollInProgress && now >= userBrowsingUntil) {
                    val viewportHeight = listState.layoutInfo.viewportSize.height
                    val items = listState.layoutInfo.visibleItemsInfo
                    val targetItem = items.firstOrNull { it.index == firstIndex }
                    try {
                        scrollInCode.value = true
                        if (targetItem != null) {
                            // 精确居中：scrollOffset = -(viewportH/2 - itemH/2)
                            // 让 item 中心对齐视口中心，使用平滑动画
                            val centeredOffset = -(viewportHeight / 2 - targetItem.size / 2)
                            listState.animateScrollToItem(firstIndex, centeredOffset)
                        } else {
                            // item 不在可见范围：animateScrollToItem 跳到附近
                            listState.animateScrollToItem(firstIndex, 0)
                        }
                    } catch (_: Exception) {
                    } finally {
                        scrollInCode.value = false
                    }
                }
            }
    }
    LookaheadScope {
        Crossfade(lyrics) { lyrics ->
            // BoxWithConstraints 提供实际视口尺寸，用于动态计算 contentPadding：
            // 上下各留半屏，确保首行和末行都能滚动到正中央，不再依赖固定 Spacer 或 layout hack
            androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.clipToBounds()) {
                val halfViewport = maxHeight / 2
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithCache {
                            onDrawWithContent {
                                drawContent()
                                val topFade = 20.dp.toPx() / size.height
                                val bottomFade = 100.dp.toPx() / size.height
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
                    // 用动态半视口高度替代旧的 (stableOffset + keepAliveZone) + 160dp Spacer 组合
                    contentPadding = PaddingValues(vertical = halfViewport)
                ) {
                    itemsIndexed(
                        items = lyrics.lines,
                        key = { index, line -> "${line.start}-${line.end}-$index" }
                    ) { index, line ->
                        val isCurrentFocusLine = index in lyricsFocusState.allIndices
                        val isLineRtl =
                            when (line) {
                                is KaraokeLine -> {
                                    remember(line.syllables) { line.syllables.any { it.content.isRtl() } }
                                }

                                else -> false
                            }
                        val isLineRightAligned = when (line) {
                            is KaraokeLine -> {
                                remember { line.alignment == KaraokeAlignment.End }
                            }

                            else -> false
                        }
                        val isVisualRightAligned = remember(isLineRightAligned, isLineRtl) {
                            if (isLineRightAligned) !isLineRtl
                            else isLineRtl
                        }

                        val distanceWeightState = remember(useBlurEffect, lyricsFocusState) {
                            derivedStateOf {
                                val start = lyricsFocusState.allIndices.firstOrNull() ?: lyricsFocusState.firstIndex
                                val end = lyricsFocusState.allIndices.lastOrNull() ?: lyricsFocusState.firstIndex
                                maxOf(0, start - index, index - end)
                            }
                        }

                        val dynamicStiffness by remember(distanceWeightState.value) {
                            derivedStateOf {
                                (120f - (distanceWeightState.value * 20f)).coerceAtLeast(20f)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .springPlacement(
                                    this@LookaheadScope,
                                    "${line.start}-${line.end}-$index",
                                    isManualScrolling,
                                    stiffness = dynamicStiffness
                                ),
                            horizontalAlignment = if (isVisualRightAligned) Alignment.End else Alignment.Start
                        ) {
                            val animDuration = 600

                            val previousLine = lyrics.lines.getOrNull(index - 1)

                            val showDotsInterlude = lyricsFocusState.activeInterludeIndex == index
                            val showDotsIntro = lyricsFocusState.activeIntro && index == 0

                            AnimatedVisibility(showDotsInterlude || showDotsIntro) {
                                KaraokeBreathingDots(
                                    alignment = when (val line = previousLine ?: firstLine) {
                                        is KaraokeLine -> line.alignment
                                        is SyncedLine -> if (line.content.isRtl()) KaraokeAlignment.End else KaraokeAlignment.Start
                                        else -> KaraokeAlignment.Start
                                    },
                                    startTimeMs = previousLine?.end ?: 0,
                                    endTimeMs = if (showDotsIntro) firstLine!!.start else line.start,
                                    currentTimeProvider = timeProvider,
                                    defaults = breathingDotsDefaults,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }


                            val blurRadiusState = animateFloatAsState(
                                targetValue = (
                                        if (!useBlurEffect || listState.isScrollInProgress) 0f
                                        else if (distanceWeightState.value > 0 && (!listState.isScrollInProgress || scrollInCode.value)) {
                                            (distanceWeightState.value * blurDelta).coerceAtMost(blurDelta * 1.5f)
                                        } else 0f),
                                animationSpec = tween(300),
                            )

                            when (line) {
                                is KaraokeLine -> {
                                    if (line is KaraokeLine.MainKaraokeLine) {
                                         LyricsLineItem(
                                            isFocused = isCurrentFocusLine,
                                            isRightAligned = isVisualRightAligned,
                                            activeAlpha = 1f,
                                            inactiveAlpha = 0.55f,
                                            onLineClicked = {
                                                userBrowsingUntil = 0L
                                                onSeek?.invoke(line.start.toLong()) ?: onLineClicked(line)
                                            },
                                            onLinePressed = {
                                                userBrowsingUntil = 0L
                                                onSeek?.invoke(line.start.toLong()) ?: onLinePressed(line)
                                            },
                                            blurRadius = { blurRadiusState.value },
                                            blendMode = stableBlendMode,
                                        ) {
                                            KaraokeLineText(
                                                line = line,
                                                currentTimeProvider = timeProvider,
                                                normalLineTextStyle = stableNormalTextStyle,
                                                accompanimentLineTextStyle = stableAccompanimentTextStyle,
                                                phoneticTextStyle = stablePhoneticTextStyle,
                                                activeColor = textColor,
                                                blendMode = stableBlendMode,
                                                showDebugRectangles = showDebugRectangles,
                                                showTranslation = showTranslation,
                                                showPhonetic = showPhonetic,
                                                precalculatedLayouts = layoutCache[index]
                                            )
                                        }
                                    }
                                }

                                is SyncedLine -> {
                                    val isLineRtl = remember(line.content) { line.content.isRtl() }
                                    LyricsLineItem(
                                        isFocused = isCurrentFocusLine,
                                        isRightAligned = isLineRtl,
                                        activeAlpha = 1f,
                                        inactiveAlpha = 0.55f,
                                        onLineClicked = {
                                            userBrowsingUntil = 0L
                                            onSeek?.invoke(line.start.toLong()) ?: onLineClicked(line)
                                        },
                                        onLinePressed = {
                                            userBrowsingUntil = 0L
                                            onSeek?.invoke(line.start.toLong()) ?: onLinePressed(line)
                                        },
                                        blurRadius = { blurRadiusState.value },
                                        blendMode = stableBlendMode,
                                    ) {
                                        SyncedLineText(
                                            line = line,
                                            isLineRtl = isLineRtl,
                                            textStyle = stableNormalTextStyle.copy(lineHeight = 1.2.em),
                                            textColor = textColor,
                                            showTranslation = showTranslation,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}