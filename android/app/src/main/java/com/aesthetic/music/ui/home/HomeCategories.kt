package com.aesthetic.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.appCard
import com.aesthetic.music.ui.components.rememberAppCardTheme
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.AccentRose
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

// ==================== 分类歌单 ====================

@Composable
fun HomeCategorySection(
    homeCategories: List<HomeCategoryDto>,
    hotPlaylists: List<PlaylistItem>,
    poolSongs: List<Song>,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
    onOpenSheet: () -> Unit,
) {
    Column {
        SectionHeader(
            title = "分类歌单",
            onClickMore = onOpenSheet
        )

        Spacer(Modifier.height(14.dp))

        // 用 homeCategories 的长度 + 首元素 id 作为轻量 key，避免 List 引用变化导致无意义重算。
        // 注意：首元素 id 用安全访问，避免空列表崩溃。
        val categoriesKey = homeCategories.size to (homeCategories.firstOrNull()?.id ?: "")
        val categories = remember(categoriesKey, hotPlaylists.size, poolSongs.size) {
            getCategoryPlaylists(homeCategories, hotPlaylists, poolSongs)
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 稳定 key：title + quote + coverUrl 组合，避免索引 key 在数据换血时复用错位
            items(
                items = categories,
                key = { item ->
                    "${item.title}|${item.quote}|${item.coverUrl ?: ""}"
                }
            ) { category ->
                CategoryCard(
                    category = category,
                    onClick = {
                        if (category.playlistItem != null) {
                            onOpenPlaylistDetail(category.playlistItem)
                        } else {
                            onOpenSheet()
                        }
                    }
                )
            }
        }
    }
}

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
            val theme = CATEGORY_THEMES[dto.id]
                ?: CATEGORY_THEME_FALLBACK[index % CATEGORY_THEME_FALLBACK.size]
            CategoryItem(
                title = dto.title.ifBlank { "分类" },
                coverUrl = dto.coverUrl ?: dto.playlist?.coverUrl,
                // 判空兜底：previewSongs 若可空，这里不会 NPE
                previewSongs = (dto.previewSongs ?: emptyList()).take(3),
                quote = dto.quote ?: "",
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
    val theme = rememberAppCardTheme(category.coverUrl, defaultAccent = category.accentColor)
    val lowerBg = if (Palette.isDark) Color(0xFF181920) else Color.White

    Column(
        modifier = Modifier
            .width(270.dp)
            .appCard(background = theme.background)
            .clickable(onClick = onClick)
    ) {
        // 上半部分：从左到右渐变
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background)
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
                            .background(theme.accent)
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
                tint = theme.accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}