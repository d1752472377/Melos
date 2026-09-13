package com.aesthetic.music.ui.fm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.model.Song

import com.aesthetic.music.player.LyricLine
import com.aesthetic.music.player.PlaybackProgress
import com.aesthetic.music.player.currentLyricIndex

import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.WaveBars
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.AccentRose
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.FmBackground
import com.aesthetic.music.ui.theme.FmScrimBottom
import com.aesthetic.music.ui.theme.FmScrimMid
import com.aesthetic.music.ui.theme.FmScrimTop
import com.aesthetic.music.ui.theme.FmSheetContainer
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.fmGlass
import com.aesthetic.music.ui.theme.fmOn
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 心动 FM 电台：全屏沉浸式拟物黑胶唱机播放页面。
 * 支持动态流光光晕背景、拟物黑胶同心圆声轨旋转、拟物唱针摆动动效、
 * 实时双行悬浮歌词与全屏逐字卡拉OK歌词无缝切换、多频道漫游切换、
 * 不感兴趣丢弃、心动收藏以及宝藏歌单导出。
 */
@Composable
fun FmScreen(
    fmQueue: List<Song>,
    fmIndex: Int,
    fmLoading: Boolean,
    fmError: String?,
    fmTreasure: List<Song>,
    currentChannel: FmChannel,
    channels: List<FmChannel>,
    favorites: Set<String>,
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    lyrics: List<LyricLine> = emptyList(),
    syncedLyrics: SyncedLyrics? = null,
    progressFlow: StateFlow<PlaybackProgress>,
    onSelectChannel: (FmChannel) -> Unit,
    onRefreshRecommendation: () -> Unit,
    onSelect: (Int) -> Unit,
    onToggleLike: (Song) -> Unit,
    onDislike: (Song) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onSeek: (Long) -> Unit,
    onSeekToLyric: (LyricLine) -> Unit,
    onExport: (List<Song>) -> Unit,
) {
    var showChannelSheet by remember { mutableStateOf(false) }
    var showTreasureSheet by remember { mutableStateOf(false) }
    var showFullLyrics by remember { mutableStateOf(false) }
    var syncingPager by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = fmIndex.coerceAtLeast(0),
        pageCount = { fmQueue.size.coerceAtLeast(1) }
    )

    // 播放引擎自动续播（一曲播毕自动切到下一曲）→ 同步 Pager 位置
    LaunchedEffect(currentSong?.id, fmQueue) {
        val activeId = currentSong?.id ?: return@LaunchedEffect
        val idx = fmQueue.indexOfFirst { it.id == activeId }
        if (idx >= 0 && idx != pagerState.currentPage) {
            syncingPager = true
            scope.launch {
                pagerState.animateScrollToPage(idx)
                syncingPager = false
            }
        }
    }

    // 用户在 Pager 上手动上下滑动切歌 → 通知外部播放新曲目
    LaunchedEffect(pagerState.currentPage, fmQueue) {
        if (syncingPager) return@LaunchedEffect
        if (fmQueue.isNotEmpty() && pagerState.currentPage != fmIndex) {
            onSelect(pagerState.currentPage)
        }
    }

    // 当前展示曲目（优先取 Pager 当前页对应的曲目，否则取 currentSong）
    val displaySong = fmQueue.getOrNull(pagerState.currentPage) ?: currentSong

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FmBackground)
    ) {
        // ===== 1. 动态沉浸背景层 =====
        displaySong?.artwork?.let { artUrl ->
            AsyncImage(
                model = artUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(64.dp)
                    .alpha(0.35f)
            )
        }
        // 径向与纵向暗角遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            FmScrimTop,
                            FmScrimMid,
                            FmScrimBottom
                        )
                    )
                )
        )

        // ===== 2. 主页面内容判定 =====
        when {
            // 加载中且列表尚为空
            fmQueue.isEmpty() && fmLoading -> {
                FmLoadingState(channel = currentChannel)
            }

            // 加载异常/空状态
            fmQueue.isEmpty() -> {
                FmEmptyState(
                    error = fmError,
                    onRetry = onRefreshRecommendation,
                    channel = currentChannel
                )
            }

            // 正常电台内容视图
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = 82.dp) // 避开底部悬浮 NavigationDock
                ) {
                    // ===== 顶部操作栏 =====
                    FmTopBar(
                        currentChannel = currentChannel,
                        isPlaying = isPlaying,
                        treasureCount = fmTreasure.size,
                        onOpenChannel = { showChannelSheet = true },
                        onOpenTreasure = { showTreasureSheet = true },
                        onRefresh = onRefreshRecommendation
                    )

                    // ===== 中间主内容区（黑胶唱机 / 竖滑页面 / 歌词模式） =====
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showFullLyrics && displaySong != null) {
                            // 全屏歌词模式
                            FmFullLyricsView(
                                song = displaySong,
                                lyrics = lyrics,
                                syncedLyrics = syncedLyrics,
                                progressFlow = progressFlow,
                                isPlaying = isPlaying,
                                onSeek = onSeek,
                                onSeekToLyric = onSeekToLyric,
                                onCloseLyrics = { showFullLyrics = false }
                            )
                        } else {
                            // 主竖滑 VerticalPager
                            VerticalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1
                            ) { page ->
                                val song = fmQueue.getOrNull(page)
                                if (song != null) {
                                    val isCurrentItem = (song.id == currentSong?.id)
                                    val isFav = favorites.contains("${song.platform}:${song.id}") || favorites.contains(song.id)

                                    FmTrackView(
                                        song = song,
                                        isCurrent = isCurrentItem,
                                        isPlaying = isPlaying && isCurrentItem,
                                        isFavorite = isFav,
                                        channelName = currentChannel.name,
                                        progressFlow = progressFlow,
                                        lyrics = if (isCurrentItem) lyrics else emptyList(),
                                        onToggleLyrics = { showFullLyrics = true }
                                    )
                                }
                            }
                        }
                    }

                    // ===== 底部控制控制台 =====
                    if (displaySong != null) {
                        val isFav = favorites.contains("${displaySong.platform}:${displaySong.id}") || favorites.contains(displaySong.id)

                        FmBottomControlBar(
                            song = displaySong,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering || fmLoading,
                            isFavorite = isFav,
                            currentIndex = pagerState.currentPage,
                            totalCount = fmQueue.size,
                            progressFlow = progressFlow,
                            showFullLyrics = showFullLyrics,
                            onTogglePlay = onPlayPause,
                            onToggleLike = { onToggleLike(displaySong) },
                            onDislike = { onDislike(displaySong) },
                            onNext = onNext,
                            onToggleLyrics = { showFullLyrics = !showFullLyrics },
                            onSeek = onSeek
                        )
                    }
                }
            }
        }

        // ===== 3. 弹层抽屉 =====
        if (showChannelSheet) {
            FmChannelSheet(
                channels = channels,
                selectedChannel = currentChannel,
                onSelect = { ch ->
                    onSelectChannel(ch)
                    showChannelSheet = false
                },
                onDismiss = { showChannelSheet = false }
            )
        }

        if (showTreasureSheet) {
            FmTreasureSheet(
                songs = fmTreasure,
                onExport = { onExport(fmTreasure) },
                onDismiss = { showTreasureSheet = false }
            )
        }
    }
}

