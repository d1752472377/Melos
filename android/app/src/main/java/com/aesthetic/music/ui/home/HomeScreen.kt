package com.aesthetic.music.ui.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.ui.components.liquidGlassCardSurface
import com.aesthetic.music.ui.components.rememberIOSOverScrollEffect
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.theme.appIsDark
import com.aesthetic.music.ui.theme.AppSpacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    dailyPlaylists: List<PlaylistItem>,
    newSongs: List<Song>,
    hotSongs: List<Song>,
    hotPlaylists: List<PlaylistItem>,
    ranks: List<PlaylistItem>,
    recommendSongs: List<Song> = emptyList(),
    isLoadingRecommendSongs: Boolean = false,
    onLoadRecommendSongs: (PlaylistItem) -> Unit = {},
    newSongRegions: List<RegionNewSongs> = emptyList(),
    isLoading: Boolean = false,
    contentError: String? = null,
    onRetryContent: (() -> Unit)? = null,
    onPlaySong: (Song, List<Song>) -> Unit,
    onSearchBarClick: () -> Unit,
    onMicClick: (() -> Unit)? = null,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
    onOpenGuess: () -> Unit,
    onOpenRanksPage: () -> Unit,
    onOpenFm: () -> Unit = {},
    isPlaying: Boolean = false,
) {
    val poolSongs = remember(newSongs, hotSongs) {
        (if (newSongs.isNotEmpty()) newSongs else hotSongs)
            .distinctBy { it.id }
    }

    val targetPlaylist = dailyPlaylists.firstOrNull() ?: hotPlaylists.firstOrNull()

    LaunchedEffect(targetPlaylist?.id, targetPlaylist?.platform) {
        val pl = targetPlaylist
        if (pl != null && pl.id.isNotBlank()) {
            onLoadRecommendSongs(pl)
        }
    }

    val hasNoContent = targetPlaylist == null &&
            poolSongs.isEmpty() &&
            hotPlaylists.isEmpty() &&
            ranks.isEmpty()

    val timeScene = remember { resolveTimeScene(currentHourOfDay()) }

    val songsMap = remember(newSongs, hotSongs, poolSongs) {
        mapOf(
            RankingTab.RISING to poolSongs.take(10),
            RankingTab.NEW to (if (newSongs.isNotEmpty()) newSongs else poolSongs).take(10),
            RankingTab.ORIGINAL to poolSongs.reversed().take(10),
            RankingTab.HOT to (if (hotSongs.isNotEmpty()) hotSongs else poolSongs).take(10)
        )
    }

    val listState = rememberLazyListState()

    val iosOverscroll = rememberIOSOverScrollEffect()

    Box(modifier = Modifier.fillMaxSize()) {
        HomeAmbientGlowBackground()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overscroll(iosOverscroll),
            contentPadding = PaddingValues(
                top = 0.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = BottomContentSpacer,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxxl)
        ) {
            item(key = "home_top_bar") {
                HomeHeader()
            }

            item(key = "home_search_bar") {
                HomeSearchEntry(onClick = onSearchBarClick, onMicClick = onMicClick)
            }

            if (targetPlaylist != null) {
                item(key = "daily_hero") {
                    HomeDailyHero(
                        title = timeScene.title,
                        subtitle = timeScene.category,
                        pages = (dailyPlaylists + hotPlaylists).distinctBy { it.id }.take(3),
                        previewSongs = (if (recommendSongs.isNotEmpty()) recommendSongs else poolSongs).take(3),
                        isLoadingSongs = isLoadingRecommendSongs && recommendSongs.isEmpty(),
                        isPlaying = isPlaying,
                        onOpenPlaylist = onOpenPlaylistDetail,
                    )
                }
            }

            item(key = "home_entry_cards") {
                HomeEntryCards(
                    onOpenRecommend = onOpenGuess,
                    onOpenRanks = onOpenRanksPage,
                    onOpenFm = onOpenFm,
                    isPlaying = isPlaying,
                )
            }

            if (isLoading && hasNoContent) {
                item(key = "home_loading") {
                    HomeLoadingState()
                }
            }

            if (contentError != null && targetPlaylist == null && poolSongs.isEmpty()) {
                item(key = "home_error_banner") {
                    HomeErrorBanner(message = contentError, onRetry = onRetryContent)
                }
            }

            if (newSongRegions.isNotEmpty()) {
                item(key = "new_song_region_section") {
                    NewSongRegionSection(
                        groups = newSongRegions,
                        onPlayRegion = { songs ->
                            val first = songs.firstOrNull()
                            if (first != null) onPlaySong(first, songs)
                        },
                    )
                }
            }

            item(key = "home_ranking_card") {
                HomeRankingCard(
                    songsMap = songsMap,
                    onPlaySong = onPlaySong,
                    onOpenRanksPage = onOpenRanksPage
                )
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(AppSpacing.lg))
            }
        }
    }
}

@Composable
private fun Modifier.gzPressScale(pressedScale: Float, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
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
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}

@Composable
private fun HomeSearchEntry(onClick: () -> Unit, onMicClick: (() -> Unit)?) {
    val dark = appIsDark()
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl)
            .height(48.dp)
            .liquidGlassCardSurface(
                shape = shape,
                flat = true,
                elevation = 0.dp,
                liquidGlassEnabled = false,
            )
            .gzPressScale(pressedScale = 0.985f, onClick = onClick)
            .padding(start = AppSpacing.xl, top = 0.dp, end = AppSpacing.xs, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = textLow(),
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = "搜索歌曲、歌手、专辑",
            color = textMedium(),
            fontSize = 13.sp,
            fontWeight = FontWeight.W500,
            maxLines = 1,
            modifier = Modifier
                .padding(start = AppSpacing.md)
                .weight(1f, fill = true),
        )

        if (onMicClick != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        color = textHigh().copy(alpha = if (dark) 0.1f else 0.06f),
                        shape = RectangleShape,
                    )
                    .gzPressScale(pressedScale = 0.9f, onClick = onMicClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "听歌识曲",
                    tint = textHigh(),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
