package com.aesthetic.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextFaint
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 全局搜索页（独立页面，不再寄生在歌单 Tab）：
 * - 顶部搜索框（自动聚焦，IME 搜索键立即提交）；
 * - 平台切换：全部平台 / 网易云 / 酷狗 / QQ / 酷我（仅单曲 Tab 生效）；
 * - 类型 Tab：单曲 / 歌手 / 歌单 / 专辑 / 歌词；
 * - 未输入时展示：最近搜索（本地持久化）+ 热门搜索（netease 官方热搜）。
 *
 * 单曲/歌词结果每行带平台徽标：播放时按 song.platform 分流到对应音源取直链。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    currentSong: Song?,
    onClose: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
) {
    val query by viewModel.searchQuery.collectAsState()
    val searchType by viewModel.searchType.collectAsState()
    val platform by viewModel.searchPlatform.collectAsState()
    val songResults by viewModel.songResults.collectAsState()
    val lyricResults by viewModel.lyricResults.collectAsState()
    val artistResults by viewModel.artistResults.collectAsState()
    val playlistResults by viewModel.playlistResults.collectAsState()
    val albumResults by viewModel.albumResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val hotWords by viewModel.hotSearchWords.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp)
                .padding(bottom = LocalBottomBarPadding.current),
        ) {
            // ===== 顶部：返回 + 搜索框 =====
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .clickable {
                            keyboard?.hide()
                            onClose()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .border(1.dp, Stone200, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = PrimaryBrown,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = query,
                            onValueChange = viewModel::onSearchQueryChanged,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = TextPrimary,
                            ),
                            cursorBrush = SolidColor(PrimaryBrown),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    viewModel.submitSearch()
                                    keyboard?.hide()
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                        if (query.isEmpty()) {
                            Text(
                                "歌名 / 歌手 / 歌单 / 专辑 / 歌词",
                                color = TextSoft,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            )
                        }
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "清空",
                            tint = TextSoft,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.onSearchQueryChanged("") },
                        )
                    }
                }
            }

            // 自动聚焦搜索框
            // 仅在无关键词时自动聚焦：从歌手页等返回且已有搜索结果时不再顶起键盘。
            // requestFocus 在 FocusRequester 尚未挂载到时抛 IllegalStateException（点击搜索框
            // 触发闪退的常见根因），此处做竞态防御：挂载失败就静默放弃，不打断页面呈现。
            LaunchedEffect(Unit) {
                if (query.isBlank()) {
                    try {
                        focusRequester.requestFocus()
                    } catch (_: IllegalStateException) {
                        // 焦点节点尚未就绪，忽略；用户可手动点击输入框聚焦
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===== 1. 类型 Tab 优先置顶：单曲 歌手 歌单 专辑 歌词 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MainViewModel.SearchType.entries.forEach { type ->
                    val selected = searchType == type
                    FilterChipBox(
                        label = type.label,
                        selected = selected,
                        filledSelected = true,
                        onClick = { viewModel.setSearchType(type) },
                    )
                }
            }

            // ===== 2. 仅单曲 Tab 展开平台切换（全网直连） =====
            if (searchType == MainViewModel.SearchType.SONG) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "全部平台" to null,
                        "网易云" to "netease",
                        "酷狗" to "kugou",
                        "QQ" to "qq",
                        "酷我" to "kuwo",
                    ).forEach { (label, value) ->
                        val selected = platform == value
                        FilterChipBox(
                            label = label,
                            selected = selected,
                            enabled = true,
                            onClick = { viewModel.setSearchPlatform(value) },
                        )
                    }
                }
            } else if (query.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "该类型结果来自公共平台官方接口",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextFaint,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ===== 内容区 =====
            if (query.isBlank()) {
                // 未搜索：最近搜索 + 热门搜索
                Column(Modifier.fillMaxSize()) {
                    if (recentSearches.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "最近搜索",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                ),
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "清空",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = TextSoft,
                                modifier = Modifier.clickable { viewModel.clearRecentSearches() },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recentSearches.forEach { word ->
                                WordChip(word = word, accent = false) {
                                    viewModel.submitSearch(word)
                                    keyboard?.hide()
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    if (hotWords.isNotEmpty()) {
                        Text(
                            "热门搜索",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            ),
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            hotWords.forEachIndexed { idx, word ->
                                WordChip(word = "${idx + 1}  $word", accent = idx < 3) {
                                    viewModel.submitSearch(word)
                                    keyboard?.hide()
                                }
                            }
                        }
                    }
                }
            } else {
                // 有关键词：分类型结果
                Column(Modifier.fillMaxSize()) {
                    when {
                        isSearching && allResultsEmpty(searchType, songResults, lyricResults, artistResults, playlistResults, albumResults) -> {
                            LoadingBox(message = "搜索中…", modifier = Modifier.weight(1f).fillMaxWidth())
                        }

                        else -> when (searchType) {
                            MainViewModel.SearchType.SONG -> SongResultList(
                                songs = songResults,
                                currentSong = currentSong,
                                emptyHint = searchError ?: "没有找到相关的单曲",
                                onPlay = { song -> onPlaySong(song, songResults) },
                            )
                            MainViewModel.SearchType.LYRIC -> SongResultList(
                                songs = lyricResults,
                                currentSong = currentSong,
                                emptyHint = searchError ?: "没有匹配歌词的歌曲",
                                onPlay = { song -> onPlaySong(song, lyricResults) },
                            )
                            MainViewModel.SearchType.ARTIST -> ArtistResultList(
                                artists = artistResults,
                                emptyHint = searchError ?: "没有找到相关的歌手",
                                onOpen = onOpenArtist,
                            )
                            MainViewModel.SearchType.PLAYLIST -> PlaylistResultList(
                                playlists = playlistResults,
                                emptyHint = searchError ?: "没有找到相关的歌单",
                                onOpen = onOpenPlaylist,
                            )
                            MainViewModel.SearchType.ALBUM -> AlbumResultList(
                                albums = albumResults,
                                emptyHint = searchError ?: "没有找到相关的专辑",
                                onOpen = onOpenAlbum,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 通用小组件 ====================

/** 筛选小胶囊：平台切换用描边样式、类型 Tab 用实底选中 */
@Composable
private fun FilterChipBox(
    label: String,
    selected: Boolean,
    filledSelected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> Surface
        selected && filledSelected -> PrimaryBrown
        selected -> PrimaryBrown.copy(alpha = 0.12f)
        else -> Surface
    }
    val fg = when {
        !enabled -> TextSoft.copy(alpha = 0.5f)
        selected && filledSelected -> Color.White
        selected -> PrimaryBrown
        else -> TextSoft
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(
                if (!filledSelected || !selected) {
                    Modifier.border(1.dp, Stone200, RoundedCornerShape(14.dp))
                } else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = fg)
    }
}

/** 最近/热搜词条 */
@Composable
private fun WordChip(word: String, accent: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, Stone200, RoundedCornerShape(15.dp))
            .clip(RoundedCornerShape(15.dp))
            .background(if (accent) Surface else Stone100)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            word,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = if (accent) TextPrimary else TextSoft,
            maxLines = 1,
        )
    }
}

// ==================== 结果列表 ====================

@Composable
private fun SongResultList(songs: List<Song>, currentSong: Song?, emptyHint: String, onPlay: (Song) -> Unit) {
    if (songs.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(
            songs,
            key = { i, s -> "$i:${s.platform}:${s.id}" },
            contentType = { _, _ -> "song" },
        ) { _, song ->
            val playingNow = currentSong != null &&
                song.id == currentSong.id && song.platform == currentSong.platform
            ResultSongRow(song = song, isPlaying = playingNow, onClick = { onPlay(song) })
        }
    }
}

@Composable
private fun ArtistResultList(artists: List<ArtistItem>, emptyHint: String, onOpen: (ArtistItem) -> Unit) {
    if (artists.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(artists, key = { i, a -> "$i:${a.platform}:${a.id}" }) { _, artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
                    .clickable { onOpen(artist) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!artist.avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = artist.avatar,
                        contentDescription = artist.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Stone100),
                    )
                } else {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Stone100),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TextSoft, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!artist.alias.isNullOrBlank()) {
                        Text(
                            artist.alias!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                PlatformBadge(artist.platform)
            }
        }
    }
}

@Composable
private fun PlaylistResultList(playlists: List<PlaylistItem>, emptyHint: String, onOpen: (PlaylistItem) -> Unit) {
    if (playlists.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(playlists, key = { i, p -> "$i:${p.platform}:${p.id}" }) { _, p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
                    .clickable { onOpen(p) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongCover(p.coverUrl, Modifier.size(52.dp), corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    val count = buildString {
                        p.trackCount?.let { append("${it}首") }
                        val played = formatPlayCountText(p.playCount)
                        if (played.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            // 注意：中文字符属于 Kotlin 标识符，模板必须用 ${} 包住变量名
                            append("${played}次播放")
                        }
                    }
                    if (count.isNotBlank()) {
                        Text(count, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextSoft)
                    }
                }
                PlatformBadge(p.platform)
            }
        }
    }
}

@Composable
private fun AlbumResultList(albums: List<AlbumItem>, emptyHint: String, onOpen: (AlbumItem) -> Unit) {
    if (albums.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(albums, key = { i, a -> "$i:${a.platform}:${a.id}" }) { _, album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
                    .clickable { onOpen(album) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongCover(album.coverUrl, Modifier.size(52.dp), corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        album.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(album.artist.ifBlank { null }, album.size?.let { "${it}首" })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PlatformBadge(album.platform)
            }
        }
    }
}

/** 单曲/歌词搜索结果行：封面 + 标题 + 副标题 + 平台徽标（播放时按 platform 分流音源） */
@Composable
private fun ResultSongRow(song: Song, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isPlaying) PrimaryBrown.copy(alpha = 0.5f) else Stone200.copy(alpha = 0.6f),
                RoundedCornerShape(10.dp),
            )
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(song.artwork, Modifier.size(44.dp), corner = 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = song.subtitleText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        PlatformBadge(song.platform)
    }
}

