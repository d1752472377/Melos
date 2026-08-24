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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.rememberDominantColors
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.AccentRose
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.PrimaryGradientEnd
import com.aesthetic.music.ui.theme.PrimaryGradientStart
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextFaint
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.TextStrong

/**
 * 首页：按反 AI 味、高质感、明快分层原则重构
 * 1. 顶部大标题“发现好音乐”，精致轮廓搜索框；
 * 2. 今日推荐 Banner 卡片（Level 1 CTA 立即收听）；
 * 3. 随便听听（横向列表 + Level 2 描边换一批）；
 * 4. 为你推荐（横向歌单卡片）；
 * 5. 分类歌单（薄荷/青蓝/琥珀/玫瑰四色专属主题 + 点阵曲目预览）；
 * 6. 排行榜（竞技金银铜标 + 新歌/热歌 Tab 切换 + 爱心收藏交互）。
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
    onPlaySong: (Song, List<Song>) -> Unit,
    onSearchBarClick: () -> Unit,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
    onOpenRank: (PlaylistItem) -> Unit,
    onOpenDiscover: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
) {
    // 随便听听的随机批次种子
    var randomSeed by remember { mutableIntStateOf(0) }
    // 排行榜 Tab 切换：0 = 新歌, 1 = 热歌
    var rankTab by remember { mutableIntStateOf(0) }

    // 候选歌曲池（优先新歌、热歌与备选歌曲）
    val poolSongs = remember(newSongs, hotSongs) {
        if (newSongs.isNotEmpty()) newSongs else hotSongs
    }
    val randomDisplaySongs = remember(poolSongs, randomSeed) {
        if (poolSongs.isEmpty()) emptyList()
        else {
            val shuffled = poolSongs.shuffled(java.util.Random(randomSeed.toLong()))
            shuffled.take(10)
        }
    }

    // 今日精选歌单 / 歌曲
    val featuredPlaylist = dailyPlaylists.firstOrNull() ?: hotPlaylists.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 20.dp,
            start = 20.dp,
            end = 20.dp,
            bottom = 150.dp // 底部留出足够空间避开 MiniPlayer 与 Dock
        ),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // 1. 顶部 Header 与精致搜索框
        item(key = "header_and_search") {
            Column {
                Text(
                    text = "发现好音乐",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
                )
                SearchEntry(onClick = onSearchBarClick)
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

        // 3. 随便听听（横向列表 + Level 2 换一批按钮）
        if (randomDisplaySongs.isNotEmpty()) {
            item(key = "random_section") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "随便听听",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = TextPrimary
                        )
                        // 次级微圆角按钮
                        Row(
                            modifier = Modifier
                                .border(1.dp, Stone200, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(Surface)
                                .clickable { randomSeed++ }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "换一批",
                                tint = PrimaryBrown,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "换一批",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = TextStrong
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(randomDisplaySongs, key = { it.platform + ":" + it.id }) { song ->
                            RandomSongCard(
                                song = song,
                                onClick = { onPlaySong(song, randomDisplaySongs) }
                            )
                        }
                    }
                }
            }
        }

        // 4. 为你推荐（横向长歌单卡片）
        val recommendList = if (dailyPlaylists.size > 1) dailyPlaylists.drop(1) else hotPlaylists
        if (recommendList.isNotEmpty()) {
            item(key = "recommend_section") {
                Column {
                    Text(
                        text = "为你推荐",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = TextPrimary
                    )

                    Spacer(Modifier.height(14.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(recommendList.take(8), key = { it.id }) { playlist ->
                            RecommendPlaylistCard(
                                playlist = playlist,
                                onClick = { onOpenPlaylistDetail(playlist) }
                            )
                        }
                    }
                }
            }
        }

        // 5. 分类歌单（带 3 首预览与金句文案）
        item(key = "category_section") {
            Column {
                SectionHeader(
                    title = "分类歌单",
                    onClickMore = onOpenDiscover
                )

                Spacer(Modifier.height(14.dp))

                val categories = remember(homeCategories, hotPlaylists, poolSongs) {
                    getCategoryPlaylists(homeCategories, hotPlaylists, poolSongs)
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(categories, key = { it.title }) { category ->
                        CategoryCard(
                            category = category,
                            onClick = {
                                if (category.playlistItem != null) {
                                    onOpenPlaylistDetail(category.playlistItem)
                                } else {
                                    onOpenDiscover()
                                }
                            }
                        )
                    }
                }
            }
        }

        // 6. 排行榜（卡片容器：新歌 / 热歌 Tab 切换 + 收藏心标）
        val rankDisplaySongs = if (rankTab == 0) {
            if (newSongs.isNotEmpty()) newSongs.take(5) else poolSongs.take(5)
        } else {
            if (hotSongs.isNotEmpty()) hotSongs.take(5) else poolSongs.take(5)
        }

        if (rankDisplaySongs.isNotEmpty()) {
            item(key = "leaderboard_section") {
                Column {
                    SectionHeader(
                        title = "排行榜",
                        onClickMore = {
                            val target = if (rankTab == 0) {
                                ranks.findRank("new", "新歌")
                            } else {
                                ranks.findRank("hot", "热歌")
                            }
                            if (target != null) onOpenRank(target) else onOpenDiscover()
                        }
                    )

                    Spacer(Modifier.height(14.dp))

                    LeaderboardCard(
                        selectedTab = rankTab,
                        onSelectTab = { rankTab = it },
                        songs = rankDisplaySongs,
                        favorites = favorites,
                        onPlaySong = { song -> onPlaySong(song, rankDisplaySongs) },
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ==================== 顶部搜索栏 ====================

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color.White.copy(alpha = 0.8f),
                spotColor = Color.White.copy(alpha = 0.8f)
            )
            .border(1.dp, Stone200.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = PrimaryBrown,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "搜索歌曲、歌手、专辑...",
            color = TextSoft,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp
            )
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(19.dp)
        )
    }
}

// ==================== 今日推荐 Banner 卡片 ====================

@Composable
private fun TodayFeaturedCard(
    playlist: PlaylistItem?,
    fallbackSong: Song?,
    onClick: () -> Unit,
) {
    val title = playlist?.name ?: fallbackSong?.title ?: "粤难粤爱 | 靠拥抱取暖的季节"
    val coverUrl = playlist?.coverUrl ?: fallbackSong?.artwork

    val themeColors = rememberDominantColors(coverUrl, defaultAccent = PrimaryBrown)
    val bgGradient = Brush.horizontalGradient(listOf(themeColors.bgStart, themeColors.bgEnd))
    val cardBorder = if (Palette.isDark) Color(0xFF33304A) else Color.White.copy(alpha = 0.9f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.White,
                spotColor = Color.White
            )
            .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(bgGradient)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 今日推荐小标签 (6dp radius)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (Palette.isDark) themeColors.buttonColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.85f))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "今日推荐",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = themeColors.buttonColor,
                        letterSpacing = 0.2.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 歌单标题：大字重黑体
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.3).sp,
                        lineHeight = 24.sp
                    ),
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // 副标题：细一号灰色说明
                Text(
                    text = "为你精挑细选的音乐灵感",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextSoft,
                    maxLines = 1
                )

                Spacer(Modifier.height(18.dp))

                // Level 1 主操作 CTA 胶囊按钮（大圆角、充足呼吸感）
                Row(
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(22.dp), ambientColor = Color.White)
                        .clip(RoundedCornerShape(22.dp))
                        .background(themeColors.buttonColor)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "立即收听",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(18.dp))

            // 右侧大圆角封面
            SongCover(
                url = coverUrl,
                modifier = Modifier
                    .size(104.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                corner = 16.dp
            )
        }
    }
}

// ==================== 随便听听 ====================

@Composable
private fun RandomSongCard(
    song: Song,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(118.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        SongCover(
            url = song.artwork,
            modifier = Modifier
                .size(118.dp)
                .shadow(3.dp, RoundedCornerShape(14.dp), ambientColor = Color.White)
                .border(1.dp, Stone200.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
            corner = 14.dp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = song.artist.ifBlank { "热门推荐" },
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            ),
            color = TextSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==================== 为你推荐 ====================

@Composable
private fun RecommendPlaylistCard(
    playlist: PlaylistItem,
    onClick: () -> Unit,
) {
    val themeColors = rememberDominantColors(playlist.coverUrl, defaultAccent = PrimaryBrown)
    val bgGradient = Brush.horizontalGradient(listOf(themeColors.bgStart, themeColors.bgEnd))
    val cardBorder = if (Palette.isDark) Color(0xFF33304A) else Color.White.copy(alpha = 0.9f)

    Row(
        modifier = Modifier
            .width(250.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color.White,
                spotColor = Color.White
            )
            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(bgGradient)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(
            url = playlist.coverUrl,
            modifier = Modifier.size(64.dp),
            corner = 14.dp
        )

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${playlist.trackCount ?: 20} 首歌曲",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = TextSoft,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(8.dp))

        // 播放按钮：同色系深色大圆角
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(themeColors.buttonColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ==================== 分类歌单 ====================

private data class CategoryItem(
    val title: String,
    val coverUrl: String?,
    val previewSongs: List<String>,
    val quote: String,
    val bgLight: List<Color>,
    val bgDark: List<Color>,
    val accentColor: Color,
    val playlistItem: PlaylistItem? = null,
)

private data class CategoryTheme(
    val bgLight: List<Color>,
    val bgDark: List<Color>,
    val accentColor: Color,
)

private val CATEGORY_THEMES = mapOf(
    "pop" to CategoryTheme(
        bgLight = listOf(Color(0xFFEBFBF5), Color(0xFFD3F8E8)),
        bgDark = listOf(Color(0xFF142B23), Color(0xFF0F1E19)),
        accentColor = AccentMint,
    ),
    "edm" to CategoryTheme(
        bgLight = listOf(Color(0xFFEBF5FF), Color(0xFFD4EBFF)),
        bgDark = listOf(Color(0xFF132338), Color(0xFF0F1928)),
        accentColor = AccentCyan,
    ),
    "folk" to CategoryTheme(
        bgLight = listOf(Color(0xFFFFF7EB), Color(0xFFFFECC7)),
        bgDark = listOf(Color(0xFF332313), Color(0xFF24190E)),
        accentColor = AccentAmber,
    ),
    "rock" to CategoryTheme(
        bgLight = listOf(Color(0xFFFFEFF2), Color(0xFFFFDCE3)),
        bgDark = listOf(Color(0xFF36181F), Color(0xFF261016)),
        accentColor = AccentRose,
    ),
)

private val CATEGORY_THEME_FALLBACK = listOf(
    CATEGORY_THEMES.getValue("pop"),
    CATEGORY_THEMES.getValue("edm"),
    CATEGORY_THEMES.getValue("folk"),
    CATEGORY_THEMES.getValue("rock"),
)

private fun getCategoryPlaylists(
    remote: List<HomeCategoryDto>,
    hotPlaylists: List<PlaylistItem>,
    poolSongs: List<Song>,
): List<CategoryItem> {
    if (remote.isNotEmpty()) {
        return remote.mapIndexed { index, dto ->
            val theme = CATEGORY_THEMES[dto.id] ?: CATEGORY_THEME_FALLBACK[index % CATEGORY_THEME_FALLBACK.size]
            CategoryItem(
                title = dto.title.ifBlank { "分类" },
                coverUrl = dto.coverUrl ?: dto.playlist?.coverUrl,
                previewSongs = dto.previewSongs.take(3),
                quote = dto.quote,
                bgLight = theme.bgLight,
                bgDark = theme.bgDark,
                accentColor = theme.accentColor,
                playlistItem = dto.playlist,
            )
        }
    }
    val covers = hotPlaylists.mapNotNull { it.coverUrl }
    return listOf(
        CategoryItem(
            title = "流行",
            coverUrl = covers.getOrNull(0) ?: poolSongs.getOrNull(0)?.artwork,
            previewSongs = emptyList(),
            quote = "人生走在路上，愿你心花路放",
            bgLight = CATEGORY_THEMES.getValue("pop").bgLight,
            bgDark = CATEGORY_THEMES.getValue("pop").bgDark,
            accentColor = AccentMint,
            playlistItem = hotPlaylists.getOrNull(0)
        ),
        CategoryItem(
            title = "电音",
            coverUrl = covers.getOrNull(1) ?: poolSongs.getOrNull(1)?.artwork,
            previewSongs = emptyList(),
            quote = "跟随强劲节拍，释放自由灵魂",
            bgLight = CATEGORY_THEMES.getValue("edm").bgLight,
            bgDark = CATEGORY_THEMES.getValue("edm").bgDark,
            accentColor = AccentCyan,
            playlistItem = hotPlaylists.getOrNull(1)
        ),
        CategoryItem(
            title = "民谣",
            coverUrl = covers.getOrNull(2) ?: poolSongs.getOrNull(2)?.artwork,
            previewSongs = emptyList(),
            quote = "一把木吉他，诉说岁月温柔",
            bgLight = CATEGORY_THEMES.getValue("folk").bgLight,
            bgDark = CATEGORY_THEMES.getValue("folk").bgDark,
            accentColor = AccentAmber,
            playlistItem = hotPlaylists.getOrNull(2)
        ),
        CategoryItem(
            title = "摇滚",
            coverUrl = covers.getOrNull(3) ?: poolSongs.getOrNull(3)?.artwork,
            previewSongs = emptyList(),
            quote = "热血不灭，唱响青春回响",
            bgLight = CATEGORY_THEMES.getValue("rock").bgLight,
            bgDark = CATEGORY_THEMES.getValue("rock").bgDark,
            accentColor = AccentRose,
            playlistItem = hotPlaylists.getOrNull(3)
        )
    )
}

@Composable
private fun CategoryCard(
    category: CategoryItem,
    onClick: () -> Unit,
) {
    val themeColors = rememberDominantColors(category.coverUrl, defaultAccent = category.accentColor)
    val upperGradient = Brush.horizontalGradient(listOf(themeColors.bgStart, themeColors.bgEnd))
    val lowerBg = if (Palette.isDark) Color(0xFF181920) else Color.White
    val cardBorder = if (Palette.isDark) Color(0xFF33304A) else Color.White.copy(alpha = 0.9f)

    Column(
        modifier = Modifier
            .width(270.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = themeColors.accentColor.copy(alpha = 0.35f), // 左上角为卡片提取色
                spotColor = Color.White // 右下角为白色
            )
            .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        // 上半部分：从左到右渐变
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(upperGradient)
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            SongCover(
                url = category.coverUrl,
                modifier = Modifier.size(70.dp),
                corner = 14.dp
            )

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(themeColors.buttonColor)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.3).sp
                        ),
                        color = TextPrimary
                    )
                }

                Spacer(Modifier.height(6.dp))

                category.previewSongs.forEachIndexed { idx, line ->
                    Text(
                        text = "${idx + 1}. $line",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // 下半部分：固定底色
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(lowerBg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = category.quote,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = themeColors.buttonColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun List<PlaylistItem>.findRank(kind: String, nameHint: String): PlaylistItem? =
    firstOrNull { it.kind == kind } ?: firstOrNull { it.name.contains(nameHint) }

// ==================== 排行榜卡片容器 ====================

@Composable
private fun LeaderboardCard(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    songs: List<Song>,
    favorites: Set<String>,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
) {
    // 新歌背景蓝色渐变，热歌背景红色渐变（浅色系），左下角到右上角渐变
    val bgGradient = if (selectedTab == 0) {
        // 新歌榜：浅蓝色渐变 (从左下角到右上角)
        if (Palette.isDark) {
            Brush.linearGradient(
                colors = listOf(Color(0xFF132238), Color(0xFF1A3354)),
                start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
                end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0xFFEBF4FF), Color(0xFFF6FAFF)),
                start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
                end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
            )
        }
    } else {
        // 热歌榜：浅红色渐变 (从左下角到右上角)
        if (Palette.isDark) {
            Brush.linearGradient(
                colors = listOf(Color(0xFF381920), Color(0xFF52222D)),
                start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
                end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0xFFFFEFF2), Color(0xFFFFF7F9)),
                start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
                end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
            )
        }
    }

    val cardBorder = if (Palette.isDark) Color(0xFF33304A) else Color.White.copy(alpha = 0.9f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.White,
                spotColor = Color.White
            )
            .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(bgGradient)
            .padding(18.dp)
    ) {
        // Tab 切换行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LeaderboardTabItem(
                title = "新歌榜",
                selected = selectedTab == 0,
                activeColor = if (Palette.isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                onClick = { onSelectTab(0) }
            )
            LeaderboardTabItem(
                title = "热歌榜",
                selected = selectedTab == 1,
                activeColor = if (Palette.isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                onClick = { onSelectTab(1) }
            )
        }

        Spacer(Modifier.height(16.dp))

        // 歌曲垂直列表：扩大行高与内边距，整齐且有呼吸感
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            songs.take(5).forEachIndexed { index, song ->
                val isFav = (song.platform + ":" + song.id) in favorites
                val rankNumber = index + 1
                val rankColor = when (rankNumber) {
                    1 -> AccentCoral
                    2 -> AccentAmber
                    3 -> AccentCyan
                    else -> TextMuted
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPlaySong(song) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 竞技数字徽章
                    Text(
                        text = "$rankNumber",
                        fontSize = 16.sp,
                        fontWeight = if (rankNumber <= 3) FontWeight.Black else FontWeight.Bold,
                        color = rankColor,
                        modifier = Modifier.width(26.dp)
                    )

                    SongCover(
                        url = song.artwork,
                        modifier = Modifier.size(48.dp),
                        corner = 12.dp
                    )

                    Spacer(Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = song.artist.ifBlank { "热门歌曲" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = TextSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Ghost 级爱心收藏图标
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onToggleFavorite(song) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (isFav) Rose else TextFaint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardTabItem(
    title: String,
    selected: Boolean,
    activeColor: Color = PrimaryBrown,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium
            ),
            color = if (selected) TextPrimary else TextSoft
        )
        Spacer(Modifier.height(5.dp))
        if (selected) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(activeColor)
            )
        } else {
            Spacer(Modifier.height(3.5.dp))
        }
    }
}

// ==================== 公共头部组件 ====================

@Composable
private fun SectionHeader(
    title: String,
    onClickMore: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            ),
            color = TextPrimary
        )
        if (onClickMore != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClickMore)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "更多",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextSoft
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = TextSoft,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
