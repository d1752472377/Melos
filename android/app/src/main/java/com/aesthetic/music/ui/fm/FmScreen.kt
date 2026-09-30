package com.aesthetic.music.ui.fm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.aesthetic.music.player.currentLyricIndex
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.aesthetic.music.ui.components.PlaylistEditorResult
import com.aesthetic.music.ui.components.PlaylistEditorSheet
import com.aesthetic.music.ui.components.WaveBars
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.AccentRose
import com.aesthetic.music.ui.theme.FmBackground
import com.aesthetic.music.ui.theme.FmCardBg
import com.aesthetic.music.ui.theme.FmCardBorder
import com.aesthetic.music.ui.theme.FmOnSolid
import com.aesthetic.music.ui.theme.FmScrimBottom
import com.aesthetic.music.ui.theme.FmScrimMid
import com.aesthetic.music.ui.theme.FmScrimTop
import com.aesthetic.music.ui.theme.FmSheetCardBg
import com.aesthetic.music.ui.theme.FmSheetCardBorder
import com.aesthetic.music.ui.theme.FmSheetContainer
import com.aesthetic.music.ui.theme.FmSolidFill
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.Surface as ThemeSurface
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.fmGlass
import com.aesthetic.music.ui.theme.fmOn
import com.aesthetic.music.ui.player.lyrics.MelosLyricsView
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekToLyric: (LyricLine) -> Unit,
    onExportToPlaylist: (PlaylistEditorResult) -> Unit,
) {
    var showChannelSheet by remember { mutableStateOf(false) }
    var showTreasureSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
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
                        .padding(bottom = 88.dp) // 底部避开悬浮 NavigationDock，并留一点间隙
                ) {
                    // ===== 顶部操作栏（左侧频道卡片 / 右侧宝藏） =====
                    FmTopBar(
                        currentChannel = currentChannel,
                        isPlaying = isPlaying,
                        treasureCount = fmTreasure.size,
                        onOpenChannel = { showChannelSheet = true },
                        onOpenTreasure = { showTreasureSheet = true }
                    )

                    // ===== 中间主内容区（黑胶唱机 / 竖滑页面 / 歌词模式） =====
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showFullLyrics && displaySong != null) {
                            // 全屏歌词模式（点唱片进入，点歌词区退出）
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
                            // 主竖滑 VerticalPager：每页只有一张唱片
                            VerticalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1
                            ) { page ->
                                val song = fmQueue.getOrNull(page)
                                if (song != null) {
                                    val isCurrentItem = (song.id == currentSong?.id)

                                    FmTurntable(
                                        artworkUrl = song.artwork,
                                        isPlaying = isPlaying && isCurrentItem,
                                        onTapDisc = { showFullLyrics = true }
                                    )
                                }
                            }
                        }
                    }

                    // ===== 唱片下方信息区：歌名 → 歌手 → 进度条 → 播放控制 → 两张动作卡 =====
                    if (displaySong != null) {
                        val isFav = favorites.contains("${displaySong.platform}:${displaySong.id}") || favorites.contains(displaySong.id)

                        FmInfoPanel(
                            song = displaySong,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering || fmLoading,
                            isFavorite = isFav,
                            progressFlow = progressFlow,
                            onPrevious = onPrevious,
                            onTogglePlay = onPlayPause,
                            onNext = onNext,
                            onDislike = { onDislike(displaySong) },
                            onToggleLike = { onToggleLike(displaySong) },
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
                onExportToPlaylist = { showExportSheet = true },
                onDismiss = { showTreasureSheet = false }
            )
        }

        // 一键导出到歌单：复用歌单页的新建歌单表单，创建成功后把宝藏歌曲整体放进去
        if (showExportSheet) {
            PlaylistEditorSheet(
                title = "新建歌单",
                confirmText = "创建歌单",
                showPlatformImport = false,
                onDismiss = { showExportSheet = false },
                onConfirm = { result ->
                    showExportSheet = false
                    onExportToPlaylist(result)
                },
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：频道卡片（白色玻璃底、无描边，点一下下拉换频道）
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(FmCardBg)
                .border(0.8.dp, FmCardBorder, RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenChannel)
                .padding(horizontal = 14.dp, vertical = 8.dp),
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

        // 右侧：淘到的宝藏（刷新入口已移除，需要换口味时走空状态的「重新调频」）
        if (treasureCount > 0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(AccentRose.copy(alpha = 0.25f))
                    .border(0.8.dp, AccentRose.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenTreasure)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
    }
}

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

            // 唱片中心圆形贴纸与封面（放大封面，让唱片中心的主体更醒目）
            Box(
                modifier = Modifier
                    .size(184.dp)
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
                            modifier = Modifier.size(48.dp)
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
    }
}

