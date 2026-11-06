package com.aesthetic.music.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SortByAlpha
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ListSearchBar
import com.aesthetic.music.ui.components.SelectionItem
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing

/** 历史排序选项 */
enum class HistorySortOrder(val title: String, val subtitle: String) {
    RECENT("最近播放", "最近听过的排在前面"),
    EARLIEST("最早播放", "最早听过的排在前面"),
    TITLE("歌曲名称", "按歌曲名称升序排列"),
}

/**
 * 播放历史页：
 * 1. 顶部大卡片：2x2 四宫格封面 + 标题 + 排序下拉胶囊 + 搜索图标 + 3 列统计 + 双操作按钮；
 * 2. 排序 BottomSheet 弹窗；
 * 3. 歌曲列表白底圆角容器 + 正在播放波形指示 + 更多操作。
 *
 * 公共骨架见 [com.aesthetic.music.ui.components.SongCollection]；本页只保留标题、清空入口、排序项与空态文案。
 */
@Composable
fun HistoryScreen(
    songs: List<Song>,
    currentSong: Song?,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onMoreSong: (Song) -> Unit,
    onClearAll: () -> Unit,
    isPlaying: Boolean = false,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    var sortOrder by remember { mutableStateOf(HistorySortOrder.RECENT) }
    var showSortSheet by remember { mutableStateOf(false) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) searchFocus.requestFocus()
    }

    val sortedSongs = remember(songs, sortOrder) {
        when (sortOrder) {
            HistorySortOrder.RECENT -> songs
            HistorySortOrder.EARLIEST -> songs.reversed()
            HistorySortOrder.TITLE -> songs.sortedBy { it.title.lowercase() }
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
                .padding(top = statusBarTop + AppSpacing.lg)
        ) {
            SongCollectionTopBar(
                onBack = onBack,
                trailing = if (songs.isNotEmpty()) {
                    {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { showClearConfirm = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "清空历史",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else null,
            )

            Spacer(Modifier.height(AppSpacing.sm))

            SongCollectionHeaderCard(
                title = "播放历史",
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
                modifier = Modifier.padding(horizontal = AppSpacing.page),
            )

            Spacer(Modifier.height(AppSpacing.xl))

            if (isSearchExpanded) {
                ListSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "搜索播放历史、歌手、专辑…",
                    focusRequester = searchFocus,
                    modifier = Modifier.padding(horizontal = AppSpacing.page),
                )
                Spacer(Modifier.height(AppSpacing.xl))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "全部记录",
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

            Spacer(Modifier.height(AppSpacing.md))

            if (songs.isEmpty()) {
                EmptyBox("暂无播放记录\n去播放你喜欢的音乐吧")
            } else if (filtered.isEmpty()) {
                EmptyBox("没有找到与「${searchQuery.trim()}」相关的播放记录")
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
                        .padding(horizontal = AppSpacing.page),
                    listKey = { index, song -> "${song.platform}:${song.id}:$index" },
                    contentType = { _, _ -> "history_song" },
                )
            }
        }
    }

    if (showClearConfirm) {
        AppConfirmDialog(
            onDismissRequest = { showClearConfirm = false },
            title = "清空播放历史",
            message = "将删除全部 ${songs.size} 条播放记录，此操作不可恢复。",
            confirmText = "清空",
            onConfirm = {
                showClearConfirm = false
                onClearAll()
            },
        )
    }

    if (showSortSheet) {
        SongCollectionSortSheet(
            title = "历史排序",
            currentTitle = sortOrder.title,
            items = listOf(
                SelectionItem(
                    id = HistorySortOrder.RECENT,
                    title = HistorySortOrder.RECENT.title,
                    subtitle = HistorySortOrder.RECENT.subtitle,
                    icon = Icons.Filled.AccessTime,
                ),
                SelectionItem(
                    id = HistorySortOrder.EARLIEST,
                    title = HistorySortOrder.EARLIEST.title,
                    subtitle = HistorySortOrder.EARLIEST.subtitle,
                    icon = Icons.Filled.History,
                ),
                SelectionItem(
                    id = HistorySortOrder.TITLE,
                    title = HistorySortOrder.TITLE.title,
                    subtitle = HistorySortOrder.TITLE.subtitle,
                    icon = Icons.Filled.SortByAlpha,
                ),
            ),
            selectedId = sortOrder,
            onSelect = { sortOrder = it },
            onDismissRequest = { showSortSheet = false },
        )
    }
}
