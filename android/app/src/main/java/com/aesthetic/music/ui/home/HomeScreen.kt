package com.aesthetic.music.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.BottomContentSpacer

/**
 * 首页：只保留顶部 Header 与今日推荐 Banner。
 *
 * 各区块 UI 拆分到同包下：HomeHeader / TodayFeaturedCard / HomeCommon。
 */
@Composable
fun HomeScreen(
    dailyPlaylists: List<PlaylistItem>,
    newSongs: List<Song>,
    hotSongs: List<Song>,
    hotPlaylists: List<PlaylistItem>,
    ranks: List<PlaylistItem>,
    homeCategories: List<HomeCategoryDto> = emptyList(),
    favorites: Set<String>,
    isLoading: Boolean = false,
    contentError: String? = null,
    onRetryContent: (() -> Unit)? = null,
    onPlaySong: (Song, List<Song>) -> Unit,
    onSearchBarClick: () -> Unit,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
    onOpenSheet: () -> Unit,
    onOpenSheetCategory: (String) -> Unit,
    onOpenRanksPage: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
) {
    // 候选歌曲池（优先新歌、热歌），去重防止下游 key 冲突
    val poolSongs = remember(newSongs, hotSongs) {
        (if (newSongs.isNotEmpty()) newSongs else hotSongs)
            .distinctBy { it.id }
    }

    // 今日精选歌单 / 歌曲
    val featuredPlaylist = dailyPlaylists.firstOrNull() ?: hotPlaylists.firstOrNull()

    // 全部数据区块为空的判定：用于首次加载态与失败横幅
    val hasNoContent = featuredPlaylist == null &&
            poolSongs.isEmpty() &&
            hotPlaylists.isEmpty() &&
            ranks.isEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 20.dp,
            start = 20.dp,
            end = 20.dp,
            bottom = BottomContentSpacer,
        ),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // 1. 顶部 Header 与精致搜索框
        item(key = "header_and_search") {
            HomeHeader(onSearchBarClick = onSearchBarClick)
        }

        // 首次加载：全部数据区块为空时显示加载态
        if (isLoading && hasNoContent) {
            item(key = "home_loading") {
                HomeLoadingState()
            }
        }

        // 内容加载失败横幅
        if (contentError != null && featuredPlaylist == null && poolSongs.isEmpty()) {
            item(key = "home_error_banner") {
                HomeErrorBanner(message = contentError, onRetry = onRetryContent)
            }
        }

        // 2. 今日推荐 Banner 卡片
        if (featuredPlaylist != null || poolSongs.isNotEmpty()) {
            item(key = "today_featured") {
                TodayFeaturedCard(
                    playlist = featuredPlaylist,
                    fallbackSong = poolSongs.firstOrNull(),
                    onClick = {
                        if (featuredPlaylist != null) {
                            onOpenPlaylistDetail(featuredPlaylist)
                        } else if (poolSongs.isNotEmpty()) {
                            onPlaySong(poolSongs.first(), poolSongs)
                        }
                    }
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(12.dp))
        }
    }
}