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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.SongRow
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextFaint
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/** 全部演唱：假分页（每次渲染 30 条，滚到底自动追加 30 条），数据为歌手全量歌曲。 */
private const val PAGE_SIZE = 30

@Composable
fun ArtistAllSongsScreen(
    artistName: String,
    songs: List<Song>,
    favorites: Set<String>,
    currentSong: Song?,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onMoreSong: (Song) -> Unit,
) {
    var visibleCount by remember(songs.size) {
        mutableIntStateOf(minOf(PAGE_SIZE, songs.size))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
        ) {
            // 顶栏：返回 + 标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "全部演唱",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }

            // 操作行：播放全部(N) + 管理（这里只做播放全部，管理留给你后续接）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onPlayAll)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "播放全部(${songs.size})",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = TextPrimary,
                    )
                }
            }

            if (songs.isEmpty()) {
                EmptyBox(message = "暂无相关歌曲")
                return@Column
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Surface),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Top,
                    contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current),
                ) {
                    itemsIndexed(
                        songs.take(visibleCount),
                        key = { i, s -> "all$i:${s.platform}:${s.id}" },
                    ) { index, song ->
                        val playingNow = currentSong != null &&
                            song.id == currentSong.id && song.platform == currentSong.platform
                        val isFavorite = (song.platform + ":" + song.id) in favorites
                        SongRow(
                            song = song,
                            isPlaying = playingNow,
                            showPlatformTag = false,
                            onPlay = { onPlaySong(song) },
                            leading = {
                                Text(
                                    text = (index + 1).toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (index < 3) PrimaryBrown else TextSoft,
                                    fontWeight = if (index < 3) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.width(24.dp),
                                )
                            },
                            actions = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 收藏
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable { onToggleFavorite(song) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            contentDescription = "收藏",
                                            tint = if (isFavorite) Rose else TextFaint,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                    // 下一首播放
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable { onPlayNext(song) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "下一首播放",
                                            tint = TextFaint,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                    // 更多
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable { onMoreSong(song) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreHoriz,
                                            contentDescription = "更多操作",
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            },
                        )
                        if (index != songs.take(visibleCount).lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 82.dp, end = 12.dp),
                                thickness = 0.5.dp,
                                color = Stone200.copy(alpha = 0.55f),
                            )
                        }
                    }
                    // 假分页哨兵：滚到底自动追加下一页
                    if (visibleCount < songs.size) {
                        item(key = "load_more_sentinel") {
                            LaunchedEffect(visibleCount) {
                                visibleCount = minOf(visibleCount + PAGE_SIZE, songs.size)
                            }
                            Spacer(Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}