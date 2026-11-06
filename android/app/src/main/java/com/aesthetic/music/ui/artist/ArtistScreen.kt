package com.aesthetic.music.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.SongRow
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.theme.AccentCyan
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import com.aesthetic.music.ui.components.ListSearchBar
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing

@Composable
fun ArtistScreen(
    name: String,
    alias: String?,
    avatar: String?,
    songs: List<Song>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    currentSong: Song?,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onMoreSong: (Song) -> Unit,
    onOpenAllSongs: () -> Unit,
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) searchFocus.requestFocus()
    }

    val visibleSongs = remember(songs, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            songs
        } else {
            songs.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        HomeAmbientGlowBackground()
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = statusBarTop + AppSpacing.xxl)
        ) {
            Text(
                text = "歌手详情",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = AppSpacing.page)
            )

            Spacer(Modifier.height(AppSpacing.xxl))

            ArtistSummaryCard(
                name = name,
                alias = alias,
                avatar = avatar,
                songCount = songs.size,
                isLoading = isLoading,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
                onSearch = {
                    isSearchExpanded = !isSearchExpanded
                    if (!isSearchExpanded) searchQuery = ""
                },
                modifier = Modifier.padding(horizontal = AppSpacing.page)
            )

            if (isSearchExpanded) {
                Spacer(Modifier.height(AppSpacing.xl))
                ListSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "搜索该歌手的歌曲…",
                    focusRequester = searchFocus,
                    modifier = Modifier.padding(horizontal = AppSpacing.page),
                )
            }

            Spacer(Modifier.height(AppSpacing.xxxl))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.page),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "热门单曲",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                )
                if (visibleSongs.isNotEmpty()) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "${visibleSongs.size} 首" else "${songs.size} 首",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = TextSoft,
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            if (isLoading && songs.isEmpty()) {
                LoadingBox(message = "探索音乐库中…")
            } else if (!isLoading && songs.isEmpty()) {
                ErrorBox(
                    message = errorMessage ?: "暂无相关歌曲",
                    onRetry = if (errorMessage != null) onRetry else null,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = AppSpacing.xxl)
                        .iosGlassCard(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), elevation = 4.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Top,
                        contentPadding = PaddingValues(top = 10.dp, bottom = BottomContentSpacer),
                    ) {
                        itemsIndexed(
                            visibleSongs,
                            key = { i, s -> "a$i:${s.platform}:${s.id}" },
                        ) { index, song ->
                            val playingNow = currentSong != null &&
                                    song.id == currentSong.id && song.platform == currentSong.platform
                            SongRow(
                                song = song,
                                isPlaying = playingNow,
                                showPlatformTag = false,
                                onPlay = { onPlaySong(song) },
                                actions = {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable { onMoreSong(song) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreHoriz,
                                            contentDescription = "更多操作",
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                            )
                            if (index != visibleSongs.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 70.dp, end = AppSpacing.lg),
                                    thickness = 0.5.dp,
                                    color = Stone200.copy(alpha = 0.55f),
                                )
                            }
                        }
                        item(key = "all_songs_footer") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenAllSongs)
                                    .padding(vertical = AppSpacing.xl),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "全部演唱",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = TextSoft,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = TextSoft,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}