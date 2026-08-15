package com.aesthetic.music.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 发现页：内容体系（每日推荐 / 新歌速递 / 排行榜）+ 热门歌单。
 */
@Composable
fun DiscoverScreen(
    hotPlaylists: List<PlaylistItem>,
    dailyPlaylists: List<PlaylistItem>,
    newSongs: List<Song>,
    ranks: List<PlaylistItem>,
    isLoading: Boolean,
    isHotLoading: Boolean,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenRank: (PlaylistItem) -> Unit,
    onPlaySong: (Song) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && dailyPlaylists.isEmpty() && newSongs.isEmpty() && ranks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
            }
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = 28.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 150.dp // 完美避开底栏播放器及 Dock
            )
        ) {
            // 顶栏标题
            item {
                Column {
                    Text(
                        "发现",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "每日推荐 · 新歌 · 排行榜",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                    )
                }
            }

            // 每日推荐
            if (dailyPlaylists.isNotEmpty()) {
                item { SectionTitle("每日推荐", "为你精选") }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(dailyPlaylists) { item ->
                            PlaylistCard(item = item, width = 132.dp, onClick = { onOpenPlaylist(item) })
                        }
                    }
                }
            }

            // 新歌速递
            if (newSongs.isNotEmpty()) {
                item { SectionTitle("新歌速递", "第一时间听见") }
                items(newSongs.take(10)) { song ->
                    NewSongRow(song = song, onClick = { onPlaySong(song) })
                }
            }

            // 排行榜
            if (ranks.isNotEmpty()) {
                item { SectionTitle("排行榜", "热门榜单") }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(ranks.take(12)) { item ->
                            PlaylistCard(item = item, width = 132.dp, onClick = { onOpenRank(item) })
                        }
                    }
                }
            }

            // 热门歌单
            if (hotPlaylists.isNotEmpty()) {
                item { SectionTitle("热门歌单", "大家都在听") }
                items(hotPlaylists.chunked(2)) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        rowItems.forEach { item ->
                            GridPlaylistCard(
                                item = item,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenPlaylist(item) },
                            )
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (isHotLoading && hotPlaylists.isEmpty()) {
                item { Text("热门歌单加载中…", color = TextSoft, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextSoft
        )
    }
}

@Composable
private fun NewSongRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 赋予单曲封面圆角与尺寸微调
        SongCover(song.artwork, Modifier.size(48.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
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
                song.subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PrimaryBrown.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = PrimaryBrown,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PlaylistCard(item: PlaylistItem, width: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(width)
                .clip(RoundedCornerShape(14.dp)) // 歌单封面圆角化
        ) {
            SongCover(item.coverUrl, Modifier.fillMaxSize(), corner = 14.dp)
            if (item.playCount != null) {
                // 精致毛玻璃感播放量标记
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp),
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            formatCount(item.playCount),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${item.trackCount ?: 0} 首",
            style = MaterialTheme.typography.bodySmall,
            color = TextSoft,
        )
    }
}

@Composable
private fun GridPlaylistCard(item: PlaylistItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
        ) {
            SongCover(item.coverUrl, Modifier.fillMaxSize(), corner = 14.dp)
            if (item.playCount != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp),
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            formatCount(item.playCount),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(2.dp))
        Text("${item.trackCount ?: 0} 首", style = MaterialTheme.typography.bodySmall, color = TextSoft)
    }
}

private fun formatCount(count: Long): String = when {
    count >= 100_000_000 -> String.format("%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format("%.1f万", count / 10_000.0)
    else -> count.toString()
}