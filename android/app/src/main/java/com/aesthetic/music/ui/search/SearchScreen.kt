package com.aesthetic.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
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
import com.aesthetic.music.ui.search.SearchController
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.AppSpacing

@Composable
fun SearchScreen(
    search: SearchController,
    currentSong: Song?,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    onMoreSong: (Song) -> Unit,
    openRecognition: Boolean = false,
    onRecognitionOpened: () -> Unit = {},
    onOpenRecognition: () -> Unit = {},
) {
    val query by search.query.collectAsState()
    val searchType by search.type.collectAsState()
    val platform by search.platform.collectAsState()
    val songResults by search.songResults.collectAsState()
    val artistResults by search.artistResults.collectAsState()
    val playlistResults by search.playlistResults.collectAsState()
    val albumResults by search.albumResults.collectAsState()
    val isSearching by search.isSearching.collectAsState()
    val searchError by search.searchError.collectAsState()
    val recentSearches by search.recentSearches.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    var showClearRecentConfirm by remember { mutableStateOf(false) }

    // 首页麦克风投递过来的"请打开识曲"信号：打开一次并清掉
    LaunchedEffect(openRecognition) {
        if (openRecognition) {
            onRecognitionOpened()
            onOpenRecognition()
        }
    }

    Box(
        Modifier.fillMaxSize()
    ) {
        HomeAmbientGlowBackground()
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = statusBarTop + AppSpacing.xxl),
        ) {
            Box(Modifier.padding(horizontal = AppSpacing.page)) {
                SearchInputBar(
                    query = query,
                    onQueryChange = search::onQueryChanged,
                    onSubmit = { search.submit() },
                    platform = platform,
                    onPlatformSelect = search::setPlatform,
                    focusRequester = focusRequester,
                    onOpenRecognition = {
                        keyboard?.hide()
                        onOpenRecognition()
                    },
                )
            }

            LaunchedEffect(Unit) {
                // 从首页麦克风进来时是要识曲，不该同时把输入法弹出来
                if (query.isBlank() && !openRecognition) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            Box(Modifier.padding(horizontal = AppSpacing.page)) {
                SearchFilterTabs(
                    selected = searchType,
                    onSelect = search::setType,
                )
            }

            if (searchType != SearchType.SONG && query.isNotBlank()) {
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    "该类型结果来自公共平台官方接口",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = AppSpacing.page),
                )
            }

            Spacer(Modifier.height(AppSpacing.md))

            if (query.isBlank()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppSpacing.page)
                        .padding(bottom = BottomContentSpacer)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SearchRecentSearches(
                        words = recentSearches,
                        onPick = { word ->
                            search.submit(word)
                            keyboard?.hide()
                        },
                        onClearAll = { showClearRecentConfirm = true },
                    )
                }
            } else {
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
                                onMore = onMoreSong,
                                bottomContentPadding = BottomContentSpacer,
                            )

                            SearchType.ARTIST -> ArtistResultList(
                                artists = artistResults,
                                emptyHint = searchError ?: "没有找到相关的歌手",
                                onOpen = onOpenArtist,
                                bottomContentPadding = BottomContentSpacer,
                            )
                            SearchType.PLAYLIST -> PlaylistResultList(
                                playlists = playlistResults,
                                emptyHint = searchError ?: "没有找到相关的歌单",
                                onOpen = onOpenPlaylist,
                                bottomContentPadding = BottomContentSpacer,
                            )
                            SearchType.ALBUM -> AlbumResultList(
                                albums = albumResults,
                                emptyHint = searchError ?: "没有找到相关的专辑",
                                onOpen = onOpenAlbum,
                                bottomContentPadding = BottomContentSpacer,
                            )
                        }
                    }
                }
            }

            if (showClearRecentConfirm) {
                AppConfirmDialog(
                    onDismissRequest = { showClearRecentConfirm = false },
                    title = "清空最近搜索",
                    message = "将清除全部最近搜索记录。",
                    confirmText = "清空",
                    onConfirm = {
                        showClearRecentConfirm = false
                        search.clearRecent()
                    },
                )
            }

        }
    }
}

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