package com.aesthetic.music.ui.artist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.unit.dp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 歌手页（全屏覆盖层）：头像 + 名称 + 别名，下方热门歌曲列表。
 * 数据来自后端 /api/artist（当前 netease 源），播放复用全局取直链降级链。
 */
@Composable
fun ArtistScreen(
    name: String,
    alias: String?,
    avatar: String?,
    songs: List<Song>,
    isLoading: Boolean,
    favorites: Set<String>,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp)
                .padding(bottom = 150.dp) // 避开底栏悬浮 Dock
        ) {
            // 顶栏：返回 + 标题 + 播放全部
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(Surface, CircleShape)
                        .clickable(onClick = onBack)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextMuted,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    name.ifBlank { "歌手" },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (songs.isNotEmpty()) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(PrimaryBrown)
                            .clickable(onClick = onPlayAll)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("播放全部", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }

            // 歌手头部信息
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                SongCover(avatar, Modifier.size(84.dp), corner = 42.dp) // corner=半径 → 圆形
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        name.ifBlank { "未知歌手" },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!alias.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            alias,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isLoading) "加载中…" else "热门歌曲 ${songs.size} 首",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 热门歌曲列表
            if (!isLoading && songs.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无歌曲", color = TextSoft, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(songs) { position, song ->
                        ArtistSongRow(
                            index = position + 1,
                            song = song,
                            isFavorite = (song.platform + ":" + song.id) in favorites,
                            onClick = { onPlaySong(song) },
                            onToggleFavorite = { onToggleFavorite(song) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistSongRow(
    index: Int,
    song: Song,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (index <= 3) PrimaryBrown else TextSoft,
            fontWeight = if (index <= 3) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(24.dp),
        )
        SongCover(song.artwork, Modifier.size(46.dp), corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                song.subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "收藏",
                tint = if (isFavorite) Rose else TextSoft,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
