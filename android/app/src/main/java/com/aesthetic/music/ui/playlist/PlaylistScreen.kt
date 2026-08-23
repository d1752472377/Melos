package com.aesthetic.music.ui.playlist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.Surface
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

@Composable
fun PlaylistScreen(
    history: List<Song>,
    searchResults: List<Song>,
    searchQuery: String,
    isSearching: Boolean,
    favorites: Set<String>,
    matchedArtists: List<ArtistItem> = emptyList(),
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenImport: () -> Unit,
    onOpenArtist: (ArtistItem) -> Unit = {},
) {
    val showSearch = searchQuery.isNotBlank()
    val baseList = if (showSearch) searchResults else history
    var platformFilter by remember { mutableStateOf<String?>(null) }
    val list = if (platformFilter == null) baseList else baseList.filter { it.platform == platformFilter }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 28.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 150.dp // 避开底栏悬浮 Dock
            )
        ) {
            // 顶栏
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (showSearch) "搜索结果 (${baseList.size})" else "听过的歌 (${baseList.size}首)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    CircleButton(Icons.Filled.Add, onClick = onOpenImport)
                }
                Spacer(Modifier.height(14.dp))
            }

            // 搜索框
            item {
                SearchField(
                    query = searchQuery,
                    onQueryChange = { onSearch(it) },
                    isSearching = isSearching,
                )
                Spacer(Modifier.height(10.dp))
            }

            // 平台 Tab 胶囊样式
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PLATFORM_TABS.forEach { (platform, label) ->
                        val selected = platformFilter == platform
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) PrimaryBrown else Surface)
                                .clickable { platformFilter = platform }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else TextMuted,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // 匹配歌手（搜索时后端命中的歌手，点击进歌手页）
            if (showSearch && matchedArtists.isNotEmpty()) {
                item {
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
                                    artist.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 64.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            // 歌曲列表或空状态
            if (list.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isSearching) "搜索中…" else "暂无结果",
                            color = TextSoft,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                itemsIndexed(list) { position, song ->
                    PlaylistRow(
                        index = position + 1,
                        song = song,
                        isFavorite = (song.platform + ":" + song.id) in favorites,
                        onClick = { onPlaySong(song) },
                        onToggleFavorite = { onToggleFavorite(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
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
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = TextSoft, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("搜索歌名、歌手...", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
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
private fun PlaylistRow(
    index: Int,
    song: Song,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (index <= 3) PrimaryBrown else TextSoft,
            fontWeight = if (index <= 3) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(24.dp),
        )
        SongCover(song.artwork, Modifier.size(46.dp), corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                song.subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "收藏",
                tint = if (isFavorite) Rose else TextSoft,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}