package com.aesthetic.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.WaveBars
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.PlayerBg
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextFaint
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

@Composable
fun HomeScreen(
    history: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    hotPlaylists: List<PlaylistItem>,
    hotLoading: Boolean,
    favorites: Set<String>,
    favoriteSongs: List<Song>,
    onPlaySong: (Song, List<Song>) -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenPlayer: () -> Unit,
    onSearchBarClick: () -> Unit,
    onOpenRecentAll: () -> Unit,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenFavorites: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 16.dp,
            start = 20.dp,
            end = 20.dp,
            bottom = 140.dp // 仅让列表滚动内容最底部留出底栏高度
        )
    ) {
        item { HomeHeader(currentSong = currentSong, isPlaying = isPlaying, onOpenPlayer = onOpenPlayer) }
        item { Spacer(Modifier.height(16.dp)) }
        item { SearchEntry(onClick = onSearchBarClick) }
        item { Spacer(Modifier.height(24.dp)) }

        // 正在播放 / 继续播放
        if (currentSong != null) {
            item {
                NowPlayingCard(
                    song = currentSong,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onPlayPause = onTogglePlayback,
                    onOpen = onOpenPlayer,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        // 最近播放
        if (history.isNotEmpty()) {
            item { SectionHeader("最近播放", count = history.size, onClick = onOpenRecentAll) }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(history.take(12)) { song ->
                        RecentSongCard(
                            song = song,
                            isCurrent = song.id == currentSong?.id && song.platform == currentSong?.platform,
                            onClick = { onPlaySong(song, history) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        // 热门歌单
        item { SectionHeader("热门歌单", onClick = onOpenDiscover) }
        if (hotLoading && hotPlaylists.isEmpty()) {
            item {
                Text(
                    "加载中…",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else if (hotPlaylists.isNotEmpty()) {
            item { Spacer(Modifier.height(12.dp)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(hotPlaylists.take(10)) { item ->
                        PlaylistCard(item, onClick = { onOpenPlaylistDetail(item) })
                    }
                }
            }
        }

        // 我的收藏
        if (favoriteSongs.isNotEmpty()) {
            item { Spacer(Modifier.height(24.dp)) }
            item { SectionHeader("我的收藏", count = favoriteSongs.size, onClick = onOpenFavorites) }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(favoriteSongs.take(10)) { song ->
                        FavoriteCard(song, onClick = { onPlaySong(song, favoriteSongs) })
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HomeHeader(currentSong: Song?, isPlaying: Boolean, onOpenPlayer: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "Melos",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                greetingText(),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSoft,
            )
        }

        // 顶部播放按钮优化：增加圆角卡片背景与悬浮质感
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(if (currentSong != null) 4.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background(if (currentSong != null) PrimaryBrown else Stone100)
                .clickable(enabled = currentSong != null, onClick = onOpenPlayer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "播放控制",
                tint = if (currentSong != null) Color.White else TextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = TextSoft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "搜索歌名、歌手或音源…",
            color = TextSoft,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun NowPlayingCard(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(PlayerBg, PrimaryBrown)))
            .clickable(onClick = onOpen)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SongCover(song.artwork, Modifier.size(60.dp), corner = 12.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("正在播放", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))

                // 进度条微调（圆角）
                if (durationMs > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f))
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "播放/暂停",
                    tint = PlayerBg,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (count != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "$count",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextFaint,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (onClick != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("更多", style = MaterialTheme.typography.labelMedium, color = TextSoft)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSoft, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun RecentSongCard(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            SongCover(song.artwork, Modifier.size(112.dp), corner = 14.dp)
            if (isCurrent) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    WaveBars(Color.White)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = TextSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCard(item: PlaylistItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp) // 增加底部边距，防止内容贴边
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            SongCover(item.coverUrl, Modifier.size(132.dp), corner = 16.dp)
            if (item.playCount != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        formatCount(item.playCount),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // 调整歌单标题：限制 2 行并固定行高，防止过度挤压下方控件
        Text(
            item.name,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(4.dp))

        // 歌曲数量
        Text(
            "${item.trackCount ?: 0} 首",
            style = MaterialTheme.typography.bodySmall,
            color = TextSoft,
            maxLines = 1
        )
    }
}
@Composable
private fun FavoriteCard(song: Song, onClick: () -> Unit) {
    Row(
        Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(song.artwork, Modifier.size(48.dp), corner = 10.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Rose, modifier = Modifier.size(18.dp))
    }
}

private fun greetingText(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h < 6 -> "夜深了，听首歌吧"
        h < 12 -> "早上好，想听点什么呢"
        h < 18 -> "下午好，放松一下吧"
        else -> "晚上好，开启你的专属音乐"
    }
}

private fun formatCount(count: Long): String = when {
    count >= 100_000_000 -> String.format("%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format("%.1f万", count / 10_000.0)
    else -> count.toString()
}