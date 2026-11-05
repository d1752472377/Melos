package com.aesthetic.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.settings.PlayerTheme
import com.aesthetic.music.data.settings.LyricFontSize
import com.aesthetic.music.data.settings.LyricBlurMode
import com.aesthetic.music.data.settings.BackgroundBlurMode
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.player.LyricLine
import com.aesthetic.music.player.PlayMode
import com.aesthetic.music.player.PlaybackProgress
import com.aesthetic.music.player.PlayerState
import com.aesthetic.music.player.currentLyricIndex
import com.aesthetic.music.ui.theme.AmberLight
import com.aesthetic.music.ui.theme.PlayerBg
import com.aesthetic.music.ui.theme.PlayerBgDark
import com.aesthetic.music.ui.components.KeepScreenOn
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.player.lyrics.MelosLyricsView
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    /** 高频进度流：在播放页内部订阅，避免进度更新向上扩散到 AppShell */
    progressFlow: StateFlow<PlaybackProgress>,
    /** 某首歌是否已收藏。传函数而不是布尔量：队列里每一行都要判断，不能只判断当前曲 */
    isFavorite: (Song) -> Boolean,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekToLyric: (LyricLine) -> Unit,
    onTogglePlayMode: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveQueueItem: (from: Int, to: Int) -> Unit,
    onClearQueue: () -> Unit,
    /** 歌词为空且没在加载时补拉一次（修复「首曲没歌词、切歌才有」） */
    onEnsureLyrics: () -> Unit,
    /** 用户主动点「重新加载」 */
    onReloadLyrics: () -> Unit,
    /** 当前音质与切换回调（音质是可选项，见中控台「音质」板块） */
    currentQuality: QualityLevel,
    onQualityChange: (QualityLevel) -> Unit,
    currentPlayerTheme: PlayerTheme,
    onPlayerThemeChange: (PlayerTheme) -> Unit,
    currentLyricFontSize: LyricFontSize,
    onLyricFontSizeChange: (LyricFontSize) -> Unit,
    currentLyricBlurMode: LyricBlurMode,
    onLyricBlurModeChange: (LyricBlurMode) -> Unit,
    currentBgBlurMode: BackgroundBlurMode,
    onBgBlurModeChange: (BackgroundBlurMode) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onArtistClick: ((artistName: String, artistId: String?, platform: String?) -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    hazeState: HazeState? = null,
) {
    val song = state.currentSong
    // 进度只在播放页内部收集：每 500ms 一次的更新不会离开本页作用域
    val progress by progressFlow.collectAsState()
    val animatedPositionState = remember { mutableLongStateOf(0L) }

    // 切歌即确保歌词在途：首曲的歌词请求常常和冷启动抢网络，失败后此前就永久空白了
    LaunchedEffect(song?.platform, song?.id) {
        if (song != null) onEnsureLyrics()
    }

    // 帧级平滑插值：逐帧刷新当前毫秒数，供歌词视图绘制平滑字级染色与滚动
    LaunchedEffect(state.isPlaying, progress.positionMs, state.speed) {
        animatedPositionState.longValue = progress.positionMs
        if (state.isPlaying) {
            val baseWallTime = System.currentTimeMillis()
            val basePos = progress.positionMs
            while (isActive) {
                awaitFrame()
                val elapsed = ((System.currentTimeMillis() - baseWallTime) * state.speed).toLong()
                val newPos = (basePos + elapsed).coerceAtMost(
                    progress.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
                )
                animatedPositionState.longValue = newPos
            }
        }
    }

    var showLyric by remember { mutableStateOf(true) }

    // 歌词页常亮：歌词是「看着唱 / 躺着看滚动」的场景，屏幕一熄就得抬手点一下。
    // 只在歌词模式生效（封面模式不需要），离开页面或切回封面时自动复原。
    KeepScreenOn(enabled = showLyric)
    var showQueueSheet by remember { mutableStateOf(false) }
    var showControlCenterSheet by remember { mutableStateOf(false) }
    var showAudioDetailSheet by remember { mutableStateOf(false) }

    // 主题不变时缓存渐变画刷
    val bgGradient: Brush = remember(currentPlayerTheme) {
        when (currentPlayerTheme) {
            PlayerTheme.CLASSIC -> Brush.verticalGradient(listOf(PlayerBg, PlayerBgDark))
            PlayerTheme.COVER -> Brush.verticalGradient(listOf(Color(0xFF5C2612), Color(0xFF1F0D06)))
            PlayerTheme.APPLE -> Brush.verticalGradient(
                listOf(Color(0xFF311B92), Color(0xFF880E4F), Color(0xFF1A237E))
            )
            PlayerTheme.RECORD_SHELF -> Brush.verticalGradient(
                listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617))
            )
            PlayerTheme.LUMINOUS_CD -> Brush.verticalGradient(
                listOf(Color(0xFF334155), Color(0xFF1E293B), Color(0xFF0F172A))
            )
        }
    }

    // ───────────────────────────────────────────────
    // 铁律一：不再全屏消费 pointer 事件。
    // 透传防护交给内容区自身的 clickable；背景层用一个只吞 tap 的点击层兜底。
    // ───────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        BackgroundImage(
            artworkUrl = song?.artwork,
            theme = currentPlayerTheme,
            bgBlurDp = currentBgBlurMode.blurDp,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient, alpha = if (currentPlayerTheme == PlayerTheme.CLASSIC) 0.85f else 0.72f)
        )

        // 兜底层：只吞 tap，不碰任何 move 事件
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* 空白点击，吞掉防透传 */ }
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            TopBar(
                onClose = onClose,
                onOpenControlCenter = { showControlCenterSheet = true },
                onOpenAudioDetail = { showAudioDetailSheet = true },
                audioFormatTag = state.audioSpec.format,
                speed = state.speed,
                sleepRemainingMs = state.sleepRemainingMs,
            )
            Spacer(Modifier.height(10.dp))

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
                    Spacer(Modifier.height(3.dp))
                    val artistDisplayName = song?.artist?.ifBlank { "未知歌手" } ?: "未知歌手"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (song != null && onArtistClick != null) {
                                        onArtistClick(
                                            song.artist,
                                            song.artistId,
                                            song.artistPlatform ?: song.platform
                                        )
                                    }
                                }
                            )
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = artistDisplayName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                        if (!song?.album.isNullOrBlank()) {
                            Text(
                                text = " · ${song.album}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = "查看歌手",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
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
                            imageVector = if (showLyric) Icons.Filled.Album else Icons.Filled.Lyrics,
                            contentDescription = if (showLyric) "切换封面" else "切换歌词",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    FavoriteBurstButton(
                        isFavorite = song != null && isFavorite(song),
                        onToggle = { song?.let(onToggleFavorite) },
                        iconSize = 20.dp,
                        activeColor = Rose,
                        inactiveColor = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 主内容区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showLyric) {
                    MelosLyricsView(
                        lyrics = state.lyrics,
                        activeIndexProvider = {
                            currentLyricIndex(state.lyrics, animatedPositionState.longValue)
                        },
                        currentTimeMsProvider = { animatedPositionState.longValue },
                        onSeek = onSeek,
                        fontSizeSp = currentLyricFontSize.spSize,
                        showTranslation = true,
                        isLoading = state.lyricsLoading,
                        onRetryLyric = onReloadLyrics,
                        onToggleLyricMode = { showLyric = false },
                        onLineLongClick = { showAudioDetailSheet = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AnimatedCoverBox(
                        artworkUrl = song?.artwork,
                        theme = currentPlayerTheme,
                        isPlaying = state.isPlaying,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onToggleLyricMode = { showLyric = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

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
        PlayerQueueSheet(
            queue = state.queue,
            currentIndex = state.currentIndex,
            isFavorite = isFavorite,
            onDismiss = { showQueueSheet = false },
            onPlayIndex = { index ->
                onPlayQueueItem(index)
                showQueueSheet = false
            },
            onRemoveIndex = onRemoveFromQueue,
            onMove = onMoveQueueItem,
            onClear = {
                onClearQueue()
                // 清空后队列没了，抽屉留在屏幕上只会是一张空卡片，直接收起
                showQueueSheet = false
            },
            onToggleFavorite = onToggleFavorite,
            onAddToPlaylist = { s -> onAddToPlaylist?.invoke(s) },
        )
    }

    if (showControlCenterSheet) {
        PlayerControlCenterSheet(
            onDismissRequest = { showControlCenterSheet = false },
            song = song,
            showLyric = showLyric,
            onToggleLyric = { showLyric = !showLyric },
            currentSpeed = state.speed,
            onSpeedChange = { speed -> onSpeedChange(speed) },
            sleepRemainingMs = state.sleepRemainingMs,
            onSleepTimer = { minutes -> onSleepTimer(minutes) },
            onCancelSleepTimer = { onCancelSleepTimer() },
            currentTheme = currentPlayerTheme,
            onThemeSelect = { theme -> onPlayerThemeChange(theme) },
            playMode = state.playMode,
            onTogglePlayMode = onTogglePlayMode,
            onDownload = {
                onDownload?.invoke()
                showControlCenterSheet = false
            },
            onAddToPlaylist = {
                song?.let { onAddToPlaylist?.invoke(it) }
                showControlCenterSheet = false
            },
            currentQuality = currentQuality,
            onQualityChange = onQualityChange,
            currentLyricFontSize = currentLyricFontSize,
            onLyricFontSizeChange = { onLyricFontSizeChange(it) },
            currentLyricBlurMode = currentLyricBlurMode,
            onLyricBlurModeChange = { onLyricBlurModeChange(it) },
            currentBgBlurMode = currentBgBlurMode,
            onBgBlurModeChange = { onBgBlurModeChange(it) },
            hazeState = hazeState,
        )
    }

    if (showAudioDetailSheet) {
        SongAudioDetailSheet(
            onDismissRequest = { showAudioDetailSheet = false },
            song = song,
            audioSpec = state.audioSpec,
            lyricSpec = state.lyricSpec,
            hazeState = hazeState,
        )
    }
}

/**
 * 带有手势跟手与单击切换模式的封面 Box
 */
@Composable
private fun AnimatedCoverBox(
    artworkUrl: String?,
    theme: PlayerTheme = PlayerTheme.CLASSIC,
    isPlaying: Boolean = false,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleLyricMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val swipeThreshold = with(LocalDensity.current) { 100.dp.toPx() }
    var containerWidthPx by remember { mutableFloatStateOf(1000f) }

    // 黑胶持续旋转动效
    val vinylRotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                vinylRotation.animateTo(
                    targetValue = vinylRotation.value + 360f,
                    animationSpec = tween(durationMillis = 24000, easing = LinearEasing)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(artworkUrl) {
                detectTapGestures(onTap = { onToggleLyricMode() })
            }
            .pointerInput(artworkUrl) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
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
                    val x = offsetX.value
                    val progress = (abs(x) / swipeThreshold).coerceIn(0f, 1f)
                    translationX = x
                    rotationZ = (x / 25f).coerceIn(-12f, 12f)
                    alpha = 1f - progress * 0.25f
                },
            contentAlignment = Alignment.Center,
        ) {
            when (theme) {
                PlayerTheme.RECORD_SHELF -> {
                    RecordShelfCover(
                        artworkUrl = artworkUrl,
                        isPlaying = isPlaying,
                        vinylRotation = vinylRotation.value % 360f,
                    )
                }
                PlayerTheme.LUMINOUS_CD -> {
                    LuminousCdCover(
                        artworkUrl = artworkUrl,
                        isPlaying = isPlaying,
                        cdRotation = vinylRotation.value % 360f,
                    )
                }
                PlayerTheme.COVER -> {
                    Box(
                        modifier = Modifier
                            .size(285.dp)
                            .graphicsLayer { rotationZ = vinylRotation.value % 360f }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF222226),
                                        Color(0xFF141416),
                                        Color(0xFF1F1F23),
                                        Color(0xFF101012),
                                        Color(0xFF26262B),
                                        Color(0xFF0C0C0D)
                                    )
                                )
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerOffset = Offset(size.width / 2, size.height / 2)
                            val maxRadius = size.width / 2
                            val grooveRadii = listOf(0.92f, 0.84f, 0.76f, 0.68f)
                            grooveRadii.forEach { factor ->
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.045f),
                                    radius = maxRadius * factor,
                                    center = centerOffset,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                )
                            }
                        }

                        if (!artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = artworkUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(175.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color(0xFF111113), CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(175.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A1C16)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Melos", color = AmberLight.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0xFFD4AF37), Color(0xFF8A6D1C), Color(0xFF261D09))
                                    )
                                )
                                .border(1.dp, AmberLight.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF09090A))
                            )
                        }
                    }
                }
                else -> {
                if (theme == PlayerTheme.APPLE && !artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(280.dp)
                            .blur(24.dp)
                            .graphicsLayer { alpha = 0.5f },
                        contentScale = ContentScale.Crop,
                    )
                }

                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(280.dp)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.25f),
                                        Color.White.copy(alpha = 0.08f)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier
                            .size(280.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("暂无封面", color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
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
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(pendingSeekMs, positionMs) {
        val target = pendingSeekMs ?: return@LaunchedEffect
        if (abs(positionMs - target) < 900) pendingSeekMs = null
    }
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
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isDragging) 12.dp else 8.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(3.dp),
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
            Text(
                formatTime(currentPos),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f)
            )
            Text(
                formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when (state.playMode) {
                    PlayMode.SEQUENCE -> Icons.Filled.Repeat
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
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

private fun formatRemain(ms: Long): String {
    val totalSec = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
}

@Composable
private fun BackgroundImage(artworkUrl: String?, theme: PlayerTheme, bgBlurDp: Int = 40) {
    if (!artworkUrl.isNullOrBlank()) {
        val context = LocalContext.current
        Box(Modifier.fillMaxSize()) {
            when (theme) {
                PlayerTheme.APPLE -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(artworkUrl).size(240).build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur((bgBlurDp * 1.2f).toInt().dp)
                            .graphicsLayer {
                                scaleX = 1.3f
                                scaleY = 1.3f
                            },
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0x953B187B), Color.Transparent),
                                    center = Offset(120f, 320f),
                                    radius = 850f,
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0x859D174D), Color.Transparent),
                                    center = Offset(900f, 1300f),
                                    radius = 950f,
                                )
                            )
                    )
                }
                PlayerTheme.COVER -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(artworkUrl).size(240).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blur(bgBlurDp.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x555C2612),
                                        Color(0xD51F0D06),
                                        Color(0xFA100603)
                                    ),
                                    radius = 1200f,
                                )
                            )
                    )
                }
                PlayerTheme.RECORD_SHELF -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(artworkUrl).size(240).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blur(bgBlurDp.dp).graphicsLayer { alpha = 0.5f },
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x880F172A),
                                        Color(0xEE020617)
                                    )
                                )
                            )
                    )
                }
                PlayerTheme.LUMINOUS_CD -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(artworkUrl).size(240).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blur(bgBlurDp.dp).graphicsLayer { alpha = 0.45f },
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x66475569),
                                        Color(0xEE0F172A)
                                    ),
                                    radius = 1100f
                                )
                            )
                    )
                }
                PlayerTheme.CLASSIC -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(artworkUrl).size(200).build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(bgBlurDp.dp)
                            .graphicsLayer { alpha = 0.35f },
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xEA0E1015),
                                        Color(0xF508090C),
                                        Color(0xFF000000)
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    onClose: () -> Unit,
    onOpenControlCenter: () -> Unit,
    onOpenAudioDetail: () -> Unit,
    audioFormatTag: String,
    speed: Float,
    sleepRemainingMs: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerCircleButton(Icons.Filled.KeyboardArrowDown, onClick = onClose)

        Text(
            "正在播放",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 2.sp,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            val displayTag =
                if (audioFormatTag.isNotBlank() && audioFormatTag != "—") audioFormatTag else "规格"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenAudioDetail)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = "音频技术规格",
                        tint = AmberLight,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = displayTag,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))

            if (speed != 1f) {
                PlayerChip(
                    icon = Icons.Filled.Speed,
                    text = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x",
                    active = true,
                    onClick = onOpenControlCenter,
                )
                Spacer(Modifier.width(6.dp))
            }
            if (sleepRemainingMs > 0) {
                PlayerChip(
                    icon = Icons.Filled.Bedtime,
                    text = formatRemain(sleepRemainingMs),
                    active = true,
                    onClick = onOpenControlCenter,
                )
                Spacer(Modifier.width(6.dp))
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenControlCenter)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = "打开中控台",
                        tint = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "中控",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = Color.White.copy(alpha = 0.95f),
                    )
                }
            }
        }
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
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(22.dp)
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val hour = totalSec / 3600
    val min = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (hour > 0) String.format(Locale.US, "%d:%02d:%02d", hour, min, sec)
    else String.format(Locale.US, "%d:%02d", min, sec)
}
