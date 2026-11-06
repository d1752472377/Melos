package com.aesthetic.music.ui.guess

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.DefaultCoverTone
import com.aesthetic.music.ui.components.PlayingBars
import com.aesthetic.music.ui.components.coverHairline
import com.aesthetic.music.ui.components.coverPlaceholder
import com.aesthetic.music.ui.components.glassToneCard
import com.aesthetic.music.ui.components.rememberCoverTone
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.appIsDark
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

@Composable
fun GuessYouLikeScreen(
    state: GuessController.State,
    refreshing: Boolean,
    isPlaying: Boolean,
    currentSongId: String?,
    favoriteKeys: Set<String>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onSavePlaylist: (List<Song>) -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        HomeAmbientGlowBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            GuessTopBar(
                refreshing = refreshing,
                topPadding = statusBarTop,
                onBack = onBack,
                onRefresh = onRefresh,
            )

            when (state) {
                is GuessController.State.Loading -> GuessLoading()
                is GuessController.State.Empty -> GuessEmpty(
                    title = state.title,
                    subtitle = state.subtitle,
                )

                is GuessController.State.Error -> GuessError(
                    message = state.message,
                    onRetry = onRetry,
                )

                is GuessController.State.Ready -> GuessContent(
                    state = state,
                    refreshing = refreshing,
                    isPlaying = isPlaying,
                    currentSongId = currentSongId,
                    favoriteKeys = favoriteKeys,
                    onRefresh = onRefresh,
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle,
                    onPlaySong = onPlaySong,
                    onToggleFavorite = onToggleFavorite,
                    onSavePlaylist = onSavePlaylist,
                )
            }
        }
    }
}

@Composable
private fun GuessTopBar(
    refreshing: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppSpacing.page, end = AppSpacing.page, top = topPadding + AppSpacing.xs, bottom = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = textHigh(),
            modifier = Modifier
                .clip(CircleShape)
                .pressClick(pressedScale = 0.9f, onClick = onBack)
                .padding(AppSpacing.xxs)
                .size(24.dp),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "猜你喜欢",
                color = textHigh(),
                fontSize = 28.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = (-0.8).sp,
                lineHeight = 34.sp,
            )
            Text(
                text = "你的每日私人推荐",
                color = textLow(),
                fontSize = 11.sp,
                fontWeight = FontWeight.W500,
                letterSpacing = 0.2.sp,
            )
        }
        GuessRefreshButton(refreshing = refreshing, onRefresh = onRefresh)
    }
}

