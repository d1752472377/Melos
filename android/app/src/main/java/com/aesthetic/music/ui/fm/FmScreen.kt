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
import android.net.Uri
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

import com.aesthetic.music.data.settings.PlaylistCoverPick
import com.aesthetic.music.player.LyricLine
import com.aesthetic.music.player.PlaybackProgress
import com.aesthetic.music.player.currentLyricIndex

import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.playlist.PlaylistEditorResult
import com.aesthetic.music.ui.playlist.PlaylistEditorSheet
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
import com.aesthetic.music.ui.components.KeepScreenOn
import com.aesthetic.music.ui.player.lyrics.MelosLyricsView
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

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
    lyricsLoading: Boolean = false,
    onReloadLyrics: (() -> Unit)? = null,
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
    onPickCover: (Uri) -> PlaylistCoverPick? = { null },
    onDeleteCover: (String?) -> Unit = {},
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

    LaunchedEffect(pagerState.currentPage, fmQueue) {
        if (syncingPager) return@LaunchedEffect
        if (fmQueue.isNotEmpty() && pagerState.currentPage != fmIndex) {
            onSelect(pagerState.currentPage)
        }
    }

    val displaySong = fmQueue.getOrNull(pagerState.currentPage) ?: currentSong

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FmBackground)
    ) {
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

        when {
            fmQueue.isEmpty() && fmLoading -> {
                FmLoadingState(channel = currentChannel)
            }

            fmQueue.isEmpty() -> {
                FmEmptyState(
                    error = fmError,
                    onRetry = onRefreshRecommendation,
                    channel = currentChannel
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = 88.dp)
                ) {
                    FmTopBar(
                        currentChannel = currentChannel,
                        isPlaying = isPlaying,
                        treasureCount = fmTreasure.size,
                        onOpenChannel = { showChannelSheet = true },
                        onOpenTreasure = { showTreasureSheet = true }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showFullLyrics && displaySong != null) {
                            FmFullLyricsView(
                                song = displaySong,
                                lyrics = lyrics,
                                syncedLyrics = syncedLyrics,
                                lyricsLoading = lyricsLoading,
                                onReloadLyrics = onReloadLyrics,
                                progressFlow = progressFlow,
                                isPlaying = isPlaying,
                                onSeek = onSeek,
                                onSeekToLyric = onSeekToLyric,
                                onCloseLyrics = { showFullLyrics = false }
                            )
                        } else {
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

        if (showExportSheet) {
            PlaylistEditorSheet(
                title = "新建歌单",
                confirmText = "创建歌单",
                showPlatformImport = false,
                onDismiss = { showExportSheet = false },
                onPickCover = onPickCover,
                onDeleteCover = onDeleteCover,
                onConfirm = { result ->
                    showExportSheet = false
                    onExportToPlaylist(result)
                },
            )
        }
    }
}

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
            .padding(horizontal = AppSpacing.page, vertical = AppSpacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(FmCardBg)
                .border(0.8.dp, FmCardBorder, RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenChannel)
                .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = currentChannel.icon, fontSize = 15.sp)
            Spacer(Modifier.width(AppSpacing.xs))
            Text(
                text = currentChannel.name,
                color = fmOn(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(AppSpacing.xs))
            if (isPlaying) {
                WaveBars(color = AccentRose, barWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(AppSpacing.xxs))
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "切换频道",
                tint = fmOn(0.7f),
                modifier = Modifier.size(16.dp)
            )
        }

        if (treasureCount > 0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(AccentRose.copy(alpha = 0.25f))
                    .border(0.8.dp, AccentRose.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenTreasure)
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
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

        Box(
            modifier = Modifier
                .size(270.dp)
                .rotate(discRotation.value)
                .shadow(elevation = 18.dp, shape = CircleShape, spotColor = Color.Black)
                .clip(CircleShape)
                .background(AppColors.fmDeep)
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.width / 2f

                val radii = listOf(0.92f, 0.86f, 0.80f, 0.74f, 0.68f, 0.62f, 0.56f)
                for (rRatio in radii) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.045f),
                        radius = maxR * rRatio,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

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
                            .background(AppColors.coverPlaceholderDark1),
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

                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(AppColors.fmBlack)
                        .border(1.2.dp, AppColors.goldDisc.copy(alpha = 0.75f), CircleShape)
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
            .padding(horizontal = AppSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

        Spacer(Modifier.height(AppSpacing.xxs))

        Text(
            text = "${song.artist.ifBlank { "未知歌手" }} · ${song.album.ifBlank { "单曲" }}",
            color = fmOn(0.65f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(AppSpacing.xl))

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

        Spacer(Modifier.height(AppSpacing.xxl))

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

        Spacer(Modifier.height(AppSpacing.xxxl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
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
            .padding(vertical = AppSpacing.xl, horizontal = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AppSpacing.sm))
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
    lyricsLoading: Boolean = false,
    onReloadLyrics: (() -> Unit)? = null,
    progressFlow: StateFlow<PlaybackProgress>,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onSeekToLyric: (LyricLine) -> Unit,
    onCloseLyrics: () -> Unit,
) {
    val progress by progressFlow.collectAsState()
    val animatedPos = remember { mutableLongStateOf(0L) }
    val listState = rememberLazyListState()

    KeepScreenOn()

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
            .padding(horizontal = AppSpacing.section)
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
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onReloadLyrics?.invoke() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (lyricsLoading) "正在加载歌词…" else "暂无歌词 · 点一下重新加载",
                    color = fmOn(0.5f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(999.dp))
                .background(fmGlass(0.2f))
                .clickable(onClick = onCloseLyrics)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Album,
                contentDescription = null,
                tint = fmOn(),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(AppSpacing.xxs))
            Text(
                text = "唱片模式",
                color = fmOn(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

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
                .padding(AppSpacing.page)
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

            Spacer(Modifier.height(AppSpacing.xxl))

            // 嵌套滚动会让抽屉底部内容被裁掉且必须上滑才能看到）
            channels.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    row.forEach { ch ->
                        FmChannelCard(
                            channel = ch,
                            isSelected = ch.id == selectedChannel.id,
                            onClick = { onSelect(ch) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(AppSpacing.md))
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
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = channel.icon, fontSize = 24.sp)
        Spacer(Modifier.width(AppSpacing.md))
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.page, vertical = AppSpacing.xl),
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
                    itemsIndexed(
                        songs,
                        key = { _, song -> "${song.platform}:${song.id}" },
                        contentType = { _, _ -> "fm_song" },
                    ) { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongCover(
                                url = song.artwork,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(AppSpacing.lg))
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
                                modifier = Modifier.padding(start = 72.dp, end = AppSpacing.xxl),
                                thickness = 0.5.dp,
                                color = Stone200.copy(alpha = 0.55f)
                            )
                        }
                    }
                }

                Button(
                    onClick = onExportToPlaylist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(AppSpacing.sm))
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
            .padding(AppSpacing.section),
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

        Spacer(Modifier.height(AppSpacing.sm))

        Text(
            text = "私人算法实时调频中，马上就来",
            color = fmOn(0.5f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

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
        Spacer(Modifier.height(AppSpacing.xxl))
        Text(
            text = "电台调频遇到了一点小波折",
            color = fmOn(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = error,
                color = fmOn(0.5f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(AppSpacing.section))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(AccentRose)
                .clickable(onClick = onRetry)
                .padding(horizontal = AppSpacing.section, vertical = AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                text = "重新调频 / 换换口味",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
