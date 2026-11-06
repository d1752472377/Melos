package com.aesthetic.music.ui.playlist

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.components.LiquidBentoCard
import com.aesthetic.music.ui.components.LiquidCapsuleChip
import com.aesthetic.music.ui.components.QuadCover
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

@Composable
fun LibraryBentoSection(
    playlistCount: Int,
    songCount: Int,
    customCount: Int,
    favoriteCount: Int,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    topCovers: List<String>,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onToggleSearch: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = Palette.isDark

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xxs),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "YOUR COLLECTION",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    color = if (isDark) AppColors.slate400 else AppColors.slate500
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "音乐库",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    color = TextPrimary
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDark) AppColors.bentoOverlayWhite20 else AppColors.bentoOverlayBlack8)
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = AppColors.blue,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "$playlistCount 歌单 · $songCount 歌曲",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSoft
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.xxl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            LiquidBentoCard(
                modifier = Modifier
                    .weight(1.15f)
                    .height(156.dp),
                onClick = onPlayAll
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    AppColors.blue.copy(alpha = if (isDark) 0.18f else 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuadCover(
                        covers = topCovers,
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(Modifier.width(AppSpacing.xl))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "全部歌曲",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(AppSpacing.xxs))
                        Text(
                            text = "$songCount 首音轨",
                            fontSize = 12.sp,
                            color = TextSoft
                        )
                        Spacer(Modifier.height(AppSpacing.md))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.blue)
                                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "全播",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .height(156.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                LiquidBentoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = onCreatePlaylist
                ) {
                    Row(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = AppSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppColors.amber.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = AppColors.amber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(AppSpacing.md))
                        Column {
                            Text(
                                text = "新建歌单",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "$customCount 自建",
                                fontSize = 11.sp,
                                color = TextSoft
                            )
                        }
                    }
                }

                LiquidBentoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = onImportPlaylist
                ) {
                    Row(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = AppSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppColors.emerald.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = AppColors.emerald,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(Modifier.width(AppSpacing.md))
                        Column {
                            Text(
                                text = "导入歌单",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "多平台同步",
                                fontSize = 11.sp,
                                color = TextSoft
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(AppSpacing.xl))

        // 「播放全部」必须是**实心主色胶囊**：此前用的是 LiquidGlassButton 的 Primary 样式，
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1.2f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(PrimaryBrown)
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = AppSpacing.xxl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(AppSpacing.xs))
                Text(
                    text = "播放全部",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .weight(0.9f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(if (isDark) AppColors.bentoOverlayWhite12 else AppColors.bentoOverlayBlack8Alt)
                    .clickable(onClick = onShufflePlay)
                    .padding(horizontal = AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "随机",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isDark) AppColors.bentoOverlayWhite12 else AppColors.bentoOverlayBlack8Alt)
                    .clickable(onClick = onToggleSearch),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索歌单",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.xl))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            item {
                LiquidCapsuleChip(
                    selected = selectedFilter == "ALL",
                    text = "全部 ($playlistCount)",
                    onClick = { onSelectFilter("ALL") }
                )
            }
            item {
                LiquidCapsuleChip(
                    selected = selectedFilter == "CUSTOM",
                    text = "自建 ($customCount)",
                    onClick = { onSelectFilter("CUSTOM") }
                )
            }
            item {
                LiquidCapsuleChip(
                    selected = selectedFilter == "FAVORITE",
                    text = "收藏 ($favoriteCount)",
                    onClick = { onSelectFilter("FAVORITE") }
                )
            }
        }
    }
}
