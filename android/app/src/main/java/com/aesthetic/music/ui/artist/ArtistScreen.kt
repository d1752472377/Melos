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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.SongRow
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.theme.AccentCyan
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import com.aesthetic.music.ui.components.ListSearchBar
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 歌手信息卡片：头像 + 名字/别名 + 单曲数，下排是播放全部 / 随机播放 / 搜索
 * （版式参照「我的收藏」的汇总卡片）。
 */
@Composable
private fun ArtistSummaryCard(
    name: String,
    alias: String?,
    avatar: String?,
    songCount: Int,
    isLoading: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(1.5.dp, AccentCyan.copy(alpha = 0.4f), CircleShape)
                    .clip(CircleShape)
            ) {
                SongCover(avatar, Modifier.fillMaxSize(), corner = 28.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name.ifBlank { "未知歌手" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!alias.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Stone100)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = alias,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isLoading) "探索音乐库中…" else "单曲 $songCount 首",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSoft,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Background)
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
 * 歌手页：参考「我的收藏」的版式。
 *
 * 1. 顶部大标题（无返回按钮，回退交给系统手势 / 返回键）；
 * 2. 歌手信息卡片：头像 + 名字/别名 + 单曲数 + 播放全部 / 随机播放 / 搜索；
 * 3. 「热门单曲」标题 + 歌曲列表（复用 SongRow，右侧「...」打开单曲操作抽屉）；
 *    搜索时在页内过滤，列表本身样式不变。
 */
@Composable
fun ArtistScreen(
    name: String,
    alias: String?,
    avatar: String?,
    songs: List<Song>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    favorites: Set<String>,
    currentSong: Song?,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onMoreSong: (Song) -> Unit,
    onOpenAllSongs: () -> Unit,
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    // 点「搜索」展开后自动聚焦，省去再点一次输入框
    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) searchFocus.requestFocus()
    }

    // 页内过滤：搜歌名 / 歌手 / 专辑
    val visibleSongs = remember(songs, searchQuery) {
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
                .padding(top = 24.dp)
        ) {
            // ===== 1. 顶部大标题（与「我的收藏」一致，不再有返回按钮） =====
            Text(
                text = "歌手详情",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(16.dp))

            // ===== 2. 歌手信息卡片：名字/别名 + 单曲数 + 播放全部/随机播放/搜索 =====
            ArtistSummaryCard(
                name = name,
                alias = alias,
                avatar = avatar,
                songCount = songs.size,
                isLoading = isLoading,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
                onSearch = {
                    isSearchExpanded = !isSearchExpanded
                    if (!isSearchExpanded) searchQuery = ""
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // ===== 2.5 列表内搜索（点卡片的「搜索」展开） =====
            if (isSearchExpanded) {
                Spacer(Modifier.height(14.dp))
                ListSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "搜索该歌手的歌曲…",
                    focusRequester = searchFocus,
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
                    text = "热门单曲",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                )
                if (visibleSongs.isNotEmpty()) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "${visibleSongs.size} 首" else "${songs.size} 首",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = TextSoft,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))


            // 热门歌曲列表
            if (isLoading && songs.isEmpty()) {
                LoadingBox(message = "探索音乐库中…")
            } else if (!isLoading && songs.isEmpty()) {
                ErrorBox(
                    message = errorMessage ?: "暂无相关歌曲",
                    onRetry = if (errorMessage != null) onRetry else null,
                )
            } else {
                // 与搜索页一致：统一白卡，左右贴屏、底部探到屏幕底
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(Surface)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.Top,
                        contentPadding = PaddingValues(bottom = BottomContentSpacer),
                    ) {
                        itemsIndexed(
                            visibleSongs,
                            key = { i, s -> "a$i:${s.platform}:${s.id}" },
                        ) { index, song ->
                            val playingNow = currentSong != null &&
                                    song.id == currentSong.id && song.platform == currentSong.platform
                            SongRow(
                                song = song,
                                isPlaying = playingNow,
                                showPlatformTag = false,
                                onPlay = { onPlaySong(song) },
                                actions = {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable { onMoreSong(song) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreHoriz,
                                            contentDescription = "更多操作",
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                            )
                            if (index != visibleSongs.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 70.dp, end = 12.dp),
                                    thickness = 0.5.dp,
                                    color = Stone200.copy(alpha = 0.55f),
                                )
                            }
                        }
                        // 最后一项：全部演唱
                        item(key = "all_songs_footer") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenAllSongs)
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "全部演唱",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = TextSoft,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = TextSoft,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}