/**
 * 顶部导航与频道选择胶囊
 */
@Composable
private fun FmTopBar(
    currentChannel: FmChannel,
    isPlaying: Boolean,
    treasureCount: Int,
    onOpenChannel: () -> Unit,
    onOpenTreasure: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：心动电台频道切换胶囊
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(fmGlass(0.2f))
                .border(0.8.dp, fmGlass(0.2f), RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenChannel)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = currentChannel.icon, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = currentChannel.name,
                color = fmOn(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            if (isPlaying) {
                WaveBars(color = AccentRose, barWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "切换频道",
                tint = fmOn(0.7f),
                modifier = Modifier.size(16.dp)
            )
        }

        // 右侧操作组：淘到的宝藏 + 重新调频刷新
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 宝藏歌单药丸
            if (treasureCount > 0) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AccentRose.copy(alpha = 0.25f))
                        .border(0.8.dp, AccentRose.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenTreasure)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎁 宝藏 $treasureCount",
                        color = fmOn(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 重新调频刷新按键
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(fmGlass(0.13f))
                    .clickable {
                        onRefresh()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "重新调频",
                    tint = fmOn(0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 单曲主展示区：包含拟物黑胶唱片、右上角唱针摆入摆出、歌名与双行即时悬浮歌词
 */
@Composable
private fun FmTrackView(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    channelName: String,
    progressFlow: StateFlow<PlaybackProgress>,
    lyrics: List<LyricLine>,
    onToggleLyrics: () -> Unit,
) {
    val progress by progressFlow.collectAsState()
    val lyricIdx = if (isCurrent) currentLyricIndex(lyrics, progress.positionMs) else -1
    val currentLine = lyrics.getOrNull(lyricIdx)?.text?.takeIf { it.isNotBlank() }
    val nextLine = lyrics.getOrNull(lyricIdx + 1)?.text?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ===== 拟物黑胶唱盘与唱针区域 =====
        FmTurntable(
            artworkUrl = song.artwork,
            isPlaying = isPlaying,
            onTapDisc = onToggleLyrics
        )

        Spacer(Modifier.height(30.dp))

        // ===== 歌曲基本信息 =====
        Text(
            text = song.title.ifBlank { "未知曲目" },
            color = fmOn(),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.88f)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "${song.artist.ifBlank { "未知歌手" }} · ${song.album.ifBlank { "单曲" }}",
            color = fmOn(0.65f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.88f)
        )

        Spacer(Modifier.height(10.dp))

        // 频道风格与品质标签
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(fmGlass(0.13f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$channelName · 心动推荐",
                    color = fmOn(0.75f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentRose.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "SQ 超清母带",
                    color = AccentRose,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ===== 双行即时歌词预览卡片（点击直接展开全屏歌词） =====
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(14.dp))
                .background(fmGlass(0.09f))
                .clickable(onClick = onToggleLyrics)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = currentLine ?: "♪ 心动旋律 流淌心间 ♪",
                    color = if (currentLine != null) fmOn() else fmOn(0.5f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (nextLine != null) {
                    Text(
                        text = nextLine,
                        color = fmOn(0.35f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 拟物黑胶唱机与唱针组件：
 * 1. 拟物同心圆声轨与金属反光条纹；
 * 2. 连续旋转并在暂停时精准保留角度；
 * 3. 右上角拟物黑胶唱针随播放状态自动平滑落盘/抬起！
 */
@Composable
private fun FmTurntable(
    artworkUrl: String?,
    isPlaying: Boolean,
    onTapDisc: () -> Unit,
) {
    // 旋转角度累积（暂停时不重置、不跳动）
    val discRotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var last = discRotation.value
            while (true) {
                val target = ((last / 360f).toInt() + 1) * 360f
                discRotation.animateTo(target, tween(24_000, easing = LinearEasing))
                last = target
            }
        }
    }

    // 唱针落针动效：播放时唱针转动落入唱盘（~24°），暂停时优雅抬回（0°）
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 24f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 120f),
        label = "tonearmAngle"
    )

    // 心动光晕呼吸动画
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(310.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTapDisc() })
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. 唱盘背后的心动微光光环
        Box(
            modifier = Modifier
                .size(290.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AccentRose.copy(alpha = if (isPlaying) pulseAlpha else 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 2. 黑胶唱片主体
        Box(
            modifier = Modifier
                .size(270.dp)
                .rotate(discRotation.value)
                .shadow(elevation = 18.dp, shape = CircleShape, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Color(0xFF101014))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // 绘制真实黑胶同心圆声轨 (Micro-grooves) 与金属光芒扇区
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.width / 2f

                // 同心圆声轨细线
                val radii = listOf(0.92f, 0.86f, 0.80f, 0.74f, 0.68f, 0.62f, 0.56f)
                for (rRatio in radii) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.045f),
                        radius = maxR * rRatio,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // 对角金属反光质感弧形
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.12f to Color.White.copy(alpha = 0.08f),
                        0.25f to Color.Transparent,
                        0.62f to Color.White.copy(alpha = 0.08f),
                        0.75f to Color.Transparent
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true
                )
            }

            // 唱片中心圆形贴纸与封面
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF26262E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // 唱机中心金属轴孔 (Spindle Hole)
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F0F12))
                        .border(1.2.dp, Color(0xFFD4AF37).copy(alpha = 0.75f), CircleShape)
                )
            }
        }

        // 3. 拟物右上角唱针 (Tonearm)
        FmTonearm(
            angle = tonearmAngle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = (-12).dp)
        )
    }
}

