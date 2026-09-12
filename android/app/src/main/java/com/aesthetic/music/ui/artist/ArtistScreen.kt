package com.aesthetic.music.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.SongRow
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 歌手页：精致分层设计 + 现代艺术质感。
 *
 * 歌曲列表复用 SongRow（不显示平台标签、显示播放高亮），外层统一白卡 + 浅色分隔线，
 * 右侧「...」打开单曲操作抽屉；白卡铺满屏幕左右、底部探到底；
 * 列表最后一项为「全部演唱 >」，由 [onOpenAllSongs] 跳转到全部演唱页。
 */
@Composable
fun ArtistScreen(
    name: String,
    alias: String?,
    avatar: String?,
    songs: List<Song>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    favorites: Set<String>,
    currentSong: Song?,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onMoreSong: (Song) -> Unit,
    onOpenAllSongs: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
        ) {
            // 顶栏：返回 + 标题 + 播放全部（自身保留左右 20dp）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = name.ifBlank { "歌手详情" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (songs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = PrimaryBrown.copy(alpha = 0.3f))
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryBrown)
                            .clickable(onClick = onPlayAll)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "播放全部",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            // 歌手头部信息（自身保留左右 20dp）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp, bottom = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(1.5.dp, AccentCyan.copy(alpha = 0.4f), CircleShape)
                        .clip(CircleShape)
                ) {
                    SongCover(avatar, Modifier.fillMaxSize(), corner = 38.dp)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = name.ifBlank { "未知歌手" },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!alias.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Stone100)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = alias,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isLoading) "探索音乐库中…" else "热门单曲 · 共 ${songs.size} 首",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 热门歌曲列表
            if (isLoading && songs.isEmpty()) {
                LoadingBox(message = "探索音乐库中…")
            } else if (!isLoading && songs.isEmpty()) {
                ErrorBox(
                    message = errorMessage ?: "暂无相关歌曲",
                    onRetry = if (errorMessage != null) onRetry else null,
                )
            } else {
                // 与搜索页一致：统一白卡，左右贴屏、底部探到屏幕底
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(Surface)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.Top,
                        contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current),
                    ) {
                        itemsIndexed(
                            songs,
                            key = { i, s -> "a$i:${s.platform}:${s.id}" },
                        ) { index, song ->
                            val playingNow = currentSong != null &&
                                    song.id == currentSong.id && song.platform == currentSong.platform
                            SongRow(
                                song = song,
                                isPlaying = playingNow,
                                showPlatformTag = false,
                                onPlay = { onPlaySong(song) },
                                actions = {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable { onMoreSong(song) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreHoriz,
                                            contentDescription = "更多操作",
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                            )
                            if (index != songs.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 70.dp, end = 12.dp),
                                    thickness = 0.5.dp,
                                    color = Stone200.copy(alpha = 0.55f),
                                )
                            }
                        }
                        // 最后一项：全部演唱
                        item(key = "all_songs_footer") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenAllSongs)
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "全部演唱",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = TextSoft,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = TextSoft,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}