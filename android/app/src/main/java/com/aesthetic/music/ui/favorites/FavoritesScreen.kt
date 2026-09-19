package com.aesthetic.music.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.BottomContentPadding
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ListSearchBar
import com.aesthetic.music.ui.components.QuadCover
import com.aesthetic.music.ui.components.SelectionBottomSheet
import com.aesthetic.music.ui.components.SelectionItem
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.WaveBars
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextMuted
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
 * 我的收藏列表页（参考现代卡片式设计重塑）：
 * 1. 顶部大卡片：2x2四宫格封面 + 标题 + 排序下拉胶囊 + 搜索图标 + 3列统计 + 播放全部/随机播放双按钮；
 * 2. 排序 BottomSheet 弹窗；
 * 3. 歌曲列表白底圆角容器 + 播放中波形动画高亮 + 更多操作弹窗。
 */
@Composable
fun FavoritesScreen(
    songs: List<Song>,
    currentSong: Song?,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onRemoveFavorite: (Song) -> Unit,
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

    // 排序处理
    val sortedSongs = remember(songs, sortOrder) {
        when (sortOrder) {
            FavoriteSortOrder.RECENT -> songs
            FavoriteSortOrder.EARLIEST -> songs.reversed()
            FavoriteSortOrder.TITLE -> songs.sortedBy { it.title.lowercase() }
            FavoriteSortOrder.ARTIST -> songs.sortedBy { it.artist.lowercase() }
        }
    }

    // 页内搜索过滤
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

    // 统计数据
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
            // ===== 1. 顶部返回栏（轻量导航） =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ===== 2. 顶部汇总大卡片 =====
            FavoritesHeaderCard(
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
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(14.dp))

            // ===== 2.5 搜索栏展开 =====
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

            // ===== 3. 列表标题栏 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "全部歌曲",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                )
                Text(
                    text = "${filtered.size} 首",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextSoft,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ===== 4. 歌曲列表容器 =====
            if (songs.isEmpty()) {
                EmptyBox("还没有收藏的歌曲\n在歌曲列表点击 ♡ 即可收藏")
            } else if (filtered.isEmpty()) {
                EmptyBox("没有找到与「${searchQuery.trim()}」相关的歌曲")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Surface),
                        contentPadding = BottomContentPadding
                    ) {
                        itemsIndexed(
                            filtered,
                            key = { _, s -> s.platform + ":" + s.id },
                            contentType = { _, _ -> "favorite_song" },
                        ) { index, song ->
                            val isCurrent = currentSong != null &&
                                    song.id == currentSong.id && song.platform == currentSong.platform

                            FavoriteSongRowItem(
                                song = song,
                                isCurrent = isCurrent,
                                isPlaying = isPlaying && isCurrent,
                                onClick = { onPlaySong(song, filtered) },
                                onMore = { onMoreSong(song) },
                            )

                            if (index < filtered.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                                    thickness = 0.6.dp,
                                    color = Stone200.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== 排序抽屉 =====
    if (showSortSheet) {
        val sortOptions = listOf(
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
        )

        SelectionBottomSheet(
            headerIcon = Icons.AutoMirrored.Filled.Sort,
            title = "收藏排序",
            subtitle = "当前：${sortOrder.title}",
            items = sortOptions,
            selectedId = sortOrder,
            onSelect = { sortOrder = it },
            onDismissRequest = { showSortSheet = false }
        )
    }
}

/**
 * 顶部 Hero 大卡片（对应参考图 1）
 */
@Composable
private fun FavoritesHeaderCard(
    covers: List<String>,
    sortTitle: String,
    onSortClick: () -> Unit,
    onSearchClick: () -> Unit,
    songCount: Int,
    artistCount: Int,
    albumCount: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = Palette.isDark

    Column(
        modifier = modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(24.dp), elevation = 4.dp)
            .padding(18.dp)
    ) {
        // 第一行：左侧 2x2 拼图 + 标题/排序药丸 + 右侧搜索按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuadCover(
                covers = covers,
                size = 76.dp,
                corner = 16.dp,
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "我喜欢的音乐",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                // 排序下拉药丸
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceSoft)
                        .clickable(onClick = onSortClick)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = sortTitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextPrimary
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 右上角搜索小按钮
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SurfaceSoft)
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 第二行：内嵌 3 列统计卡片
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isDark) SurfaceSoft.copy(alpha = 0.5f) else Surface)
                .height(IntrinsicSize.Min)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatsColumn(
                label = "歌曲",
                value = "$songCount",
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .background(Stone200.copy(alpha = 0.45f))
            )

            StatsColumn(
                label = "歌手",
                value = "$artistCount",
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .background(Stone200.copy(alpha = 0.45f))
            )

            StatsColumn(
                label = "专辑",
                value = "$albumCount",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // 第三行：双胶囊操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：播放全部（深色背景实心胶囊）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2C2E33))
                    .clickable(onClick = onPlayAll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "播放全部",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }

            // 右：随机播放（浅白底胶囊）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) SurfaceSoft else Surface)
                    .clickable(onClick = onShuffle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "随机播放",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary
                )
            }
        }
    }
}

/**
 * 统计单列（带小圆点与加粗数值）
 */
@Composable
private fun StatsColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(TextSoft)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = TextSoft
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary
        )
    }
}

/**
 * 歌曲行（对应参考图 1）：
 * 正在播放高亮与波形，副标题智能显示“已暂停 · 歌手”或“歌手 · 专辑”，右侧三点菜单
 */
@Composable
private fun FavoriteSongRowItem(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面 + 播放状态波形指示
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            SongCover(song.artwork, Modifier.fillMaxSize(), corner = 10.dp)

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        WaveBars(color = Color.White, barWidth = 2.5.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "已暂停",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // 歌曲信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isCurrent) AccentCoral else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(3.dp))

            val subtitleText = when {
                isCurrent && !isPlaying -> "已暂停 · ${song.artist.ifBlank { "未知歌手" }}"
                isCurrent && isPlaying -> "播放中 · ${song.artist.ifBlank { "未知歌手" }}"
                else -> song.subtitleText
            }

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = if (isCurrent) AccentCoral.copy(alpha = 0.85f) else TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 右侧三点更多按钮
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "更多操作",
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}