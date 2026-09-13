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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
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
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.SongRow
import com.aesthetic.music.ui.components.ListSearchBar
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextFaint
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/** 全部演唱：渐进渲染（每次渲染 50 条，滚到底自动追加 50 条），支持全量歌曲流畅播放与浏览。 */
private const val PAGE_SIZE = 50

@Composable
fun ArtistAllSongsScreen(
    artistName: String,
    alias: String?,
    avatar: String?,
    songs: List<Song>,
    favorites: Set<String>,
    currentSong: Song?,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onMoreSong: (Song) -> Unit,
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 页内过滤：搜歌名 / 歌手 / 专辑（作用于全量列表，假分页基于过滤结果）
    val filtered = remember(songs, searchQuery) {
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

    var visibleCount by remember(filtered.size) {
        mutableIntStateOf(minOf(PAGE_SIZE, filtered.size))
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
            // ===== 1. 顶部大标题（与「我的收藏」一致，不再有返回按钮） =====
            Text(
                text = "全部演唱",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(16.dp))

            // ===== 2. 歌手信息卡片（与歌手页同款） =====
            ArtistSummaryCard(
                name = artistName,
                alias = alias,
                avatar = avatar,
                songCount = filtered.size,
                isLoading = isLoading,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
                onSearch = {
                    isSearchExpanded = !isSearchExpanded
                    if (!isSearchExpanded) searchQuery = ""
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // ===== 2.5 列表内搜索 =====
            if (isSearchExpanded) {
                Spacer(Modifier.height(14.dp))
                ListSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "搜索该歌手的歌曲…",
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            Spacer(Modifier.height(18.dp))

            // ===== 3. 列表标题栏 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "歌曲列表",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "${filtered.size} 首" else "${songs.size} 首",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = TextSoft,
                    )
                    if (isLoadingMore) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "(全量加载中…)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isLoading && songs.isEmpty()) {
                LoadingBox(message = "正在探索全量音乐库…")
                return@Column
            }

            if (errorMessage != null && songs.isEmpty()) {
                ErrorBox(message = errorMessage, onRetry = onRetry)
                return@Column
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
                    contentPadding = PaddingValues(bottom = BottomContentSpacer),
                ) {
                    itemsIndexed(
                        filtered.take(visibleCount),
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
                        if (index != filtered.take(visibleCount).lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 82.dp, end = 12.dp),
                                thickness = 0.5.dp,
                                color = Stone200.copy(alpha = 0.55f),
                            )
                        }
                    }
                    // 假分页哨兵：滚到底自动追加下一页
                    if (visibleCount < filtered.size) {
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