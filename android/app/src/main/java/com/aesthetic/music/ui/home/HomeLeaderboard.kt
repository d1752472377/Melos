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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.appCard
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.TextFaint
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

// ==================== 排行榜 ====================

@Composable
fun HomeLeaderboardSection(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    songs: List<Song>,
    favorites: Set<String>,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenRanksPage: () -> Unit,
) {
    Column {
        SectionHeader(
            title = "排行榜",
            onClickMore = onOpenRanksPage
        )

        Spacer(Modifier.height(14.dp))

        LeaderboardCard(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            songs = songs,
            favorites = favorites,
            onPlaySong = remember(songs, onPlaySong) {
                { song: Song -> onPlaySong(song, songs) }
            },
            onToggleFavorite = onToggleFavorite
        )
    }
}

@Composable
private fun LeaderboardCard(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    songs: List<Song>,
    favorites: Set<String>,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
) {
    val isDark = Palette.isDark

    // 修复：去掉 Offset(Float.POSITIVE_INFINITY, ...)，改用默认方向（左上→右下）
    // 如需保留"左下→右上"方向，用具体数值，绝不能用 Infinity
    val bgGradient = remember(selectedTab, isDark) {
        if (selectedTab == 0) {
            // 新歌榜
            if (isDark) {
                Brush.linearGradient(
                    colors = listOf(Color(0xFF132238), Color(0xFF1A3354))
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(Color(0xFFEBF4FF), Color(0xFFF6FAFF))
                )
            }
        } else {
            // 热歌榜
            if (isDark) {
                Brush.linearGradient(
                    colors = listOf(Color(0xFF381920), Color(0xFF52222D))
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFFEFF2), Color(0xFFFFF7F9))
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appCard(background = bgGradient)
            .padding(18.dp)
    ) {
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