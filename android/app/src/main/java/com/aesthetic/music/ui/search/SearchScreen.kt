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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.components.AddToPlaylistSheet
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.DangerTextButton
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.PlatformTag
import com.aesthetic.music.ui.components.SongActionSheet
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
 * 全局搜索页：
 * - 顶部搜索框，平台选择内嵌在其右端（仅影响「单曲」）；已去掉返回按钮，退出走系统返回 / 底部 Dock；
 * - 类型筛选为 tag：单曲 / 歌手 / 歌单 / 专辑 / 歌词；
 * - 未输入时展示：最近搜索（本地持久化，带二次确认的清空）+ 热门搜索（卡片 + 排序列表）；
 * - 单曲/歌词结果行右侧「...」打开单曲操作抽屉
 *   （下一首播放 / 收藏 / 下载 / 加入播放队列 / 添加到歌单）。
 *
 * 播放时按 song.platform 分流到对应音源取直链。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    currentSong: Song?,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    myPlaylists: List<MyPlaylist>,
    isSongFavorite: (Song) -> Boolean,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDownloadSong: (Song) -> Unit,
    onAddToPlaylist: (String, Song) -> Unit,
    onCreatePlaylist: (String, Song) -> Unit,
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

    // 单曲操作抽屉：点击结果行右侧「...」时记录目标歌曲
    var songForActionMenu by remember { mutableStateOf<Song?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var showClearRecentConfirm by remember { mutableStateOf(false) }

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
            // ===== 顶部：搜索框（内嵌平台选择） =====
            // 无返回按钮：退出走系统返回 / 底部 Dock（NavHost 原生接管返回栈）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, Stone200, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Surface)
                    .clickable {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                    .padding(start = 14.dp, end = 6.dp),
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
                        tint = TextMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { viewModel.onSearchQueryChanged("") },
                    )
                    Spacer(Modifier.width(6.dp))
                }
                PlatformSelector(current = platform, onSelect = viewModel::setSearchPlatform)
            }

            // 自动聚焦搜索框：仅在无关键词时聚焦（从歌手页等返回且已有结果时不再顶起键盘）
            LaunchedEffect(Unit) {
                if (query.isBlank()) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===== 1. 类型 Tab 优先置顶：单曲 歌手 歌单 专辑 歌词 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MainViewModel.SearchType.entries.forEach { type ->
                    TypeTag(
                        label = type.label,
                        selected = searchType == type,
                        onClick = { viewModel.setSearchType(type) },
                    )
                }
            }

            // 非单曲类型不涉及平台切换（平台选择器在搜索框内，仅影响单曲）
            if (searchType != MainViewModel.SearchType.SONG && query.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "该类型结果来自公共平台官方接口",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextMuted,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ===== 内容区 =====
            if (query.isBlank()) {
                // 未搜索：最近搜索 + 热门搜索
                // 可滚动：最近最多 10 条 + 热门 8 条，键盘弹起后可视区变小，不滚动会被裁掉且无法触达
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
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
                            DangerTextButton(
                                text = "清空",
                                onClick = { showClearRecentConfirm = true },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recentSearches.forEach { word ->
                                WordChip(word = word) {
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
                        HotSearchCard(
                            words = hotWords,
                            onPick = { word ->
                                viewModel.submitSearch(word)
                                keyboard?.hide()
                            },
                        )
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
                                onMore = { song -> songForActionMenu = song },
                            )
                            MainViewModel.SearchType.LYRIC -> SongResultList(
                                songs = lyricResults,
                                currentSong = currentSong,
                                emptyHint = searchError ?: "没有匹配歌词的歌曲",
                                onPlay = { song -> onPlaySong(song, lyricResults) },
                                onMore = { song -> songForActionMenu = song },
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

            // ===== 单曲操作抽屉 / 收藏到歌单抽屉 / 清空最近搜索确认 =====
            songForActionMenu?.let { song ->
                SongActionSheet(
                    song = song,
                    isFavorite = isSongFavorite(song),
                    onDismiss = { songForActionMenu = null },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onDownload = { onDownloadSong(song) },
                    onAddToPlaylist = {
                        val target = song
                        songForActionMenu = null
                        songForAddToPlaylist = target
                    },
                )
            }

            songForAddToPlaylist?.let { song ->
                AddToPlaylistSheet(
                    song = song,
                    myPlaylists = myPlaylists,
                    onDismiss = { songForAddToPlaylist = null },
                    onSelectPlaylist = { playlistId ->
                        onAddToPlaylist(playlistId, song)
                        songForAddToPlaylist = null
                    },
                    onCreateAndAdd = { newName ->
                        onCreatePlaylist(newName, song)
                        songForAddToPlaylist = null
                    },
                )
            }

            if (showClearRecentConfirm) {
                AppConfirmDialog(
                    onDismissRequest = { showClearRecentConfirm = false },
                    title = "清空最近搜索",
                    message = "将清除全部最近搜索记录。",
                    confirmText = "清空",
                    onConfirm = {
                        showClearRecentConfirm = false
                        viewModel.clearRecentSearches()
                    },
                )
            }
        }
    }
}

// ==================== 通用小组件 ====================

/** 平台筛选：短标签用于搜索框内的紧凑选择器，value 为音源 id（null = 全部平台聚合） */
private val PlatformFilters = listOf(
    "全部" to null,
    "网易" to "netease",
    "酷狗" to "kugou",
    "QQ" to "qq",
    "酷我" to "kuwo",
)

/** 最近搜索词条：圆角收到 6dp（原先 15dp 接近胶囊），文字用 TextMuted 保证对比度达标 */
@Composable
private fun WordChip(word: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, Stone200, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Stone100)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            word,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = TextMuted,
            maxLines = 1,
        )
    }
}