/**
 * 拟物黑胶唱针：金属转轴底座 + 弯角合金唱臂 + 唱头探针
 */
@Composable
private fun FmTonearm(
    angle: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 85.dp, height = 120.dp)
            .graphicsLayer {
                rotationZ = angle
                transformOrigin = TransformOrigin(0.85f, 0.12f)
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pivotX = size.width * 0.85f
            val pivotY = size.height * 0.12f

            // 转轴底座金属圆环
            drawCircle(
                color = Color(0xFF38383F),
                radius = 16.dp.toPx(),
                center = Offset(pivotX, pivotY)
            )
            drawCircle(
                color = Color(0xFF5E5E68),
                radius = 11.dp.toPx(),
                center = Offset(pivotX, pivotY)
            )
            drawCircle(
                color = Color(0xFF1E1E22),
                radius = 5.dp.toPx(),
                center = Offset(pivotX, pivotY)
            )

            // 唱臂主体（从转轴引向左下）
            val midX = pivotX - 28.dp.toPx()
            val midY = pivotY + 54.dp.toPx()
            val endX = midX - 22.dp.toPx()
            val endY = midY + 44.dp.toPx()

            // 第一段金属臂
            drawLine(
                color = Color(0xFFCCCCCC),
                start = Offset(pivotX, pivotY),
                end = Offset(midX, midY),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            // 第二段弯折至唱头
            drawLine(
                color = Color(0xFFCCCCCC),
                start = Offset(midX, midY),
                end = Offset(endX, endY),
                strokeWidth = 3.2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 唱头外壳 (Cartridge)
            drawRoundRect(
                color = Color(0xFFE53935),
                topLeft = Offset(endX - 8.dp.toPx(), endY - 2.dp.toPx()),
                size = Size(16.dp.toPx(), 22.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            // 针尖小亮点
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(endX, endY + 22.dp.toPx())
            )
        }
    }
}

/**
 * 底部交互控制台：含细线进度条、时间、不感兴趣(💔)、心动喜欢(❤️)、晶透播放键(⏯️)、下一曲(⏭️)、歌词切换(💬)
 */
@Composable
private fun FmBottomControlBar(
    song: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFavorite: Boolean,
    currentIndex: Int,
    totalCount: Int,
    progressFlow: StateFlow<PlaybackProgress>,
    showFullLyrics: Boolean,
    onTogglePlay: () -> Unit,
    onToggleLike: () -> Unit,
    onDislike: () -> Unit,
    onNext: () -> Unit,
    onToggleLyrics: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val progress by progressFlow.collectAsState()
    val pos = progress.positionMs
    val dur = progress.durationMs.coerceAtLeast(1L)
    val fraction = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 微型进度条与电台流曲目序号
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(pos),
                color = fmOn(0.5f),
                fontSize = 11.sp
            )
            Text(
                text = "心动调频中 · 第 ${currentIndex + 1} 首",
                color = fmOn(0.4f),
                fontSize = 10.sp
            )
            Text(
                text = formatDuration(dur),
                color = fmOn(0.5f),
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(4.dp))

        // 细线进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .pointerInput(dur) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek((ratio * dur).toLong())
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // 背景槽
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(fmOn(0.15f))
            )
            // 播放进度高亮条
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentRose.copy(alpha = 0.8f), AccentRose)
                        )
                    )
            )
        }

        Spacer(Modifier.height(14.dp))

        // 控制按键行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 不感兴趣 (Dislike / 垃圾桶)
            FmRoundButton(
                size = 46.dp,
                onClick = onDislike,
                description = "不感兴趣"
            ) {
                Icon(
                    imageVector = Icons.Filled.ThumbDown,
                    contentDescription = "不感兴趣",
                    tint = fmOn(0.65f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // 2. 心动喜欢 (Like / 红心)
            val likeScale = remember { Animatable(1f) }
            val scope = rememberCoroutineScope()
            FmRoundButton(
                size = 50.dp,
                onClick = {
                    scope.launch {
                        likeScale.animateTo(1.35f, spring(stiffness = 500f))
                        likeScale.animateTo(1.0f, spring(stiffness = 300f))
                    }
                    onToggleLike()
                },
                description = "喜欢"
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "喜欢",
                    tint = if (isFavorite) AccentRose else fmOn(0.8f),
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = likeScale.value
                            scaleY = likeScale.value
                        }
                )
            }

            // 3. 晶透播放/暂停按键 (突出大圆键)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        spotColor = if (isPlaying) AccentRose.copy(alpha = 0.5f) else Color.Black
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(fmOn(0.28f), fmOn(0.12f))
                        )
                    )
                    .border(1.5.dp, fmOn(0.45f), CircleShape)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = fmOn()
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = fmOn(),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // 4. 下一首切歌 (Skip Next)
            FmRoundButton(
                size = 50.dp,
                onClick = onNext,
                description = "下一首"
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "下一首",
                    tint = fmOn(),
                    modifier = Modifier.size(26.dp)
                )
            }

            // 5. 歌词模式切换按键
            FmRoundButton(
                size = 46.dp,
                onClick = onToggleLyrics,
                description = "歌词"
            ) {
                Icon(
                    imageVector = if (showFullLyrics) Icons.Filled.Album else Icons.Filled.Lyrics,
                    contentDescription = if (showFullLyrics) "唱片模式" else "歌词模式",
                    tint = if (showFullLyrics) AccentRose else fmOn(0.7f),
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

/**
 * 全屏卡拉OK逐字/逐行动态歌词视图
 */
@Composable
private fun FmFullLyricsView(
    song: Song,
    lyrics: List<LyricLine>,
    syncedLyrics: SyncedLyrics?,
    progressFlow: StateFlow<PlaybackProgress>,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onSeekToLyric: (LyricLine) -> Unit,
    onCloseLyrics: () -> Unit,
) {
    val progress by progressFlow.collectAsState()
    val animatedPos = remember { mutableLongStateOf(0L) }
    val listState = rememberLazyListState()

    // 帧级毫秒平滑推移
    LaunchedEffect(isPlaying, progress.positionMs) {
        animatedPos.longValue = progress.positionMs
        if (isPlaying) {
            val baseTime = System.currentTimeMillis()
            val basePos = progress.positionMs
            while (isActive) {
                awaitFrame()
                val elapsed = System.currentTimeMillis() - baseTime
                animatedPos.longValue = (basePos + elapsed).coerceAtMost(progress.durationMs)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onCloseLyrics() })
            }
    ) {
        if (syncedLyrics != null && syncedLyrics.lines.isNotEmpty()) {
            KaraokeLyricsView(
                listState = listState,
                lyrics = syncedLyrics,
                currentPosition = { animatedPos.longValue.toInt() },
                onLineClicked = { line -> onSeek(line.start.toLong()) },
                onLinePressed = { line -> onSeek(line.start.toLong()) },
                showTranslation = true,
                showPhonetic = false,
                textColor = fmOn(),
                normalLineTextStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                accompanimentLineTextStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                ),
                useBlurEffect = true,
                modifier = Modifier.fillMaxSize()
            )
        } else if (lyrics.isNotEmpty()) {
            val activeIdx = currentLyricIndex(lyrics, progress.positionMs)
            LaunchedEffect(activeIdx) {
                if (activeIdx >= 0) {
                    listState.animateScrollToItem((activeIdx - 2).coerceAtLeast(0))
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(lyrics.size) { idx ->
                    val line = lyrics[idx]
                    val isCurrent = (idx == activeIdx)
                    Text(
                        text = line.text,
                        color = if (isCurrent) fmOn() else fmOn(0.4f),
                        fontSize = if (isCurrent) 20.sp else 16.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeekToLyric(line) }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无逐字歌词 · 随旋律漫游",
                    color = fmOn(0.5f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 浮动返回黑胶标签
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(999.dp))
                .background(fmGlass(0.2f))
                .clickable(onClick = onCloseLyrics)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Album,
                contentDescription = null,
                tint = fmOn(),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "唱片模式",
                color = fmOn(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 频道选择抽屉：网罗私人心动、新歌速递、热门流行、民谣、摇滚、电音、治愈等 11 款精选频道
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FmChannelSheet(
    channels: List<FmChannel>,
    selectedChannel: FmChannel,
    onSelect: (FmChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = FmSheetContainer,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "选择心动电台频道",
                        color = fmOn(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "不同频道，不同的心动频率",
                        color = fmOn(0.5f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(fmGlass(0.13f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = fmOn(),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                items(channels) { ch ->
                    val isSelected = (ch.id == selectedChannel.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) AccentRose.copy(alpha = 0.22f) else fmGlass(0.09f)
                            )
                            .border(
                                width = if (isSelected) 1.2.dp else 0.5.dp,
                                color = if (isSelected) AccentRose else fmOn(0.1f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelect(ch) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = ch.icon, fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = ch.name,
                                color = if (isSelected) AccentRose else fmOn(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = ch.subtitle,
                                color = fmOn(0.45f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 宝藏歌单抽屉：列出本次会话听到的心动好歌，支持一键分享与导出
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FmTreasureSheet(
    songs: List<Song>,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = FmSheetContainer,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "本次淘到的宝藏歌单",
                        color = fmOn(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "共 ${songs.size} 首 · 听歌点赞已自动加入「我喜欢的音乐」",
                        color = fmOn(0.5f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(fmGlass(0.13f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = fmOn(),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(fmGlass(0.06f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SongCover(
                            url = song.artwork,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = fmOn(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                color = fmOn(0.5f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "一键导出歌单文本", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 通用磨砂毛玻璃圆形按钮
 */
@Composable
private fun FmRoundButton(
    size: Dp = 44.dp,
    onClick: () -> Unit,
    description: String = "",
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(fmGlass(0.14f))
            .border(0.8.dp, fmOn(0.2f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 加载中状态
 */
@Composable
private fun FmLoadingState(channel: FmChannel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val rotateTransition = rememberInfiniteTransition(label = "loadingRotate")
        val rotation by rotateTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing)
            ),
            label = "rot"
        )

        Box(
            modifier = Modifier
                .size(120.dp)
                .rotate(rotation)
                .clip(CircleShape)
                .background(fmGlass(0.09f))
                .border(2.dp, AccentRose.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = channel.icon, fontSize = 42.sp)
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "正在为你在「${channel.name}」寻觅心动旋律…",
            color = fmOn(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "私人算法实时调频中，马上就来",
            color = fmOn(0.5f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * 空状态 / 异常重试
 */
@Composable
private fun FmEmptyState(
    error: String?,
    onRetry: () -> Unit,
    channel: FmChannel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = channel.icon, fontSize = 54.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "电台调频遇到了一点小波折",
            color = fmOn(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                color = fmOn(0.5f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(AccentRose)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "重新调频 / 换换口味",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 格式化时间 00:00
 */
private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}