@Composable
private fun FmInfoPanel(
    song: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFavorite: Boolean,
    progressFlow: StateFlow<PlaybackProgress>,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onDislike: () -> Unit,
    onToggleLike: () -> Unit,
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
        // 歌名
        Text(
            text = song.title.ifBlank { "未知曲目" },
            color = fmOn(),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        // 歌手名
        Text(
            text = "${song.artist.ifBlank { "未知歌手" }} · ${song.album.ifBlank { "单曲" }}",
            color = fmOn(0.65f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        // 细线进度条（点击定位）
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(fmOn(0.15f))
            )
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

        Spacer(Modifier.height(16.dp))

        // 播放控制：上一首（小） / 播放暂停（大） / 下一首（小）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            FmSolidButton(size = 48.dp, onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "上一首",
                    tint = FmOnSolid,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(26.dp))
            FmSolidButton(size = 68.dp, onClick = onTogglePlay) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = FmOnSolid
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = FmOnSolid,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.width(26.dp))
            FmSolidButton(size = 48.dp, onClick = onNext) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "下一首",
                    tint = FmOnSolid,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // 两张大圆角动作卡：不合口味 / 收藏这首
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FmActionCard(
                text = "不合口味",
                icon = Icons.Filled.ThumbDown,
                tint = fmOn(0.75f),
                onClick = onDislike,
                modifier = Modifier.weight(1f)
            )
            FmActionCard(
                text = "收藏这首",
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (isFavorite) AccentRose else fmOn(0.75f),
                onClick = onToggleLike,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 实心圆播放控制按钮：浅色纯黑、深色纯白 */
@Composable
private fun FmSolidButton(
    size: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(FmSolidFill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** 大圆角动作卡（玻璃底）：图标 + 文案 */
@Composable
private fun FmActionCard(
    text: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(FmCardBg)
            .border(0.8.dp, FmCardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


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
    ) {
        if (lyrics.isNotEmpty()) {
            MelosLyricsView(
                lyrics = lyrics,
                activeIndexProvider = {
                    currentLyricIndex(lyrics, animatedPos.longValue)
                },
                onSeek = onSeek,
                fontSizeSp = 22,
                showTranslation = true,
                onToggleLyricMode = onCloseLyrics,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无歌词 · 随旋律漫游",
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
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.72f).dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
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

            // 两列卡片（用普通 Column 排，不再嵌套 Lazy 网格：
            // 嵌套滚动会让抽屉底部内容被裁掉且必须上滑才能看到）
            channels.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { ch ->
                        FmChannelCard(
                            channel = ch,
                            isSelected = ch.id == selectedChannel.id,
                            onClick = { onSelect(ch) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 奇数个时补一个占位，保证最后一个卡片不会独占整行
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun FmChannelCard(
    channel: FmChannel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) AccentRose.copy(alpha = 0.22f) else FmSheetCardBg
            )
            .border(
                width = if (isSelected) 1.2.dp else 0.5.dp,
                color = if (isSelected) AccentRose else FmSheetCardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = channel.icon, fontSize = 24.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = if (isSelected) AccentRose else fmOn(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = channel.subtitle,
                color = fmOn(0.45f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 宝藏歌单抽屉：本次会话听到的心动好歌。
 *
 * 固定高度，顶部标题固定；下面是一整张贴边的大卡片（左右/下都到屏幕边缘），
 * 卡片内是歌曲列表（歌曲之间一条分割线），卡片底部固定「一键导出到歌单」按钮。
 * 列表单独滚动，标题与按钮不动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FmTreasureSheet(
    songs: List<Song>,
    onExportToPlaylist: () -> Unit,
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
                .height(460.dp)
        ) {
            // ===== 顶部标题行（固定） =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
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

            // ===== 大卡片：左右/下贴边，内部列表滚动 =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(ThemeSurface)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    itemsIndexed(songs) { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = song.artist,
                                    color = TextSoft,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (index != songs.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                                thickness = 0.5.dp,
                                color = Stone200.copy(alpha = 0.55f)
                            )
                        }
                    }
                }

                // ===== 底部按钮（固定在卡片内） =====
                Button(
                    onClick = onExportToPlaylist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = "一键导出到歌单", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


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