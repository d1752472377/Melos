package com.aesthetic.music.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextFaint
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/** 平台 Tab（对应后端返回的平台字段；chksz = 网易云完整音源） */
private val PLATFORM_TABS = listOf(
    null to "全部",
    "chksz" to "网易云",
    "qq" to "QQ",
    "kugou" to "酷狗",
    "kuwo" to "酷我",
)

/**
 * 歌单中心页：
 * 1. 收藏的歌单（来自推荐/收藏的精选歌单列表）
 * 2. 我的自建歌单（支持创建、重命名、删除）
 * 3. 快捷入口（我喜欢的单曲、最近播放）
 * 4. 搜索框（输入关键词时智能切换搜索视图）
 */
@Composable
fun PlaylistScreen(
    myPlaylists: List<MyPlaylist>,
    collectedPlaylists: List<PlaylistItem>,
    favoriteSongs: List<Song>,
    history: List<Song>,
    searchResults: List<Song>,
    searchQuery: String,
    isSearching: Boolean,
    favorites: Set<String>,
    matchedArtists: List<ArtistItem> = emptyList(),
    onSearch: (String) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
    onOpenMyPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenArtist: (ArtistItem) -> Unit = {},
) {
    val showSearch = searchQuery.isNotBlank()
    // 歌单页面主 Tab：0 = 收藏的歌单, 1 = 自建歌单
    var playlistTab by remember { mutableIntStateOf(0) }
    var platformFilter by remember { mutableStateOf<String?>(null) }
    val filteredSearchResults = if (platformFilter == null) searchResults else searchResults.filter { it.platform == platformFilter }

    // 新建与重命名弹窗状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<MyPlaylist?>(null) }
    var renameText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = 24.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 150.dp // 避开底栏悬浮 Dock
            )
        ) {
            // 1. 顶栏标题与操作按钮
            item(key = "playlist_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (showSearch) "全网搜索" else "我的歌单",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (showSearch) "共找到 ${searchResults.size} 首歌曲" else "${collectedPlaylists.size} 个收藏歌单 · ${myPlaylists.size} 个自建",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft
                        )
                    }

                    if (!showSearch) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 新建歌单按钮 (Level 2 Secondary Action)
                            Row(
                                modifier = Modifier
                                    .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface)
                                    .clickable { showCreateDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "新建", tint = PrimaryBrown, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("新建", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = PrimaryBrown, fontWeight = FontWeight.SemiBold)
                            }

                            // 导入歌单按钮 (Level 2 Secondary Action)
                            Row(
                                modifier = Modifier
                                    .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface)
                                    .clickable(onClick = onOpenImport)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = "导入", tint = TextSoft, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("导入", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = TextSoft)
                            }
                        }
                    }
                }
            }

            // 2. 搜索框
            item(key = "playlist_search_bar") {
                SearchField(
                    query = searchQuery,
                    onQueryChange = onSearch,
                    isSearching = isSearching
                )
            }

            // ==================== 搜索模式 ====================
            if (showSearch) {
                // 平台 Tab 胶囊样式
                item(key = "search_platform_tabs") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PLATFORM_TABS.forEach { (platform, label) ->
                            val selected = platformFilter == platform
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) PrimaryBrown else Surface)
                                    .clickable { platformFilter = platform }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.White else TextMuted,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // 匹配歌手
                if (matchedArtists.isNotEmpty()) {
                    item(key = "search_matched_artists") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                        ) {
                            matchedArtists.forEach { artist ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenArtist(artist) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    SongCover(artist.avatar, Modifier.size(56.dp), corner = 28.dp)
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 64.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // 歌曲搜索列表
                if (filteredSearchResults.isEmpty()) {
                    item(key = "search_empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 60.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (isSearching) "正在全网检索…" else "未找到相关歌曲",
                                color = TextSoft,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else {
                    itemsIndexed(filteredSearchResults, key = { _, s -> s.platform + ":" + s.id }) { position, song ->
                        SearchSongRow(
                            index = position + 1,
                            song = song,
                            isFavorite = (song.platform + ":" + song.id) in favorites,
                            onClick = { onPlaySong(song, filteredSearchResults) },
                            onToggleFavorite = { onToggleFavorite(song) },
                        )
                    }
                }
            } else {
                // ==================== 歌单展示模式 ====================

                // 3. 快捷入口卡片（我喜欢的音乐 + 最近播放）
                item(key = "quick_entry_cards") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 我喜欢的音乐
                        QuickCard(
                            modifier = Modifier.weight(1f),
                            title = "我喜欢的音乐",
                            subtitle = "${favoriteSongs.size} 首歌曲",
                            icon = Icons.Filled.Favorite,
                            iconTint = Rose,
                            bgBrush = if (Palette.isDark) {
                                Brush.linearGradient(listOf(Color(0xFF3B1E2B), Color(0xFF26141D)))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFFFFEEF2), Color(0xFFFFF7F9)))
                            },
                            onClick = onOpenFavorites
                        )

                        // 最近播放
                        QuickCard(
                            modifier = Modifier.weight(1f),
                            title = "最近播放",
                            subtitle = "${history.size} 首歌曲",
                            icon = Icons.Filled.History,
                            iconTint = PrimaryBrown,
                            bgBrush = if (Palette.isDark) {
                                Brush.linearGradient(listOf(Color(0xFF241F38), Color(0xFF171424)))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFFF1EDFC), Color(0xFFFAF7FE)))
                            },
                            onClick = {
                                if (history.isNotEmpty()) onPlaySong(history.first(), history)
                            }
                        )
                    }
                }

                // 4. Tab 切换：收藏歌单 / 自建歌单
                item(key = "playlist_sub_tabs") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlaylistTabItem(
                            title = "收藏歌单",
                            count = collectedPlaylists.size,
                            selected = playlistTab == 0,
                            onClick = { playlistTab = 0 }
                        )
                        PlaylistTabItem(
                            title = "自建歌单",
                            count = myPlaylists.size,
                            selected = playlistTab == 1,
                            onClick = { playlistTab = 1 }
                        )
                    }
                }

                // 5. 歌单内容列表
                if (playlistTab == 0) {
                    // 收藏的歌单列表
                    if (collectedPlaylists.isEmpty()) {
                        item(key = "collected_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无收藏歌单，可在首页或发现页添加", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        items(collectedPlaylists.chunked(2), key = { it.firstOrNull()?.id ?: "" }) { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                pair.forEach { item ->
                                    CollectedPlaylistItemCard(
                                        modifier = Modifier.weight(1f),
                                        playlist = item,
                                        onClick = { onOpenPlaylistDetail(item) }
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    // 自建歌单列表
                    if (myPlaylists.isEmpty()) {
                        item(key = "my_playlists_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无自建歌单，点击右上角「新建」创建", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        items(myPlaylists, key = { it.id }) { playlist ->
                            MyPlaylistRow(
                                playlist = playlist,
                                onClick = { onOpenMyPlaylist(playlist.id) },
                                onRename = {
                                    renameTarget = playlist
                                    renameText = playlist.name
                                },
                                onDelete = { onDeletePlaylist(playlist.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 新建歌单对话框
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = { Text("新建歌单") },
            text = {
                BasicTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = TextPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Stone100)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim())
                            showCreateDialog = false
                            newPlaylistName = ""
                        }
                    }
                ) { Text("创建", color = PrimaryBrown) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newPlaylistName = ""
                    }
                ) { Text("取消", color = TextSoft) }
            }
        )
    }

    // 重命名歌单对话框
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名歌单") },
            text = {
                BasicTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = TextPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Stone100)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenamePlaylist(target.id, renameText.trim())
                            renameTarget = null
                        }
                    }
                ) { Text("保存", color = PrimaryBrown) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消", color = TextSoft) }
            }
        )
    }
}

