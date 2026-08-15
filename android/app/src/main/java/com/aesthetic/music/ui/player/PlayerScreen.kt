package com.aesthetic.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.music.data.settings.PlayerTheme
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.player.LyricLine
import com.aesthetic.music.player.PlayMode
import com.aesthetic.music.player.PlayerState
import com.aesthetic.music.player.currentLyricIndex
import com.aesthetic.music.ui.components.WaveBars
import com.aesthetic.music.ui.sheet.ThemeSheet
import com.aesthetic.music.ui.theme.AmberLight
import com.aesthetic.music.ui.theme.PlayerBg
import com.aesthetic.music.ui.theme.PlayerBgDark
import com.aesthetic.music.ui.theme.Rose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekToLyric: (LyricLine) -> Unit,
    onTogglePlayMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
) {
    val song = state.currentSong
    val lyricIndex = currentLyricIndex(state.lyrics, state.positionMs)
    var showLyric by remember { mutableStateOf(true) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var playerTheme by remember { mutableStateOf(SettingsStore.getPlayerTheme()) }

    val bgGradient: Brush = when (playerTheme) {
        PlayerTheme.CLASSIC -> Brush.verticalGradient(listOf(PlayerBg, PlayerBgDark))
        PlayerTheme.COVER -> Brush.verticalGradient(listOf(Color(0xFF5C2612), Color(0xFF1F0D06)))
        PlayerTheme.APPLE -> Brush.verticalGradient(listOf(Color(0xFF311B92), Color(0xFF880E4F), Color(0xFF1A237E)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        BackgroundImage(artworkUrl = song?.artwork, theme = playerTheme)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient, alpha = if (playerTheme == PlayerTheme.CLASSIC) 0.85f else 0.72f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            TopBar(
                onClose = onClose,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                PlayerChip(
                    icon = Icons.Filled.Speed,
                    text = "${state.speed}x",
                    active = state.speed != 1f,
                    onClick = { showSpeedDialog = true },
                )
                Spacer(Modifier.size(8.dp))
                PlayerChip(
                    icon = Icons.Filled.Bedtime,
                    text = if (state.sleepRemainingMs > 0) formatRemain(state.sleepRemainingMs) else "定时",
                    active = state.sleepRemainingMs > 0,
                    onClick = { showSleepDialog = true },
                )
                Spacer(Modifier.size(8.dp))
                PlayerChip(
                    icon = Icons.Filled.Palette,
                    text = playerTheme.label,
                    active = false,
                    onClick = { showThemeSheet = true },
                )
            }
            Spacer(Modifier.height(12.dp))

            // 歌曲信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.title ?: "未在播放",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        maxLines = 2,
                    )
                    Text(
                        song?.subtitleText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(onClick = onToggleFavorite),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "取消收藏" else "收藏",
                            tint = if (isFavorite) Rose else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 主内容区域：支持直接单击空白/内容切换 歌词与封面
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showLyric) {
                    LyricList(
                        lyrics = state.lyrics,
                        currentIndex = lyricIndex,
                        onLyricClick = { line -> onSeekToLyric(line) },
                        onToggleLyricMode = { showLyric = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AnimatedCoverBox(
                        artworkUrl = song?.artwork,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onToggleLyricMode = { showLyric = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 底部控制与进度条
            BottomControls(
                state = state,
                onToggle = onToggle,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onTogglePlayMode = onTogglePlayMode,
                onOpenQueue = { showQueueSheet = true },
            )
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = PlayerBg,
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(
                    "播放队列 (${state.queue.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                LazyColumn {
                    itemsIndexed(state.queue) { index, item ->
                        val isCurrent = index == state.currentIndex
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { onPlayQueueItem(index); showQueueSheet = false }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                if (isCurrent) WaveBars(Color.White, barWidth = 2.dp)
                                else Text(
                                    "${index + 1}",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                )
                                Text(
                                    item.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                )
                            }
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "移除",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onRemoveFromQueue(index) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            containerColor = PlayerBgDark,
        ) {
            ThemeSheet(
                current = playerTheme,
                onSelect = { theme ->
                    playerTheme = theme
                    SettingsStore.setPlayerTheme(theme)
                    showThemeSheet = false
                },
                onClose = { showThemeSheet = false },
            )
        }
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("播放倍速", color = Color.White) },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        TextButton(onClick = {
                            onSpeedChange(speed)
                            showSpeedDialog = false
                        }) {
                            Text(
                                "${speed}x${if (speed == state.speed) "  ✓" else ""}",
                                color = if (speed == state.speed) AmberLight else Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("取消") }
            },
            containerColor = PlayerBg,
        )
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("睡眠定时", color = Color.White) },
            text = {
                Column {
                    listOf(5, 10, 15, 30, 60).forEach { minutes ->
                        TextButton(onClick = {
                            onSleepTimer(minutes)
                            showSleepDialog = false
                        }) {
                            Text(
                                "$minutes 分钟后停止",
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                    if (state.sleepRemainingMs > 0) {
                        TextButton(onClick = {
                            onCancelSleepTimer()
                            showSleepDialog = false
                        }) {
                            Text("关闭定时", color = Rose)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSleepDialog = false }) { Text("取消") }
            },
            containerColor = PlayerBg,
        )
    }
}

/**
 * 带有手势跟手与单击切换模式的封面 Box
 */
@Composable
private fun AnimatedCoverBox(
    artworkUrl: String?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleLyricMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val swipeThreshold = with(LocalDensity.current) { 100.dp.toPx() }

    Box(
        modifier = modifier
            .pointerInput(artworkUrl) {
                detectTapGestures(
                    onTap = { onToggleLyricMode() } // 单击直接切换至歌词模式
                )
            }
            .pointerInput(artworkUrl) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            val currentX = offsetX.value
                            if (abs(currentX) >= swipeThreshold) {
                                val targetX = if (currentX > 0) 1000f else -1000f
                                offsetX.animateTo(targetX, animationSpec = tween(180))
                                if (currentX > 0) onPrevious() else onNext()
                                offsetX.snapTo(if (currentX > 0) -600f else 600f)
                                offsetX.animateTo(0f, animationSpec = spring(stiffness = 300f))
                            } else {
                                offsetX.animateTo(0f, animationSpec = spring(stiffness = 400f))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f) }
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val currentOffset = offsetX.value
        val progress = (abs(currentOffset) / swipeThreshold).coerceIn(0f, 1f)
        val rotation = (currentOffset / 25f).coerceIn(-12f, 12f)
        val alpha = 1f - (progress * 0.25f)

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = currentOffset
                    rotationZ = rotation
                    this.alpha = alpha
                }
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无封面", color = Color.White.copy(alpha = 0.4f))
                }
            }
        }
    }
}

/**
 * 歌词列表：单击任意区域/歌词可切回封面
 */
@Composable
private fun LyricList(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    onLyricClick: (LyricLine) -> Unit,
    onToggleLyricMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.clickable { onToggleLyricMode() },
            contentAlignment = Alignment.Center
        ) {
            Text("暂无歌词", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val listState = rememberLazyListState()
    var isUserDragging by remember { mutableStateOf(false) }
    var userTouchEpoch by remember { mutableStateOf(0L) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) {
                    isUserDragging = true
                    userTouchEpoch = System.currentTimeMillis()
                } else {
                    userTouchEpoch = System.currentTimeMillis()
                }
            }
    }

    LaunchedEffect(userTouchEpoch, isUserDragging) {
        if (isUserDragging) {
            delay(3000)
            isUserDragging = false
        }
    }

    LaunchedEffect(currentIndex, isUserDragging) {
        if (!isUserDragging && currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex.coerceAtLeast(0))
        }
    }

    val centerLyricLine by remember {
        derivedStateOf {
            if (!isUserDragging) null
            else {
                val layoutInfo = listState.layoutInfo
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val nearestItem = layoutInfo.visibleItemsInfo.minByOrNull {
                    abs((it.offset + it.size / 2) - viewportCenter)
                }
                nearestItem?.let { lyrics.getOrNull(it.index) }
            }
        }
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { onToggleLyricMode() } // 点击歌词背景区域切回封面
            )
        }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isCurrent = index == currentIndex
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(line) {
                            detectTapGestures(
                                onTap = { onToggleLyricMode() }, // 单击歌词切回封面
                                onDoubleTap = {
                                    onLyricClick(line)
                                    isUserDragging = false
                                }
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        line.text,
                        style = when {
                            isCurrent -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            else -> MaterialTheme.typography.bodyLarge
                        },
                        color = when {
                            isCurrent -> Color.White
                            index == currentIndex + 1 -> Color.White.copy(alpha = 0.55f)
                            else -> Color.White.copy(alpha = 0.3f)
                        },
                        textAlign = TextAlign.Start,
                    )
                    if (line.translated != null && line.translated != line.text) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            line.translated,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) AmberLight.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.22f),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }

        centerLyricLine?.let { targetLine ->
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DottedLine(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                )

                Spacer(Modifier.size(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = formatTime(targetLine.timeMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.size(8.dp))

                DottedLine(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                )

                Spacer(Modifier.size(8.dp))

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AmberLight)
                        .clickable {
                            onLyricClick(targetLine)
                            isUserDragging = false
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "跳转",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            "跳转",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DottedLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
            strokeWidth = 2f
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomControls(
    state: PlayerState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayMode: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    // 缓冲 Seek：滑动时只改变 UI 临时值，手松开时才真正 Seek
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val currentPos = if (isDragging) dragPositionMs.toLong() else state.positionMs

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        Slider(
            value = currentPos.coerceAtMost(state.durationMs).toFloat(),
            onValueChange = {
                isDragging = true
                dragPositionMs = it
            },
            onValueChangeFinished = {
                onSeek(dragPositionMs.toLong())
                isDragging = false
            },
            valueRange = 0f..(state.durationMs.takeIf { it > 0 } ?: 1L).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
            thumb = {
                // 精简细腻的滑动小圆点
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.White, CircleShape)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(3.dp), // 细进度条
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                    ),
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp,
                )
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(currentPos), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
            Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when (state.playMode) {
                    PlayMode.SEQUENCE -> Icons.Filled.Shuffle
                    PlayMode.REPEAT -> Icons.Filled.RepeatOne
                    PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                },
                contentDescription = "播放模式",
                tint = if (state.playMode == PlayMode.SEQUENCE) Color.White.copy(alpha = 0.55f) else Color.White,
                modifier = Modifier.size(22.dp).clickable(onClick = onTogglePlayMode),
            )
            Icon(
                Icons.Filled.SkipPrevious, contentDescription = "上一首", tint = Color.White,
                modifier = Modifier.size(30.dp).clickable(onClick = onPrevious),
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    tint = PlayerBg,
                    modifier = Modifier.size(30.dp),
                )
            }
            Icon(
                Icons.Filled.SkipNext, contentDescription = "下一首", tint = Color.White,
                modifier = Modifier.size(30.dp).clickable(onClick = onNext),
            )
            // 右下角按钮替换为播放队列
            Icon(
                Icons.Filled.QueueMusic,
                contentDescription = "播放队列",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp).clickable(onClick = onOpenQueue),
            )
        }
    }
}

@Composable
private fun PlayerChip(
    icon: ImageVector,
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
    }
}

private fun formatRemain(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
private fun BackgroundImage(artworkUrl: String?, theme: PlayerTheme) {
    if (!artworkUrl.isNullOrBlank()) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun TopBar(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // 左侧收起按钮
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            PlayerCircleButton(Icons.Filled.KeyboardArrowDown, onClick = onClose)
        }

        // 中间标题
        Text(
            "正在播放",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun PlayerCircleButton(
    icon: ImageVector,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}