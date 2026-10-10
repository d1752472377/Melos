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
import com.aesthetic.music.ui.components.LiquidGlassButton
import com.aesthetic.music.ui.components.LiquidGlassButtonStyle
import com.aesthetic.music.ui.components.LiquidGhostButton
import com.aesthetic.music.ui.components.QuadCover
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 音乐库 / 媒体库 Apple Bento 拟态区域（复刻 GZ Music 2.1.8 LibraryBentoSectionKt 规范）
 */
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
    platformList: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    val isDark = Palette.isDark

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // ===== 1. 顶部 COLLECTION 标签与大标题 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "YOUR COLLECTION",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
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

            // 快捷统计胶囊
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDark) Color(0x33FFFFFF) else Color(0x15000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
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

        Spacer(Modifier.height(16.dp))

        // ===== 2. Bento 2x2 格子架构 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 左侧大 Bento 卡：四宫格封面 + 媒体库概览
            LiquidBentoCard(
                modifier = Modifier
                    .weight(1.15f)
                    .height(156.dp),
                onClick = onPlayAll
            ) {
                // 渐变底纹
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF3B82F6).copy(alpha = if (isDark) 0.18f else 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuadCover(
                        covers = topCovers,
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "全部歌曲",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$songCount 首音轨",
                            fontSize = 12.sp,
                            color = TextSoft
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF3B82F6))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
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

            // 右侧两列小 Bento 方块 (新建 & 导入)
            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .height(156.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 小方块 1: 新建歌单
                LiquidBentoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = onCreatePlaylist
                ) {
                    Row(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
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

                // 小方块 2: 导入歌单
                LiquidBentoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = onImportPlaylist
                ) {
                    Row(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
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

        Spacer(Modifier.height(14.dp))

        // ===== 3. Bento 流体操作条 (播放全部 / 随机播放 / 搜索) =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [GZ 真值] LiquidGlassButton 已换成逐值还原版（LiquidGlassButtonKt.java:101）：
            // GZ 的 API 不含 text/icon/accentColor —— 尺寸走 modifier，文案与图标走 content。
            LiquidGlassButton(
                onClick = onPlayAll,
                modifier = Modifier.weight(1.2f),
                style = LiquidGlassButtonStyle.Primary,
                content = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "播放全部",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )

            LiquidGhostButton(
                text = "随机",
                icon = Icons.Default.Shuffle,
                onClick = onShufflePlay,
                modifier = Modifier.weight(0.9f)
            )

            LiquidGhostButton(
                text = "",
                icon = Icons.Default.Search,
                onClick = onToggleSearch,
                modifier = Modifier.width(48.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        // ===== 4. 平台与维度切换胶囊栏 =====
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            items(platformList) { (platName, count) ->
                LiquidCapsuleChip(
                    selected = selectedFilter == platName,
                    text = "$platName ($count)",
                    onClick = { onSelectFilter(platName) }
                )
            }
        }
    }
}