// ==================== 平台徽标 ====================

private fun platformLabel(p: String?): String = when (p) {
    "netease", "chksz" -> "网易"
    "kugou" -> "酷狗"
    "qq" -> "QQ"
    "kuwo" -> "酷我"
    null -> ""
    else -> p.take(3)
}

private fun platformColor(p: String?): Color = when (p) {
    "netease", "chksz" -> Color(0xFFC20C0C)
    "kugou" -> Color(0xFF2CA6E0)
    "qq" -> Color(0xFF31C27C)
    "kuwo" -> Color(0xFFE08A00)
    else -> Color(0xFF8893A7)
}

@Composable
private fun PlatformBadge(platform: String?) {
    if (platform.isNullOrBlank()) return
    Text(
        text = platformLabel(platform),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(platformColor(platform).copy(alpha = 0.85f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

// ==================== 杂项 ====================

@Composable
private fun EmptyHint(text: String) {
    // 统一空态组件（各结果类型文案不变；emptyHint/searchError 仍只在列表为空时传入）
    EmptyBox(message = text)
}

private fun allResultsEmpty(
    type: MainViewModel.SearchType,
    songs: List<Song>,
    lyrics: List<Song>,
    artists: List<ArtistItem>,
    playlists: List<PlaylistItem>,
    albums: List<AlbumItem>,
): Boolean = when (type) {
    MainViewModel.SearchType.SONG -> songs.isEmpty()
    MainViewModel.SearchType.LYRIC -> lyrics.isEmpty()
    MainViewModel.SearchType.ARTIST -> artists.isEmpty()
    MainViewModel.SearchType.PLAYLIST -> playlists.isEmpty()
    MainViewModel.SearchType.ALBUM -> albums.isEmpty()
}
