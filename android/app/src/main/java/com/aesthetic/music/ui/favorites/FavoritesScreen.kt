package com.aesthetic.music.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * 我的收藏列表页：列出持久化收藏的歌曲，点击播放/取消收藏，支持一键播放全部。
 */
@Composable
fun FavoritesScreen(
    songs: List<Song>,
    currentSong: Song?,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onRemoveFavorite: (Song) -> Unit,
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
                // 底部给常驻导航 Dock 留空间
                .padding(bottom = 150.dp),
        ) {
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
                    "我的收藏 (${songs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (songs.isNotEmpty()) {
                    Row(
                        Modifier
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
            Spacer(Modifier.height(12.dp))

            if (songs.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = TextSoft,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("还没有收藏的歌曲", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                        Text("在歌曲列表点 ♡ 即可收藏", color = TextSoft, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn {
                    items(songs) { song ->
                        val isCurrent = song.id == currentSong?.id && song.platform == currentSong?.platform
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isCurrent) Surface else Color.Transparent)
                                .clickable { onPlaySong(song) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SongCover(song.artwork, Modifier.size(44.dp), corner = 0.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrent) PrimaryBrown else TextPrimary,
                                    maxLines = 1,
                                )
                                Text(song.subtitleText, style = MaterialTheme.typography.bodySmall, color = TextSoft, maxLines = 1)
                            }
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = "取消收藏",
                                tint = Rose,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onRemoveFavorite(song) },
                            )
                        }
                    }
                }
            }
        }
    }
}
