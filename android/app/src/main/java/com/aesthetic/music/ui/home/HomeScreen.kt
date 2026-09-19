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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 首页（iOS 玻璃拟态风格）：
 * 1. 底层 HomeAmbientGlowBackground 动态光晕画布
 * 2. 顶部 Header 与搜索框
 * 3. 快捷入口卡片（排行榜、猜你喜欢、随心听，全圆角 Squircle 拟态）
 * 4. 沉浸式场景精选推荐卡片（时段氛围 Banner + Hi-Res 徽标 + 此刻旋律曲目试听）
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
    recommendSongs: List<Song> = emptyList(),
    isLoadingRecommendSongs: Boolean = false,
    onLoadRecommendSongs: (PlaylistItem) -> Unit = {},
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

    // 推荐选取的精选歌单（优先取每日推荐歌单，次选热门歌单）
    val targetPlaylist = dailyPlaylists.firstOrNull() ?: hotPlaylists.firstOrNull()

    // 监听 targetPlaylist 变化，自动触发获取歌单内部真实歌曲
    LaunchedEffect(targetPlaylist?.id, targetPlaylist?.platform) {
        val pl = targetPlaylist
        if (pl != null && pl.id.isNotBlank()) {
            onLoadRecommendSongs(pl)
        }
    }

    // 全部数据区块为空的判定：用于首次加载态与失败横幅
    val hasNoContent = targetPlaylist == null &&
            poolSongs.isEmpty() &&
            hotPlaylists.isEmpty() &&
            ranks.isEmpty()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusBarTop + 16.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = BottomContentSpacer,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. 顶部 Header 与毛玻璃搜索框
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
            if (contentError != null && targetPlaylist == null && poolSongs.isEmpty()) {
                item(key = "home_error_banner") {
                    HomeErrorBanner(message = contentError, onRetry = onRetryContent)
                }
            }

            // 2. 快捷入口卡片（排行榜、分类歌单、随心听，完全对齐图 2）
            item(key = "quick_features") {
                QuickFeaturesCard(
                    onOpenRanks = onOpenRanksPage,
                    onOpenCategory = onOpenSheet,
                    onOpenFm = onOpenFm,
                )
            }

            // 3. 沉浸式场景推荐卡片（融合场景 Banner 与此刻旋律曲目列表）
            if (targetPlaylist != null) {
                item(key = "featured_scene_card") {
                    FeaturedScenePlaylistCard(
                        playlist = targetPlaylist,
                        previewSongs = (if (recommendSongs.isNotEmpty()) recommendSongs else poolSongs).take(3),
                        allPlaylistSongs = if (recommendSongs.isNotEmpty()) recommendSongs else poolSongs,
                        isLoadingSongs = isLoadingRecommendSongs && recommendSongs.isEmpty(),
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
}

/**
 * 快捷入口卡片：排行榜、分类歌单、随心听（对齐图 2 与 iOS 拟态）
 */
@Composable
private fun QuickFeaturesCard(
    onOpenRanks: () -> Unit,
    onOpenCategory: () -> Unit,
    onOpenFm: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(22.dp), elevation = 6.dp)
            .padding(vertical = 14.dp, horizontal = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 排行榜：天蓝底色 + 白色柱状图
            QuickFeatureItem(
                icon = Icons.Filled.BarChart,
                iconTint = Color.White,
                iconContainerBg = Color(0xFF4EA4F6),
                title = "排行榜",
                onClick = onOpenRanks,
            )

            // 半透明细分割线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(if (Palette.isDark) Color.White.copy(alpha = 0.12f) else Stone200.copy(alpha = 0.5f))
            )

            // 分类歌单：落日粉红底色 + 白色九宫格（点击直接跳转歌单广场）
            QuickFeatureItem(
                icon = Icons.Filled.GridOn,
                iconTint = Color.White,
                iconContainerBg = Color(0xFFFA6B88),
                title = "分类歌单",
                onClick = onOpenCategory,
            )

            // 半透明细分割线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(if (Palette.isDark) Color.White.copy(alpha = 0.12f) else Stone200.copy(alpha = 0.5f))
            )

            // 随心听：翠绿底色 + 白色随机箭头
            QuickFeatureItem(
                icon = Icons.Filled.Shuffle,
                iconTint = Color.White,
                iconContainerBg = Color(0xFF34C79F),
                title = "随心听",
                onClick = onOpenFm,
            )
        }
    }
}

