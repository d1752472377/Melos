package com.aesthetic.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.rememberAppCardTheme
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 首页：
 * 1. 顶部 Header 与搜索框
 * 2. 今日推荐 Banner
 * 3. 快捷入口卡片（排行榜、随心听、分类歌单）
 * 4. 为你推荐精选卡片（展示名称、图片、创建者、3首音乐、总歌曲数、分类）
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
    onOpenFm: () -> Unit = {},
    onToggleFavorite: (Song) -> Unit,
) {
    // 候选歌曲池（优先新歌、热歌），去重防止下游 key 冲突
    val poolSongs = remember(newSongs, hotSongs) {
        (if (newSongs.isNotEmpty()) newSongs else hotSongs)
            .distinctBy { it.id }
    }

    // 今日精选歌单 / 歌曲
    val featuredPlaylist = dailyPlaylists.firstOrNull() ?: hotPlaylists.firstOrNull()

    // 为你推荐选取的歌单（优先取第2个每日歌单或第1个热门歌单）
    val recommendPlaylist = (if (dailyPlaylists.size > 1) dailyPlaylists[1] else hotPlaylists.firstOrNull()) ?: featuredPlaylist

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
        verticalArrangement = Arrangement.spacedBy(20.dp)
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

        // 3. 快捷入口卡片（排行榜、随心听、分类歌单）
        item(key = "quick_features") {
            QuickFeaturesCard(
                onOpenRanks = onOpenRanksPage,
                onOpenFm = onOpenFm,
                onOpenCategory = onOpenSheet,
            )
        }

        // 4. 为你推荐精选卡片（名称、图片、创建者、3首音乐预览、歌曲总数、分类）
        if (recommendPlaylist != null) {
            item(key = "featured_recommend") {
                FeaturedRecommendPlaylistCard(
                    playlist = recommendPlaylist,
                    previewSongs = poolSongs.take(3),
                    onOpenPlaylistDetail = onOpenPlaylistDetail,
                    onPlaySong = onPlaySong,
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 快捷入口卡片：排行榜、随心听、分类歌单 */
@Composable
private fun QuickFeaturesCard(
    onOpenRanks: () -> Unit,
    onOpenFm: () -> Unit,
    onOpenCategory: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(vertical = 16.dp, horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickFeatureItem(
                icon = Icons.Filled.BarChart,
                iconTint = AccentAmber,
                iconBg = AccentAmber.copy(alpha = 0.12f),
                title = "排行榜",
                subtitle = "热歌飙升",
                onClick = onOpenRanks,
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Stone200.copy(alpha = 0.5f))
            )

            QuickFeatureItem(
                icon = Icons.Filled.Radio,
                iconTint = AccentCoral,
                iconBg = AccentCoral.copy(alpha = 0.12f),
                title = "随心听",
                subtitle = "心动电台",
                onClick = onOpenFm,
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Stone200.copy(alpha = 0.5f))
            )

            QuickFeatureItem(
                icon = Icons.Filled.GridOn,
                iconTint = AccentMint,
                iconBg = AccentMint.copy(alpha = 0.12f),
                title = "分类歌单",
                subtitle = "风格精选",
                onClick = onOpenCategory,
            )
        }
    }
}

@Composable
private fun QuickFeatureItem(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = TextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
            ),
            color = TextSoft,
        )
    }
}

/** 为你推荐精选卡片 */
@Composable
private fun FeaturedRecommendPlaylistCard(
    playlist: PlaylistItem,
    previewSongs: List<Song>,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
) {
    val theme = rememberAppCardTheme(playlist.coverUrl, defaultAccent = PrimaryBrown)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .clickable { onOpenPlaylistDetail(playlist) }
            .padding(18.dp),
    ) {
        Column {
            // Header: 标签 + 分类
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "为你推荐",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = TextPrimary,
                )
                // 分类标签
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.accent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = playlist.group ?: playlist.kind ?: "精选歌单",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.accent,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 歌单主体：封面 + 名称 + 创建者 + 歌曲数
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongCover(
                    url = playlist.coverUrl,
                    modifier = Modifier.size(80.dp),
                    corner = 14.dp,
                )

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp,
                        ),
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "由 ${playlist.platform?.uppercase() ?: "Melos"} 精选 创建",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSoft,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "共 ${playlist.trackCount ?: (previewSongs.size.coerceAtLeast(20))} 首歌 · 分类：${playlist.group ?: playlist.kind ?: "流行"}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = TextMuted,
                    )
                }
            }

            // 歌单里面的三首音乐
            if (previewSongs.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Stone200.copy(alpha = 0.5f))
                )
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    previewSongs.take(3).forEachIndexed { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPlaySong(song, previewSongs) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "0${index + 1}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = theme.accent,
                                modifier = Modifier.width(26.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "播放",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}