// ==================== 子组件 ====================

@Composable
private fun QuickCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    bgBrush: Brush,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .border(1.dp, iconTint.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .shadow(1.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = 0.03f))
            .clip(RoundedCornerShape(12.dp))
            .background(bgBrush)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
                maxLines = 1
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp
                ),
                color = TextSoft,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PlaylistTabItem(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (selected) TextPrimary else TextSoft
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = if (selected) PrimaryBrown else TextFaint
            )
        }
        Spacer(Modifier.height(4.dp))
        if (selected) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(PrimaryBrown)
            )
        } else {
            Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun CollectedPlaylistItemCard(
    modifier: Modifier = Modifier,
    playlist: PlaylistItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Stone200.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            SongCover(
                url = playlist.coverUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                corner = 10.dp
            )
            if (playlist.playCount != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = formatPlayCount(playlist.playCount),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(1.dp))

        Text(
            text = "${playlist.trackCount ?: 0} 首歌曲",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp
            ),
            color = TextSoft
        )
    }
}

@Composable
private fun MyPlaylistRow(
    playlist: MyPlaylist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200, RoundedCornerShape(10.dp))
            .shadow(0.5.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongCover(
            url = playlist.coverUrl ?: playlist.songs.firstOrNull()?.artwork,
            modifier = Modifier.size(50.dp),
            corner = 8.dp
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${playlist.songs.size} 首歌曲",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp
                ),
                color = TextSoft
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRename),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "重命名", tint = TextMuted, modifier = Modifier.size(15.dp))
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = TextFaint, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .border(1.dp, Stone200, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = PrimaryBrown, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("搜索歌单、歌曲、歌手...", color = TextSoft, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (isSearching) {
                Text("…", color = TextSoft, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SearchSongRow(
    index: Int,
    song: Song,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200, RoundedCornerShape(10.dp))
            .shadow(0.5.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (index <= 3) PrimaryBrown else TextSoft,
            fontWeight = if (index <= 3) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(20.dp),
        )
        SongCover(song.artwork, Modifier.size(44.dp), corner = 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = song.subtitleText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp
                ),
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "收藏",
                tint = if (isFavorite) Rose else TextFaint,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private fun formatPlayCount(count: Long): String = when {
    count >= 100_000_000 -> String.format("%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format("%.1f万", count / 10_000.0)
    else -> count.toString()
}