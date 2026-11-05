package com.aesthetic.music.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SortByAlpha
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ListSearchBar
import com.aesthetic.music.ui.components.SelectionItem
import com.aesthetic.music.ui.components.SongCollectionHeaderCard
import com.aesthetic.music.ui.components.SongCollectionList
import com.aesthetic.music.ui.components.SongCollectionSortSheet
import com.aesthetic.music.ui.components.SongCollectionTopBar
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/** 收藏歌曲排序方式 */
enum class FavoriteSortOrder(val title: String, val subtitle: String) {
    RECENT("最近收藏", "最近收藏的排在前面"),
    EARLIEST("最早收藏", "最早收藏的排在前面"),
    TITLE("歌曲名称", "按歌曲名称升序排列"),
    ARTIST("歌手名称", "按歌手名称升序排列"),
}

/**
 * 我的收藏列表页：
 * 1. 顶部大卡片：2x2 四宫格封面 + 标题 + 排序下拉胶囊 + 搜索图标 + 3 列统计 + 播放全部/随机播放；
 * 2. 排序 BottomSheet 弹窗；
 * 3. 歌曲列表白底圆角容器 + 播放中波形动画高亮 + 更多操作弹窗。
 *
 * 公共骨架见 [com.aesthetic.music.ui.components.SongCollection]；本页只保留标题、排序项与空态文案。
 */
@Composable
fun FavoritesScreen(
    songs: List<Song>,
    currentSong: Song?,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    isPlaying: Boolean = false,
    onMoreSong: (Song) -> Unit = {},
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    var sortOrder by remember { mutableStateOf(FavoriteSortOrder.RECENT) }
    var showSortSheet by remember { mutableStateOf(false) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 搜索展开自动聚焦
    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) searchFocus.requestFocus()
    }

    val sortedSongs = remember(songs, sortOrder) {
        when (sortOrder) {
            FavoriteSortOrder.RECENT -> songs
            FavoriteSortOrder.EARLIEST -> songs.reversed()
            FavoriteSortOrder.TITLE -> songs.sortedBy { it.title.lowercase() }
            FavoriteSortOrder.ARTIST -> songs.sortedBy { it.artist.lowercase() }
        }
    }

    val filtered = remember(sortedSongs, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            sortedSongs
        } else {
            sortedSongs.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
            }
        }
    }

    val artistCount = remember(songs) {
        songs.map { it.artist.trim() }.filter { it.isNotBlank() }.distinct().size
    }
    val albumCount = remember(songs) {
        songs.map { it.album.trim() }.filter { it.isNotBlank() }.distinct().size
    }
    val topCovers = remember(songs) {
        songs.mapNotNull { it.artwork.takeIf { art -> !art.isNullOrBlank() } }.take(4)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = statusBarTop + 12.dp)
        ) {
            SongCollectionTopBar(onBack = onBack)

            Spacer(Modifier.height(8.dp))

            SongCollectionHeaderCard(
                title = "我喜欢的音乐",
                covers = topCovers,
                sortTitle = sortOrder.title,
                onSortClick = { showSortSheet = true },
                onSearchClick = {
                    isSearchExpanded = !isSearchExpanded
                    if (!isSearchExpanded) searchQuery = ""
                },
                songCount = songs.size,
                artistCount = artistCount,
                albumCount = albumCount,
                onPlayAll = { if (filtered.isNotEmpty()) onPlayAll(filtered) },
                onShuffle = { if (filtered.isNotEmpty()) onPlayAll(filtered.shuffled()) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(14.dp))

            if (isSearchExpanded) {
                ListSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "搜索我喜欢的音乐、歌手、专辑…",
                    focusRequester = searchFocus,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(14.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "全部歌曲",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                )
                Text(
                    text = "${filtered.size} 首",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = TextSoft,
                )
            }

            Spacer(Modifier.height(10.dp))

            if (songs.isEmpty()) {
                EmptyBox("还没有收藏的歌曲\n在歌曲列表点击 ♡ 即可收藏")
            } else if (filtered.isEmpty()) {
                EmptyBox("没有找到与「${searchQuery.trim()}」相关的歌曲")
            } else {
                SongCollectionList(
                    songs = filtered,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onPlaySong = onPlaySong,
                    onMoreSong = onMoreSong,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                )
            }
        }
    }

    if (showSortSheet) {
        SongCollectionSortSheet(
            title = "收藏排序",
            currentTitle = sortOrder.title,
            items = listOf(
                SelectionItem(
                    id = FavoriteSortOrder.RECENT,
                    title = FavoriteSortOrder.RECENT.title,
                    subtitle = FavoriteSortOrder.RECENT.subtitle,
                    icon = Icons.Filled.AccessTime,
                ),
                SelectionItem(
                    id = FavoriteSortOrder.EARLIEST,
                    title = FavoriteSortOrder.EARLIEST.title,
                    subtitle = FavoriteSortOrder.EARLIEST.subtitle,
                    icon = Icons.Filled.History,
                ),
                SelectionItem(
                    id = FavoriteSortOrder.TITLE,
                    title = FavoriteSortOrder.TITLE.title,
                    subtitle = FavoriteSortOrder.TITLE.subtitle,
                    icon = Icons.Filled.SortByAlpha,
                ),
                SelectionItem(
                    id = FavoriteSortOrder.ARTIST,
                    title = FavoriteSortOrder.ARTIST.title,
                    subtitle = FavoriteSortOrder.ARTIST.subtitle,
                    icon = Icons.Filled.Person,
                ),
            ),
            selectedId = sortOrder,
            onSelect = { sortOrder = it },
            onDismissRequest = { showSortSheet = false },
        )
    }
}