@Composable
private fun GuessRefreshButton(refreshing: Boolean, onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (refreshing) {
                    Modifier
                } else {
                    Modifier.pressClick(pressedScale = 0.9f, onClick = onRefresh)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            CircularProgressIndicator(
                color = textMedium(),
                strokeWidth = 2.2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "换一批",
                tint = textMedium(),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun GuessContent(
    state: GuessController.State.Ready,
    refreshing: Boolean,
    isPlaying: Boolean,
    currentSongId: String?,
    favoriteKeys: Set<String>,
    onRefresh: () -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onSavePlaylist: (List<Song>) -> Unit,
) {
    val songs = state.songs
    val listState = rememberLazyListState()

    val tone = rememberCoverTone(songs.firstOrNull()?.artwork, DefaultCoverTone)
    val accent = discoveryAccent(tone)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 10.dp,
            bottom = BottomContentSpacer,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        item(key = "hero") {
            GuessHero(
                songs = songs,
                seedCount = state.seedCount,
                tone = tone,
                accent = accent,
                refreshing = refreshing,
                onPlayAll = { onPlayAll(songs) },
                onShuffle = { onShuffle(songs) },
                onSavePlaylist = { onSavePlaylist(songs) },
            )
        }

        item(key = "taste_profile") {
            GuessTasteProfile(seedCount = state.seedCount, accent = accent)
        }

        item(key = "personal_section") {
            GuessPersonalSectionHeader(
                count = songs.size,
                seedCount = state.seedCount,
                accent = accent,
            )
        }

        itemsIndexed(songs, key = { _, s -> "guess:${s.platform}:${s.id}" }) { index, song ->
            val key = "${song.platform}:${song.id}"
            GuessSongRow(
                song = song,
                index = index,
                accent = accent,
                isCurrent = currentSongId != null && currentSongId == key,
                isPlaying = isPlaying,
                isFavorite = key in favoriteKeys,
                onPlay = { onPlaySong(song, songs) },
                onToggleFavorite = { onToggleFavorite(song) },
            )
        }
    }
}

@Composable
private fun GuessHero(
    songs: List<Song>,
    seedCount: Int,
    tone: Color,
    accent: Color,
    refreshing: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSavePlaylist: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .glassToneCard(
                shape = RoundedCornerShape(20.dp),
                tone = tone,
                selected = true,
                gradient = false,
            )
            .padding(AppSpacing.page),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "为你精选",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "PRIVATE MIX",
                color = textMedium(),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val deckScale = if (maxWidth < 320.dp) 0.82f else 1f
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "下一首，\n也许就心动。",
                        color = textHigh(),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.W800,
                        letterSpacing = (-0.8).sp,
                        lineHeight = 33.sp,
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    Text(
                        text = "沿着你的听歌足迹\n遇见新的喜欢",
                        color = textMedium(),
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                    )
                }
                Spacer(Modifier.width(AppSpacing.sm))
                GuessCoverDeck(
                    covers = songs.take(3).map { it.artwork },
                    tone = tone,
                    scale = deckScale,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(textMedium().copy(alpha = 0.08f)),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.page),
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = songs.size.toString().padStart(2, '0'),
                    color = textHigh(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.W800,
                    letterSpacing = (-1).sp,
                )
                Text(
                    text = "首精选歌曲",
                    color = textMedium(),
                    fontSize = 11.sp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "由你的热爱出发",
                    color = textHigh(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W800,
                )
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    text = if (seedCount > 0) {
                        "来自收藏与最近播放的 $seedCount 条线索"
                    } else {
                        "越常听，越懂你"
                    },
                    color = textMedium(),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuessPlayAllButton(
                tone = tone,
                enabled = songs.isNotEmpty(),
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
            )
            GuessHeroRoundButton(
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "随机播放",
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = "随机播放",
                enabled = songs.isNotEmpty(),
                tone = tone,
                onClick = onShuffle,
            )
            GuessHeroRoundButton(
                icon = { tint ->
                    if (refreshing) {
                        CircularProgressIndicator(
                            color = tint,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = "保存为歌单",
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                label = "保存为歌单",
                enabled = songs.isNotEmpty(),
                tone = tone,
                onClick = onSavePlaylist,
            )
        }
    }
}

@Composable
private fun GuessPlayAllButton(
    tone: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brush = remember(tone) {
        Brush.linearGradient(listOf(tone, lerp(tone, Color.White, 0.24f)))
    }
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(brush, RoundedCornerShape(14.dp))
            .then(
                if (enabled) Modifier.pressClick(pressedScale = 0.98f, onClick = onClick)
                else Modifier,
            )
            .padding(AppSpacing.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = "播放全部",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.W800,
        )
    }
}

@Composable
private fun GuessHeroRoundButton(
    icon: @Composable (Color) -> Unit,
    label: String,
    enabled: Boolean,
    tone: Color,
    onClick: () -> Unit,
) {
    val accent = discoveryAccent(tone)
    val iconTint = remember(accent) { lerp(accent, Color.White, 0.8f) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(tone.copy(alpha = if (enabled) 0.1f else 0.05f), CircleShape)
            .then(
                if (enabled) Modifier.pressClick(pressedScale = 0.9f, onClick = onClick)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f }) {
            icon(iconTint)
        }
    }
}

@Composable
private fun GuessCoverDeck(covers: List<String?>, tone: Color, scale: Float = 1f) {
    val front = covers.getOrNull(0)
    val back1 = covers.getOrNull(1) ?: front
    val back2 = covers.getOrNull(2) ?: back1

    Box(
        modifier = Modifier
            .width(132.dp * scale)
            .height(126.dp * scale),
        contentAlignment = Alignment.Center,
    ) {
        DeckCover(
            url = back1,
            size = 92.dp * scale,
            tone = tone,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { rotationZ = -8f },
        )
        DeckCover(
            url = back2,
            size = 92.dp * scale,
            tone = tone,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { rotationZ = 8f },
        )
        DeckCover(
            url = front,
            size = 104.dp * scale,
            tone = tone,
            modifier = Modifier.align(Alignment.Center),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp * scale, y = (-2).dp * scale)
                .size(28.dp * scale)
                .shadow(5.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.22f))
                .clip(CircleShape)
                .background(tone, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp * scale),
            )
        }
    }
}