/**
 * 类型筛选 tag。
 *
 * 用户要求把「单曲/歌手/歌单/专辑/歌词」从实底按钮改成 tag：选中态用主色淡底 + 主色文字，
 * 未选中只留弱描边，视觉重量明显低于按钮，不再和结果行抢注意力。
 */
@Composable
private fun TypeTag(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PrimaryBrown.copy(alpha = 0.12f) else Color.Transparent)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, Stone200, RoundedCornerShape(8.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = if (selected) PrimaryBrown else TextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 搜索框内的平台选择器：显示当前平台，点击弹出下拉菜单 */
@Composable
private fun PlatformSelector(current: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = PlatformFilters.firstOrNull { it.second == current }?.first ?: PlatformFilters.first().first

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Stone100)
                .clickable { expanded = true }
                .padding(start = 9.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = if (current == null) TextMuted else PrimaryBrown,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "选择搜索平台",
                tint = TextMuted,
                modifier = Modifier.size(15.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Surface,
        ) {
            PlatformFilters.forEach { (name, value) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = if (value == current) PrimaryBrown else TextPrimary,
                            fontWeight = if (value == current) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/** 热门搜索卡片：展示为带序号的排序列表，前三名主色加粗 */
@Composable
private fun HotSearchCard(words: List<String>, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Surface),
    ) {
        words.forEachIndexed { idx, word ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(word) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${idx + 1}",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = if (idx < 3) PrimaryBrown else TextMuted,
                    fontWeight = if (idx < 3) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.width(26.dp),
                )
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (idx != words.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 14.dp),
                    thickness = 0.5.dp,
                    color = Stone200.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ==================== 结果列表 ====================

@Composable
private fun SongResultList(
    songs: List<Song>,
    currentSong: Song?,
    emptyHint: String,
    onPlay: (Song) -> Unit,
    onMore: (Song) -> Unit,
) {
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
            ResultSongRow(
                song = song,
                isPlaying = playingNow,
                onPlay = { onPlay(song) },
                onMore = { onMore(song) },
            )
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
                PlatformTag(artist.platform)
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
                PlatformTag(p.platform)
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
                PlatformTag(album.platform)
            }
        }
    }
}

/**
 * 单曲/歌词搜索结果行。
 * 封面 48dp + 15sp 标题；平台改为标题下方的彩色小标签；最右侧「...」打开单曲操作抽屉。
 */
@Composable
private fun ResultSongRow(
    song: Song,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    val subtitle = listOfNotNull(
        song.artist.ifBlank { null },
        song.album.ifBlank { null },
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isPlaying) PrimaryBrown.copy(alpha = 0.5f) else Stone200.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(song.artwork, Modifier.size(48.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isPlaying) PrimaryBrown else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!song.platform.isNullOrBlank()) {
                    PlatformTag(song.platform)
                    if (subtitle.isNotBlank()) Spacer(Modifier.width(6.dp))
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreHoriz,
                contentDescription = "更多操作",
                tint = TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
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
