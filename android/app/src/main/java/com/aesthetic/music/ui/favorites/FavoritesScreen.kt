package com.aesthetic.music.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
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
import com.aesthetic.music.ui.components.BottomContentPadding
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ListSearchBar
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 我的收藏列表页：按播放历史页的样式重塑。
 * 1. 大标题 + 顶部汇总卡片；
 * 2. 列表改为无外框、白底 + 分割线；
 * 3. 页内搜索过滤后，播放全部 / 随机播放 / 点击播放都以「过滤结果」为队列，和显示内容一致。
 */
@Composable
fun FavoritesScreen(
    songs: List<Song>,
    currentSong: Song?,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onRemoveFavorite: (Song) -> Unit,
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

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
        Modifier.fillMaxSize()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = statusBarTop + 16.dp)
        ) {
            // ===== 1. 顶部大标题 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onBack)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "我的收藏",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 2. 顶部汇总卡片 =====
            if (songs.isNotEmpty()) {
                FavoritesSummaryCard(
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
                    placeholder = "搜索收藏的歌曲、歌手…",
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
                    text = "心动单曲",
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
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===== 4. 歌曲列表 =====
            if (songs.isEmpty()) {
                EmptyBox("还没有收藏的歌曲\n在歌曲列表点击 ♡ 即可收藏")
            } else if (filtered.isEmpty()) {
                EmptyBox("没有找到与「${searchQuery.trim()}」相关的歌曲")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = BottomContentPadding
                ) {
                    itemsIndexed(
                        filtered,
                        key = { _, s -> s.platform + ":" + s.id },
                        contentType = { _, _ -> "favorite_song" },
                    ) { index, song ->
                        val playingNow = currentSong != null &&
                                song.id == currentSong.id && song.platform == currentSong.platform

                        FavoriteRow(
                            song = song,
                            playingNow = playingNow,
                            // 以过滤后的列表当队列：边搜边播时上一首/下一首仍留在结果内
                            onClick = { onPlaySong(song, filtered) },
                            onRemove = { onRemoveFavorite(song) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶部汇总卡片：收藏了 XX 首 + 操作按钮（iOS 玻璃拟态风格）
 */
@Composable
private fun FavoritesSummaryCard(
    songCount: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(22.dp), elevation = 6.dp)
            .padding(18.dp)
    ) {
        // 标签
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Rose.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "心动收藏",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Rose
            )
        }

        Spacer(Modifier.height(10.dp))

        // 标题
        Text(
            text = "喜欢的歌",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary
        )

        Spacer(Modifier.height(4.dp))

        // 统计信息
        Text(
            text = "共 $songCount 首心动单曲",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = TextSoft
        )

        Spacer(Modifier.height(18.dp))

        // 操作按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放全部按钮 (主按钮)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(PrimaryBrown)
                    .clickable(onClick = onPlayAll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "播放全部",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp, fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }

            Spacer(Modifier.width(12.dp))

            // 随机播放按钮
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .iosGlassCard(shape = CircleShape, elevation = 2.dp)
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

            Spacer(Modifier.width(10.dp))

            // 搜索按钮
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .iosGlassCard(shape = CircleShape, elevation = 2.dp)
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
 * 列表行：iOS 玻璃拟态胶囊卡片
 */
@Composable
private fun FavoriteRow(
    song: Song,
    playingNow: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .iosGlassCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 封面
            SongCover(song.artwork, Modifier.size(46.dp), corner = 10.dp)

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

            // 取消收藏按钮（红心）
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "取消收藏",
                    tint = Rose,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}