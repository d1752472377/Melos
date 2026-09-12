package com.aesthetic.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.components.AddToPlaylistSheet
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.SongActionSheet
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.TextMuted

/**
 * 全局搜索页：
 * - 顶部搜索框，平台选择内嵌在其右端（仅影响「单曲」）；已去掉返回按钮，退出走系统返回 / 底部 Dock；
 * - 类型筛选为 tag：单曲 / 歌手 / 歌单 / 专辑 / 歌词；
 * - 未输入时展示：最近搜索（本地持久化，带二次确认的清空）+ 热门搜索（卡片 + 排序列表）；
 * - 单曲/歌词结果行右侧「...」打开单曲操作抽屉
 *   （下一首播放 / 收藏 / 下载 / 加入播放队列 / 添加到歌单）。
 *
 * 播放时按 song.platform 分流到对应音源取直链。
 *
 * 布局说明（方案 B）：
 * - 外层 Column 不再统一加 horizontal padding，搜索框 / tab 各自加 horizontal 20dp；
 * - 结果卡片由 SearchResultLists.kt 的 ResultCard 负责贴到屏幕左右边；
 * - 底部不再统一 padding(bottom = LocalBottomBarPadding)，而是把该高度作为
 *   LazyColumn 的 contentPadding 底部留白，让白卡白底一直探到屏幕底、内容不被底栏遮挡。
 */
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
    val artistResults by viewModel.artistResults.collectAsState()
    val playlistResults by viewModel.playlistResults.collectAsState()
    val albumResults by viewModel.albumResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
//    val hotWords by viewModel.hotSearchWords.collectAsState()

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
                .padding(top = 24.dp),
        ) {
            // ===== 顶部：搜索框（内嵌平台选择），自身保留左右 20dp =====
            Box(Modifier.padding(horizontal = 20.dp)) {
                SearchInputBar(
                    query = query,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    onSubmit = { viewModel.submitSearch() },
                    platform = platform,
                    onPlatformSelect = viewModel::setSearchPlatform,
                    focusRequester = focusRequester,
                )
            }

            // 自动聚焦搜索框：仅在无关键词时聚焦（从歌手页等返回且已有结果时不再顶起键盘）
            LaunchedEffect(Unit) {
                if (query.isBlank()) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===== 1. 类型 Tab 优先置顶：单曲 歌手 歌单 专辑 歌词，自身保留左右 20dp =====
            Box(Modifier.padding(horizontal = 20.dp)) {
                SearchFilterTabs(
                    selected = searchType,
                    onSelect = viewModel::setSearchType,
                )
            }

            // 非单曲类型不涉及平台切换（平台选择器在搜索框内，仅影响单曲）
            if (searchType != SearchType.SONG && query.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "该类型结果来自公共平台官方接口",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // ===== 内容区 =====
            if (query.isBlank()) {
                // 未搜索：最近搜索 + 热门搜索（自身保留左右 20dp）
                // 可滚动：最近最多 10 条 + 热门 8 条，键盘弹起后可视区变小，不滚动会被裁掉且无法触达
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = LocalBottomBarPadding.current)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SearchRecentSearches(
                        words = recentSearches,
                        onPick = { word ->
                            viewModel.submitSearch(word)
                            keyboard?.hide()
                        },
                        onClearAll = { showClearRecentConfirm = true },
                    )
//                    SearchHotSearch(
//                        words = hotWords.map { HotSearchWord(it) },
//                        onPick = { word ->
//                            viewModel.submitSearch(word)
//                            keyboard?.hide()
//                        },
//                    )
                }
            } else {
                // 有关键词：分类型结果，卡片贴屏幕左右边、底部探到屏幕底
                Column(Modifier.fillMaxSize()) {
                    when {
                        isSearching && allResultsEmpty(searchType, songResults, artistResults, playlistResults, albumResults) -> {
                            LoadingBox(message = "搜索中…", modifier = Modifier.weight(1f).fillMaxWidth())
                        }

                        else -> when (searchType) {
                            SearchType.SONG -> SongResultList(
                                songs = songResults,
                                currentSong = currentSong,
                                emptyHint = searchError ?: "没有找到相关的单曲",
                                onPlay = { song -> onPlaySong(song, songResults) },
                                onMore = { song -> songForActionMenu = song },
                                bottomContentPadding = LocalBottomBarPadding.current,
                            )

                            SearchType.ARTIST -> ArtistResultList(
                                artists = artistResults,
                                emptyHint = searchError ?: "没有找到相关的歌手",
                                onOpen = onOpenArtist,
                                bottomContentPadding = LocalBottomBarPadding.current,
                            )
                            SearchType.PLAYLIST -> PlaylistResultList(
                                playlists = playlistResults,
                                emptyHint = searchError ?: "没有找到相关的歌单",
                                onOpen = onOpenPlaylist,
                                bottomContentPadding = LocalBottomBarPadding.current,
                            )
                            SearchType.ALBUM -> AlbumResultList(
                                albums = albumResults,
                                emptyHint = searchError ?: "没有找到相关的专辑",
                                onOpen = onOpenAlbum,
                                bottomContentPadding = LocalBottomBarPadding.current,
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

/** 当前类型下结果是否全部为空：用于「搜索中」加载态的判定 */
private fun allResultsEmpty(
    type: SearchType,
    songs: List<Song>,
    artists: List<ArtistItem>,
    playlists: List<PlaylistItem>,
    albums: List<AlbumItem>,
): Boolean = when (type) {
    SearchType.SONG -> songs.isEmpty()
    SearchType.ARTIST -> artists.isEmpty()
    SearchType.PLAYLIST -> playlists.isEmpty()
    SearchType.ALBUM -> albums.isEmpty()
}