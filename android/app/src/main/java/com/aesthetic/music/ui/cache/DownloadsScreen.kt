package com.aesthetic.music.ui.cache

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ListSearchBar
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.components.BottomContentPadding
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextFaint
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

@Composable
fun DownloadsScreen(
    songs: List<AudioCacheService.CachedSong>,
    currentSong: Song?,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onRemoveOne: (String) -> Unit,
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) searchFocus.requestFocus()
    }

    val downloadedSongs = remember(songs) {
        songs.filter { it.manual }.distinctBy { it.filePath }
    }

    val totalSizeText = AudioCacheService.formatSize(downloadedSongs.sumOf { it.size })

    val filtered = remember(downloadedSongs, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            downloadedSongs
        } else {
            downloadedSongs.filter {
                it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
            }
        }
    }

    val filteredSongList = remember(filtered) { filtered.map { it.toSong() } }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarTop + AppSpacing.xxl)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.page),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onBack)
                )
                Spacer(Modifier.width(AppSpacing.lg))
                Text(
                    text = "我的下载",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                )
            }

            Spacer(Modifier.height(AppSpacing.xxl))

            if (songs.isNotEmpty()) {
                DownloadsSummaryCard(
                    songCount = downloadedSongs.size,
                    totalSizeText = totalSizeText,
                    onPlayAll = {
                        if (filteredSongList.isNotEmpty()) {
                            onPlaySong(filteredSongList.first(), filteredSongList)
                        }
                    },
                    onShuffle = {
                        if (filteredSongList.isNotEmpty()) {
                            val shuffled = filteredSongList.shuffled()
                            onPlaySong(shuffled.first(), shuffled)
                        }
                    },
                    onSearch = {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded) searchQuery = ""
                    },
                    modifier = Modifier.padding(horizontal = AppSpacing.page)
                )
                Spacer(Modifier.height(AppSpacing.xxl))
            }

            if (isSearchExpanded) {
                ListSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "搜索已下载的歌曲、歌手…",
                    focusRequester = searchFocus,
                    modifier = Modifier.padding(horizontal = AppSpacing.page),
                )
                Spacer(Modifier.height(AppSpacing.xxl))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.page),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "已下载歌曲",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                )
                if (songs.isNotEmpty()) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "${filtered.size} 首" else "${songs.size} 首",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = TextSoft,
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            if (songs.isEmpty()) {
                EmptyBox("暂无下载歌曲\n在歌曲详情或播放时点击下载，可在此离线免流收听")
            } else if (filtered.isEmpty()) {
                EmptyBox("没有找到与「${searchQuery.trim()}」相关的歌曲")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    contentPadding = BottomContentPadding
                ) {
                    itemsIndexed(
                        filtered,
                        key = { i, s -> "dl$i:${s.filePath}" },
                        contentType = { _, _ -> "download_song" },
                    ) { index, song ->
                        val isPlayingThis = currentSong != null &&
                                ((currentSong.id.isNotBlank() && currentSong.id == song.songId) ||
                                        (currentSong.title == song.title && currentSong.artist == song.artist))

                        DownloadRow(
                            song = song,
                            playingNow = isPlayingThis,
                            onClick = {
                                val target = filteredSongList.firstOrNull {
                                    (it.id.isNotBlank() && it.id == song.songId) ||
                                            (it.title == song.title && it.artist == song.artist)
                                }
                                if (target != null) {
                                    onPlaySong(target, filteredSongList)
                                }
                            },
                            onRemove = { onRemoveOne(song.filePath) },
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun DownloadsSummaryCard(
    songCount: Int,
    totalSizeText: String,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(22.dp), elevation = 6.dp)
            .padding(AppSpacing.xxxl)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(AccentMint.copy(alpha = 0.15f))
                .padding(horizontal = AppSpacing.sm, vertical = 3.dp)
        ) {
            Text(
                text = "离线音乐",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = AccentMint
            )
        }

        Spacer(Modifier.height(AppSpacing.md))

        Text(
            text = "已下载",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary
        )

        Spacer(Modifier.height(AppSpacing.xxs))

        Text(
            text = "共 $songCount 首 · 占用 $totalSizeText",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = TextSoft
        )

        Spacer(Modifier.height(AppSpacing.xxxl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(PrimaryBrown)
                    .clickable(onClick = onPlayAll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(AppSpacing.xs))
                Text(
                    "播放全部",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp, fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }

            Spacer(Modifier.width(AppSpacing.lg))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .iosGlassCard(shape = CircleShape, elevation = 2.dp)
                    .clickable(onClick = onShuffle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "随机播放",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(AppSpacing.md))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .iosGlassCard(shape = CircleShape, elevation = 2.dp)
                    .clickable(onClick = onSearch),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    song: AudioCacheService.CachedSong,
    playingNow: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.page)
            .iosGlassCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (Palette.isDark) AppColors.successSurfaceDark else AppColors.successSurfaceLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playingNow) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = AccentMint,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(AppSpacing.lg))

            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = if (playingNow) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = if (playingNow) PrimaryBrown else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = AccentMint,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${song.artist} · ${AudioCacheService.formatSize(song.size)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除下载",
                    tint = TextFaint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun AudioCacheService.CachedSong.toSong(): Song = Song(
    id = songId.ifBlank { filePath.hashCode().toString() },
    title = title,
    artist = artist,
    platform = platformCode.ifBlank { "local" },
)