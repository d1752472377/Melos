package com.aesthetic.music.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.BottomContentSpacer

/**
 * 首页：按反 AI 味、高质感、明快分层原则重构
 * 1. 顶部大标题“发现好音乐”，精致轮廓搜索框；
 * 2. 今日推荐 Banner 卡片（Level 1 CTA 立即收听）；
 * 3. 随便听听（横向列表 + Level 2 描边换一批）；
 * 4. 为你推荐（横向歌单卡片）；
 * 5. 分类歌单（薄荷/青蓝/琥珀/玫瑰四色专属主题 + 点阵曲目预览）；
 * 6. 排行榜（竞技金银铜标 + 新歌/热歌 Tab 切换 + 爱心收藏交互）。
 *
 * 本文件只保留页面框架（状态 + LazyColumn 骨架），各区块 UI 拆分到同包下的：
 * HomeHeader / TodayFeaturedCard / HomeRandomSection / HomeRecommendSection /
 * HomeCategorySection / HomeLeaderboardSection / HomeCommon。
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
    // 随便听听的随机批次种子
    var randomSeed by rememberSaveable { mutableStateOf(0) }
    // 排行榜 Tab 切换：0 = 新歌, 1 = 热歌
    var rankTab by rememberSaveable { mutableStateOf(0) }

    // 候选歌曲池（优先新歌、热歌与备选歌曲），去重防止下游 key 冲突
    val poolSongs = remember(newSongs, hotSongs) {
        (if (newSongs.isNotEmpty()) newSongs else hotSongs)
            .distinctBy { it.id }
    }

    val randomDisplaySongs = remember(poolSongs, randomSeed) {
        if (poolSongs.isEmpty()) emptyList()
        else poolSongs.shuffled(java.util.Random(randomSeed.toLong())).take(10)
    }

    // 今日精选歌单 / 歌曲
    val featuredPlaylist = dailyPlaylists.firstOrNull() ?: hotPlaylists.firstOrNull()

    // 全部数据区块为空的判定：用于首次加载态与失败横幅
    val hasNoContent = featuredPlaylist == null &&
            poolSongs.isEmpty() &&
            hotPlaylists.isEmpty() &&
            ranks.isEmpty()

    // 排行榜展示歌曲（新歌 / 热歌 Tab），去重防止 key 冲突
    val rankDisplaySongs = remember(rankTab, newSongs, hotSongs, poolSongs) {
        val base = if (rankTab == 0) {
            if (newSongs.isNotEmpty()) newSongs else poolSongs
        } else {
            if (hotSongs.isNotEmpty()) hotSongs else poolSongs
        }
        base.distinctBy { it.id }.take(5)
    }

    // 为你推荐（横向长歌单卡片），去重防止 key 冲突
    val recommendList = remember(dailyPlaylists, hotPlaylists) {
        (if (dailyPlaylists.size > 1) dailyPlaylists.drop(1) else hotPlaylists)
            .distinctBy { it.id }
    }

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

        // 首次加载：全部数据区块为空时显示加载态（LazyColumn item 内用固定高度适配，而非 fillMaxSize）
        if (isLoading && hasNoContent) {
            item(key = "home_loading") {
                HomeLoadingState()
            }
        }

        // 内容加载失败横幅：全部区块为空时给出原因与重试入口（原先失败即各区块静默消失）
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

        // 3. 随便听听（横向列表 + Level 2 描边换一批按钮）
        if (randomDisplaySongs.isNotEmpty()) {
            item(key = "random_section") {
                HomeRandomSection(
                    songs = randomDisplaySongs,
                    onRegenerate = { randomSeed++ },
                    onPlaySong = onPlaySong,
                )
            }
        }

        // 4. 为你推荐（横向长歌单卡片）
        if (recommendList.isNotEmpty()) {
            item(key = "recommend_section") {
                HomeRecommendSection(
                    playlists = recommendList,
                    onOpenPlaylistDetail = onOpenPlaylistDetail,
                )
            }
        }

        // 5. 分类歌单（带 3 首预览与金句文案，点“更多”进歌单广场）
        item(key = "category_section") {
            HomeCategorySection(
                homeCategories = homeCategories,
                hotPlaylists = hotPlaylists,
                poolSongs = poolSongs,
                onOpenPlaylistDetail = onOpenPlaylistDetail,
                onOpenSheet = onOpenSheet,
                onOpenSheetCategory = onOpenSheetCategory,
            )
        }

        // 6. 排行榜（卡片容器：新歌 / 热歌 Tab 切换 + 收藏心标）
        if (rankDisplaySongs.isNotEmpty()) {
            item(key = "leaderboard_section") {
                HomeLeaderboardSection(
                    selectedTab = rankTab,
                    onSelectTab = { rankTab = it },
                    songs = rankDisplaySongs,
                    favorites = favorites,
                    onPlaySong = onPlaySong,
                    onToggleFavorite = onToggleFavorite,
                    onOpenRanksPage = onOpenRanksPage,
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(12.dp))
        }
    }
}