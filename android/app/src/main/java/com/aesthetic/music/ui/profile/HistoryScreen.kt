package com.aesthetic.music.ui.profile

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ListSearchBar
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 最近播放页（重构样式）：
 * 1. 移除顶部回退按钮，改为大标题。
 * 2. 顶部汇总卡片：播放全部 / 随机播放（打乱顺序整队播放）/ 搜索（页内实时过滤）。
 * 3. 列表改为分割线样式，右侧三点打开单曲操作弹窗（含「移除」）。
 * 4. 搜索过滤后，播放全部 / 随机播放 / 点击播放都以「过滤结果」为队列，和显示内容一致。
 */
@Composable
fun HistoryScreen(
    songs: List<Song>,
    currentSong: Song?,
    onBack: () -> Unit, // 保留参数以防调用处报错，虽然UI不再使用
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    /** 点列表右侧三点：打开该歌曲的操作弹窗 */
    onMoreSong: (Song) -> Unit,
    onClearAll: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    // 点「搜索」展开后自动聚焦，省去再点一次输入框
    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) searchFocus.requestFocus()
    }

    // 页内过滤：搜歌名 / 歌手 / 专辑
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // 调整顶部间距，因为去掉了返回按钮
                .padding(top = 24.dp)
        ) {
            // ===== 1. 顶部大标题 =====
            Text(
                text = "播放历史",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(16.dp))

            // ===== 2. 顶部汇总卡片 =====
            if (songs.isNotEmpty()) {
                HistorySummaryCard(
                    songCount = songs.size,
                    // 过滤生效时，「播放全部 / 随机播放」只作用于当前可见的结果集
                    onPlayAll = { if (filtered.isNotEmpty()) onPlayAll(filtered) },
                    onShuffle = {
                        if (filtered.isNotEmpty()) {
                            // 打乱顺序后整队播放（不改全局播放模式）
                            onPlayAll(filtered.shuffled())
                        }
                    },
                    onSearch = {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded) searchQuery = ""
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

            // ===== 2.5 列表内搜索（点汇总卡片的「搜索」展开） =====
            if (isSearchExpanded) {
                ListSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "搜索播放过的歌曲、歌手…",
                    focusRequester = searchFocus,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(16.dp))
            }

            // ===== 3. 列表标题栏 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "最近播放",
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
                        modifier = Modifier.clickable { showClearConfirm = true } // 点击数字触发清空确认
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===== 4. 歌曲列表 =====
            if (songs.isEmpty()) {
                EmptyBox("还没有播放记录\n播过的歌会自动出现在这里")
            } else if (filtered.isEmpty()) {
                EmptyBox("没有找到与「${searchQuery.trim()}」相关的记录")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 移除整体间距，让分割线贴合
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(bottom = BottomContentSpacer)
                ) {
                    itemsIndexed(
                        filtered,
                        key = { _, s -> s.platform + ":" + s.id },
                        contentType = { _, _ -> "history_song" },
                    ) { index, song ->
                        val playingNow = currentSong != null &&
                                song.id == currentSong.id && song.platform == currentSong.platform

                        HistoryRow(
                            song = song,
                            playingNow = playingNow,
                            // 以过滤后的列表当队列：边搜边播时上一首/下一首仍留在结果内
                            onClick = { onPlaySong(song, filtered) },
                            onMore = { onMoreSong(song) },
                            // 最后一项不显示分割线
                            showDivider = index < filtered.size - 1
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AppConfirmDialog(
            onDismissRequest = { showClearConfirm = false },
            title = "清空最近播放",
            message = "将删除全部 ${songs.size} 条播放记录（含云端），此操作不可恢复。",
            confirmText = "清空",
            onConfirm = {
                showClearConfirm = false
                onClearAll()
            },
        )
    }
}

/**
 * 顶部汇总卡片：最近听过 XX 首 + 操作按钮
 */
@Composable
private fun HistorySummaryCard(
    songCount: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface) // 假设 Surface 是卡片背景色
            .padding(16.dp)
    ) {
        // 标签
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(PrimaryBrown.copy(alpha = 0.1f)) // 浅色背景
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "最近听过",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = PrimaryBrown
            )
        }

        Spacer(Modifier.height(8.dp))

        // 标题
        Text(
            text = "听过的歌",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary
        )

        Spacer(Modifier.height(4.dp))

        // 统计信息
        Text(
            text = "共 $songCount 首记录",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = TextSoft
        )

        Spacer(Modifier.height(16.dp))

        // 操作按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放全部按钮 (主按钮)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PrimaryBrown)
                    .clickable(onClick = onPlayAll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "播放全部",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            }

            Spacer(Modifier.width(12.dp))

            // 随机播放按钮
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Background) // 浅色圆形背景
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

            Spacer(Modifier.width(12.dp))

            // 搜索按钮
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Background)
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

/**
 * 列表行：移除卡片边框，改为白底+分割线
 */
@Composable
private fun HistoryRow(
    song: Song,
    playingNow: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    showDivider: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface) // 整行白色背景
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp), // 调整内边距
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 封面
            SongCover(song.artwork, Modifier.size(48.dp), corner = 8.dp)

            Spacer(Modifier.width(12.dp))

            // 歌曲信息
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
                Text(
                    text = song.subtitleText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 右侧三点：打开单曲操作弹窗（下一首/收藏/下载/队列/歌单/移除）
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = "更多操作",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // 分割线
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 80.dp, end = 20.dp), // 左侧留出封面宽度
                thickness = 0.5.dp,
                color = Stone200.copy(alpha = 0.5f)
            )
        }
    }
}