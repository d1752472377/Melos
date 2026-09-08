package com.aesthetic.music.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.settings.PlayerTheme
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.player.LyricLine
import com.aesthetic.music.player.PlayMode
import com.aesthetic.music.player.PlaybackProgress
import com.aesthetic.music.player.PlayerState
import com.aesthetic.music.player.currentLyricIndex
import com.aesthetic.music.ui.components.WaveBars
import com.aesthetic.music.ui.sheet.ThemeSheet
import com.aesthetic.music.ui.theme.AmberLight
import com.aesthetic.music.ui.theme.PlayerBg
import com.aesthetic.music.ui.theme.PlayerBgDark
import com.aesthetic.music.ui.theme.Rose
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    /** 高频进度流：在播放页内部订阅，避免进度更新向上扩散到 AppShell */
    progressFlow: StateFlow<PlaybackProgress>,
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
    // 进度只在播放页内部收集：每 500ms 一次的更新不会离开本页作用域
    val progress by progressFlow.collectAsState()
    val lyricIndex = currentLyricIndex(state.lyrics, progress.positionMs)
    val karaokeListState = rememberLazyListState()
    val animatedPositionState = remember { mutableLongStateOf(0L) }
    val currentPositionProvider = remember {
        { animatedPositionState.longValue.toInt() }
    }

    // 帧级平滑插值：逐帧刷新当前毫秒数，供 KaraokeLyricsView 绘制平滑字级染色与滚动，无需引起全页重组
    LaunchedEffect(state.isPlaying, progress.positionMs, state.speed) {
        animatedPositionState.longValue = progress.positionMs
        if (state.isPlaying) {
            val baseWallTime = System.currentTimeMillis()
            val basePos = progress.positionMs
            while (isActive) {
                awaitFrame()
                val elapsed = ((System.currentTimeMillis() - baseWallTime) * state.speed).toLong()
                val newPos = (basePos + elapsed).coerceAtMost(progress.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
                animatedPositionState.longValue = newPos
            }
        }
    }

    var showLyric by remember { mutableStateOf(true) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var playerTheme by remember { mutableStateOf(SettingsStore.getPlayerTheme()) }

    // 主题不变时缓存渐变画刷，避免每次重组（含每 500ms 进度刷新）重新分配
    val bgGradient: Brush = remember(playerTheme) {
        when (playerTheme) {
            PlayerTheme.CLASSIC -> Brush.verticalGradient(listOf(PlayerBg, PlayerBgDark))
            PlayerTheme.COVER -> Brush.verticalGradient(listOf(Color(0xFF5C2612), Color(0xFF1F0D06)))
            PlayerTheme.APPLE -> Brush.verticalGradient(listOf(Color(0xFF311B92), Color(0xFF880E4F), Color(0xFF1A237E)))
        }
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
                    // 1f 直接插值成 "1.0x"，整数倍率显示成 "1x"/"2x" 更干净
                    text = if (state.speed % 1f == 0f) "${state.speed.toInt()}x" else "${state.speed}x",
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
            Spacer(Modifier.height(14.dp))

            // 歌曲信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "未在播放",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = song?.subtitleText ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        ),
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { showLyric = !showLyric },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (showLyric) Icons.Filled.Album else Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = if (showLyric) "切换封面" else "切换歌词",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(onClick = onToggleFavorite),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "取消收藏" else "收藏",
                            tint = if (isFavorite) Rose else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp),
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
                    val synced = state.syncedLyrics
                    if (synced != null && synced.lines.isNotEmpty()) {
                        KaraokeLyricsView(
                            listState = karaokeListState,
                            lyrics = synced,
                            currentPosition = currentPositionProvider,
                            onLineClicked = { line -> onSeek(line.start.toLong()) },
                            onLinePressed = { line -> onSeek(line.start.toLong()) },
                            showTranslation = true,
                            showPhonetic = false,
                            textColor = Color.White,
                            normalLineTextStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 23.sp
                            ),
                            accompanimentLineTextStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            useBlurEffect = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { showLyric = false }
                                    )
                                }
                        )
                    } else {
                        LyricList(
                            lyrics = state.lyrics,
                            currentIndex = lyricIndex,
                            onLyricClick = { line -> onSeekToLyric(line) },
                            onToggleLyricMode = { showLyric = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
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
                positionMs = progress.positionMs,
                durationMs = progress.durationMs,
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
                    itemsIndexed(
                        state.queue,
                        // index 参与拼 key：既避免同曲重复入队时 key 碰撞崩溃，又能稳定复用
                        key = { i, item -> "$i:${item.platform}:${item.id}" },
                    ) { index, item ->
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
    // 飞出/飞入距离按容器实际宽度计算，替代硬编码像素（窄屏也能完整飞出视口）
    var containerWidthPx by remember { mutableFloatStateOf(1000f) }

    Box(
        modifier = modifier
            .onSizeChanged { containerWidthPx = it.width.toFloat().coerceAtLeast(1f) }
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
                                val dir = if (currentX > 0) 1f else -1f
                                val flyOut = containerWidthPx
                                offsetX.animateTo(dir * flyOut, animationSpec = tween(180))
                                if (dir > 0) onPrevious() else onNext()
                                offsetX.snapTo(-dir * flyOut * 0.6f)
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
        Box(
            modifier = Modifier
                .graphicsLayer {
                    // 延迟读取：在图层块内读 Animatable 值，拖动期间只更新绘制属性、不触发重组
                    val x = offsetX.value
                    val progress = (abs(x) / swipeThreshold).coerceIn(0f, 1f)
                    translationX = x
                    rotationZ = (x / 25f).coerceIn(-12f, 12f)
                    alpha = 1f - progress * 0.25f
                }
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(280.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .size(280.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
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
 * 歌词列表（主流播放器交互）：
 * - 中央虚线指示线常驻；用户拖动时浮现「时间 + 跳转」胶囊，松手淡出；
 * - 拖动与惯性滑动期间，高亮行实时跟随最接近中线的歌词，
 *   惯性下滑时呈现「一行行歌词被依次选中」的吸附感；
 * - 松手停稳后保持选中约 3.5 秒（期间不跟唱），随后平滑回正到当前播放行；
 * - 自动回正先等待进行中的惯性滚动自然结束，避免动画互相打断造成跳变。
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
    val scope = rememberCoroutineScope()

    // ===== 用户手势窗口 =====
    // holdAutoFollow=true：用户正在接管列表（拖动中 / 惯性滑动 / 停稳后的保持期）
    var holdAutoFollow by remember { mutableStateOf(false) }
    var userTouchEpoch by remember { mutableStateOf(0L) }

    // 用 InteractionSource 判定"真实用户拖动"：程序化滚动（animateScrollToItem）不会产生
    // DragInteraction。此前用 isScrollInProgress 会把自动滚动误判为手势，
    // 形成"自动滚动→置位→3 秒复位→再次自动滚动"的约 3 秒周期自激循环。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    holdAutoFollow = true
                    userTouchEpoch = System.currentTimeMillis()
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    userTouchEpoch = System.currentTimeMillis()
                }
            }
        }
    }

    // 惯性滚动持续期间顺延保持期：松手后 fling 还在走也算"用户接管"
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && holdAutoFollow) userTouchEpoch = System.currentTimeMillis()
        }
    }

    // 保持期计时：最后一次手势/滚动活动后 3.5s 归还控制权（Start 会重置 epoch 重启本协程）
    LaunchedEffect(userTouchEpoch) {
        if (userTouchEpoch == 0L) return@LaunchedEffect
        delay(3500)
        holdAutoFollow = false
    }

    // 最接近视口中线的可见行索引：拖动/惯性时驱动"逐行选中"
    val centerLineIndex by remember(lyrics) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) -1 else {
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                layoutInfo.visibleItemsInfo
                    .minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }
                    ?.index ?: -1
            }
        }
    }

    // 高亮行：用户接管期间选中"中线行"，否则跟随播放进度
    val activeIndex = if (holdAutoFollow && centerLineIndex >= 0) centerLineIndex else currentIndex

    // 切歌（歌词更换）后立即回到顶部，不再从旧位置长距离爬升
    LaunchedEffect(lyrics) {
        listState.scrollToItem(0)
    }

    // 自动跟随：key 任一变化即取消上一次动画（天然防重入）；先等惯性自然停止再平滑回正
    LaunchedEffect(currentIndex, holdAutoFollow, lyrics) {
        if (currentIndex < 0 || holdAutoFollow) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        if (!holdAutoFollow) {
            listState.animateScrollToItemCentered(currentIndex.coerceAtLeast(0))
        }
    }

    // 中线指向的歌词（跳转目标）；key 加 lyrics 防止切歌后闭包解析到上一首的行
    val targetLine = if (centerLineIndex >= 0) lyrics.getOrNull(centerLineIndex) else null

    BoxWithConstraints(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { onToggleLyricMode() } // 点击歌词空白区域切回封面
            )
        }
    ) {
        val verticalPadding = (maxHeight / 2 - 28.dp).coerceAtLeast(0.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = verticalPadding, bottom = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(
                lyrics,
                key = { index, line -> "$index:${line.timeMs}" },
            ) { index, line ->
                val isCurrent = index == activeIndex
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(line, holdAutoFollow) {
                            detectTapGestures(
                                onTap = {
                                    if (holdAutoFollow) {
                                        // 手势接管状态下：直接点击该句即可立即跳转播放
                                        onLyricClick(line)
                                        holdAutoFollow = false
                                    } else {
                                        // 正常播放中：单击切回封面
                                        onToggleLyricMode()
                                    }
                                },
                                onDoubleTap = {
                                    onLyricClick(line)
                                    holdAutoFollow = false
                                }
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        line.text,
                        style = when {
                            isCurrent -> MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp
                            )
                            index == activeIndex - 1 || index == activeIndex + 1 ->
                                MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)
                            else -> MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                        },
                        color = when {
                            isCurrent -> Color.White
                            index == activeIndex - 1 || index == activeIndex + 1 -> Color.White.copy(alpha = 0.55f)
                            else -> Color.White.copy(alpha = 0.28f)
                        },
                        textAlign = TextAlign.Start,
                    )
                    if (line.translated != null && line.translated != line.text) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            line.translated,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = if (isCurrent) 14.sp else 13.sp
                            ),
                            color = if (isCurrent) AmberLight.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.22f),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }

        // 仅在用户手动滚动/拖动歌词时展示对齐中线与右侧跳转胶囊
        // 正常播放时完全隐去，绝不遮挡歌词
        AnimatedVisibility(
            visible = holdAutoFollow && targetLine != null,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧极细虚线：指示所选中行的水平位置
                DottedLine(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                )

                Spacer(Modifier.width(12.dp))

                // 右侧跳转胶囊：时间戳 + 播放图标，置于屏幕最右侧，绝不遮挡左侧歌词文字
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.88f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .clickable {
                            targetLine?.let { line ->
                                onLyricClick(line)
                                holdAutoFollow = false
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = formatTime(targetLine?.timeMs ?: 0L),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "跳转至此句",
                        tint = AmberLight,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DottedLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomControls(
    state: PlayerState,
    positionMs: Long,
    durationMs: Long,
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
    // 松手后的目标值：保持显示直到轮询进度追上目标，避免滑块先弹回旧位置再跳回
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }

    // 轮询值到达目标附近（一个轮询周期 + 余量）后切回实时进度
    LaunchedEffect(pendingSeekMs, positionMs) {
        val target = pendingSeekMs ?: return@LaunchedEffect
        if (abs(positionMs - target) < 900) pendingSeekMs = null
    }
    // 切歌后立即丢弃未决的 seek 目标，防止残留到下一首。
    // 按歌曲身份键控而非队列索引：换队列/单曲队列替换时索引可能不变，
    // 残留的 seek 目标会把新歌直接跳到旧目标位置
    LaunchedEffect(state.currentSong?.platform, state.currentSong?.id) {
        pendingSeekMs = null
        isDragging = false
    }

    val currentPos = when {
        isDragging -> dragPositionMs.toLong()
        else -> pendingSeekMs ?: positionMs
    }

    val maxDuration = (durationMs.takeIf { it > 0 } ?: 1L).toFloat()
    val rawPos = when {
        isDragging -> dragPositionMs
        else -> (pendingSeekMs ?: positionMs).toFloat()
    }
    val safePos = if (rawPos.isNaN() || rawPos.isInfinite()) 0f else rawPos.coerceIn(0f, maxDuration)

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        Slider(
            value = safePos,
            onValueChange = {
                if (!it.isNaN() && !it.isInfinite()) {
                    isDragging = true
                    dragPositionMs = it.coerceIn(0f, maxDuration)
                }
            },
            onValueChangeFinished = {
                val target = dragPositionMs.toLong()
                onSeek(target)
                pendingSeekMs = target
                isDragging = false
            },
            valueRange = 0f..maxDuration,
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
            Text(formatTime(durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when (state.playMode) {
                    PlayMode.SEQUENCE -> Icons.Filled.Repeat // 顺序播放用线性图标，Shuffle 留给随机模式
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
                if (state.isLoading) {
                    // 歌曲加载中：此前点击后播放键纹丝不动，用户以为没点上而狂点
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = PlayerBg,
                    )
                } else {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = PlayerBg,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Icon(
                Icons.Filled.SkipNext, contentDescription = "下一首", tint = Color.White,
                modifier = Modifier.size(30.dp).clickable(onClick = onNext),
            )
            // 右下角按钮替换为播放队列
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
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
            .border(1.dp, Color.White.copy(alpha = if (active) 0.35f else 0.15f), RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
        Spacer(Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Color.White.copy(alpha = 0.9f))
    }
}

private fun formatRemain(ms: Long): String {
    val totalSec = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
}

@Composable
private fun BackgroundImage(artworkUrl: String?, theme: PlayerTheme) {
    if (!artworkUrl.isNullOrBlank()) {
        val context = LocalContext.current
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                // 让 Coil 直接解码 ~240px 小图再放大模糊：
                // 全屏 blur 的 GPU 开销与纹理尺寸成正比，视觉上 240px 放大后经 32dp 模糊无差别，
                // 低端机上可显著降低每帧渲染成本
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(240)
                    .build(),
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
    val hour = totalSec / 3600
    val min = (totalSec % 3600) / 60
    val sec = totalSec % 60
    // 超过 1 小时的进度此前显示成 "75:23"，分钟位溢出难读；有小时位时带出来
    return if (hour > 0) String.format(Locale.US, "%d:%02d:%02d", hour, min, sec)
    else String.format(Locale.US, "%d:%02d", min, sec)
}

/**
 * 把 index 对应行滚动到视口垂直居中（歌词自动滚动用）。
 *
 * 旧实现用 animateScrollToItem(index, -offset) 求滚动偏移，但该 API 已把
 * LazyColumn 的 beforeContentPadding（top 半屏留白）计入目标位置，再手动减半屏
 * 相当于把留白计了两次，当前行被推到视口底部——即"指示线在中、实际歌词在底部"。
 * 这里改成按"行中心相对视口顶部的实测距离"直接算差值 animateScrollBy，彻底摆脱
 * 偏移符号与 contentPadding 的耦合。
 */
private suspend fun LazyListState.animateScrollToItemCentered(index: Int) {
    val info = layoutInfo
    val viewportSize = info.viewportEndOffset - info.viewportStartOffset
    if (viewportSize <= 0) return
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item != null) {
        // item.offset 是相对视口顶部的距离；目标 = 行中心对齐视口中心
        val delta = (item.offset + item.size / 2 - viewportSize / 2).toFloat()
        animateScrollBy(delta)
    } else {
        // 目标行不在可见区（seek 跨度大 / 歌词极长）：先落到顶部附近再测距微调居中
        animateScrollToItem(index)
        val after = layoutInfo
        val target = after.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val vs = after.viewportEndOffset - after.viewportStartOffset
        if (vs <= 0) return
        animateScrollBy((target.offset + target.size / 2 - vs / 2).toFloat())
    }
}