@Composable
private fun QuickFeatureItem(
    icon: ImageVector,
    iconTint: Color,
    iconContainerBg: Color,
    title: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconContainerBg),
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
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = TextPrimary,
        )
    }
}

/**
 * 沉浸式场景精选卡片（完美对齐图 2）：
 * 上半部：沉浸式封面 Banner（带时段场景、Hi-Res 标、大标题、创建者与毛玻璃播放圆钮）
 * 下半部：此刻的旋律（歌单曲目总数、带方形封面的 3 首试听曲目、金色播放箭头）
 */
@Composable
private fun FeaturedScenePlaylistCard(
    playlist: PlaylistItem,
    previewSongs: List<Song>,
    allPlaylistSongs: List<Song> = emptyList(),
    isLoadingSongs: Boolean = false,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
) {
    val timeScene = rememberTimeScene()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(24.dp), elevation = 8.dp)
            .padding(10.dp),
    ) {
        // ==================== 上半部：沉浸式场景 Banner ====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(204.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { onOpenPlaylistDetail(playlist) },
        ) {
            // 1. 歌单背景大图
            SongCover(
                url = playlist.coverUrl,
                modifier = Modifier.fillMaxSize(),
                corner = 18.dp,
            )

            // 2. 纵向明暗渐变 Scrim（保证文字与胶囊在任何底图上都清晰可见）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.72f),
                            )
                        )
                    )
            )

            // 3. 悬浮文字与操作层
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Row: 场景与时段
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    // 左侧：场景 Mood 胶囊（小人/时段图标 + 场景大字与说明）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(timeScene.accentColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = timeScene.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = timeScene.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = timeScene.subtitle,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.5.sp,
                            )
                        }
                    }

                    // 右侧：时段胶囊（如 18:00-23:00）
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.38f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = timeScene.timeTag,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Bottom Row: Hi-Res 标 + 标题 + 创建者 + 悬浮大播放圆钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    ) {
                        // Hi-Res AUDIO 黄金小徽标
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFE5A038))
                                .padding(horizontal = 4.dp, vertical = 1.5.dp),
                        ) {
                            Text(
                                text = "Hi-Res AUDIO",
                                color = Color.Black,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.3.sp,
                            )
                        }

                        Spacer(Modifier.height(5.dp))

                        // 歌单标题
                        Text(
                            text = playlist.name,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 22.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(Modifier.height(3.dp))

                        // 创建者
                        Text(
                            text = "由 ${playlist.platform?.uppercase() ?: "Melos"} 精选 创建",
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 右侧大号毛玻璃播放按钮（点击立即播放整单）
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.32f))
                            .border(1.2.dp, Color.White.copy(alpha = 0.70f), CircleShape)
                            .clickable {
                                if (allPlaylistSongs.isNotEmpty()) {
                                    onPlaySong(allPlaylistSongs.first(), allPlaylistSongs)
                                } else if (previewSongs.isNotEmpty()) {
                                    onPlaySong(previewSongs.first(), previewSongs)
                                } else {
                                    onOpenPlaylistDetail(playlist)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "播放全部",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ==================== 下半部：此刻的旋律曲目试听 ====================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            // Header 行：左侧“此刻的旋律”，右侧“歌单共 X 首”
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "此刻的旋律",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                )
                Text(
                    text = "歌单共 ${playlist.trackCount ?: (allPlaylistSongs.size.takeIf { it > 0 } ?: 49)} 首",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSoft,
                )
            }

            Spacer(Modifier.height(8.dp))

            // 3 首歌曲预览：带封面、歌名、歌手与金色播放图标
            if (previewSongs.isNotEmpty()) {
                previewSongs.take(3).forEach { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onPlaySong(
                                    song,
                                    if (allPlaylistSongs.isNotEmpty()) allPlaylistSongs else previewSongs
                                )
                            }
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 歌曲方形封面（带圆角）
                        SongCover(
                            url = song.artwork,
                            modifier = Modifier.size(42.dp),
                            corner = 10.dp,
                        )

                        Spacer(Modifier.width(12.dp))

                        // 歌曲名与歌手
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = TextSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // 琥珀金色播放三角图标
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "播放",
                            tint = AccentAmber,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else if (isLoadingSongs) {
                // 歌单歌曲加载中提示
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = AccentAmber,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "正在加载歌单曲目…",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSoft,
                    )
                }
            }
        }
    }
}