@Composable
private fun DeckCover(
    url: String?,
    size: androidx.compose.ui.unit.Dp,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    val brush = remember(tone) {
        Brush.linearGradient(listOf(tone, lerp(tone, Color.White, 0.24f)))
    }
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 9.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.18f),
            )
            .border(1.dp, coverHairline(), shape)
            .background(brush, shape)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(200).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(size * 0.34f),
            )
        }
    }
}

@Composable
private fun GuessTasteProfile(seedCount: Int, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Text(
            text = if (seedCount > 0) "熟悉的偏爱，也有意外的新发现" else "多听几首，让推荐慢慢懂你",
            color = textMedium(),
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun GuessPersonalSectionHeader(count: Int, seedCount: Int, accent: Color) {
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "本次精选",
                color = textHigh(),
                fontSize = 20.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.height(AppSpacing.xxs))
            Text(
                text = if (seedCount > 0) "综合收藏偏好与近期播放排序" else "继续听歌，推荐会越来越懂你",
                color = textLow(),
                fontSize = 11.sp,
                fontWeight = FontWeight.W500,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            Text(
                text = "$count 首",
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun GuessSongRow(
    song: Song,
    index: Int,
    accent: Color,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val context = LocalContext.current
    val dark = appIsDark()
    val quietSurface = if (dark) AppColors.quietSurfaceDark else AppColors.quietSurfaceLight

    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) quietSurface else Color.Transparent)
            .pressClick(pressedScale = 0.98f, onClick = onPlay)
            .padding(horizontal = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            color = if (isCurrent) accent else textLow(),
            fontSize = 10.sp,
            fontWeight = if (index < 3) FontWeight.W800 else FontWeight.W700,
            letterSpacing = 0.4.sp,
            modifier = Modifier.width(28.dp),
        )
        Spacer(Modifier.width(AppSpacing.xxs))

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(coverPlaceholder(), RoundedCornerShape(12.dp))
                .border(1.dp, coverHairline(), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!song.artwork.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(song.artwork).crossfade(200).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                )
                PlayingBars(
                    isPlaying = isPlaying,
                    color = Color.White,
                    modifier = Modifier.height(13.dp),
                )
            }
        }
        Spacer(Modifier.width(AppSpacing.lg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title.ifBlank { "未知歌曲" },
                color = if (isCurrent) accent else textHigh(),
                fontSize = 14.sp,
                fontWeight = FontWeight.W500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(song.artist.ifBlank { "未知歌手" })
                    if (song.album.isNotBlank()) append(" · ").append(song.album)
                },
                color = textLow(),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .pressClick(pressedScale = 0.85f, onClick = onToggleFavorite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) accent else textLow(),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun GuessLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = textMedium(),
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(AppSpacing.lg))
            Text(
                text = "正在根据你的收藏挑选…",
                color = textMedium(),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun GuessEmpty(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.section, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = textLow(),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(AppSpacing.xl))
            Text(
                text = title,
                color = textHigh(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                text = subtitle,
                color = textLow(),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GuessError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.section, vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message.ifBlank { "推荐获取失败" },
                color = textMedium(),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppSpacing.xl))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(textMedium().copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .pressClick(pressedScale = 0.96f, onClick = onRetry)
                    .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.sm),
            ) {
                Text(
                    text = "重试",
                    color = textHigh(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W800,
                )
            }
        }
    }
}

@Composable
private fun Modifier.pressClick(pressedScale: Float, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = 150,
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
        ),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
private fun discoveryAccent(tone: Color): Color =
    if (appIsDark()) lerp(tone, Color.White, 0.48f) else lerp(tone, Color.Black, 0.28f)
