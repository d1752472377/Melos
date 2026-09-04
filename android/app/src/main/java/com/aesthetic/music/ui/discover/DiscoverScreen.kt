package com.aesthetic.music.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.BottomBarReserved
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 发现页：按反 AI 味原则重塑，色彩锚点分明、卡片与列表排版精致。
 */
@Composable
fun DiscoverScreen(
    hotPlaylists: List<PlaylistItem>,
    dailyPlaylists: List<PlaylistItem>,
    newSongs: List<Song>,
    ranks: List<PlaylistItem>,
    isLoading: Boolean,
    isHotLoading: Boolean,
    hotError: String? = null,
    onRetryHot: () -> Unit = {},
    contentError: String? = null,
    onRetryContent: (() -> Unit)? = null,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenRank: (PlaylistItem) -> Unit,
    onPlaySong: (Song) -> Unit,
) {
    // 预先分好行：key 用行号（必然唯一），避免 null-id 兜底成重复 "" 导致 LazyList 崩溃
    val hotRows = androidx.compose.runtime.remember(hotPlaylists) { hotPlaylists.chunked(2) }
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && dailyPlaylists.isEmpty() && newSongs.isEmpty() && ranks.isEmpty()) {
            // 页面整体加载态：统一走 LoadingBox（居中 spinner + 文案），contentError 错误分支保持不变
            LoadingBox(message = "探索内容中…")
            return@Box
        }

        // 错误态：全部为空且不在加载中时给出失败原因与重试入口（原先失败即永久白屏）
        if (contentError != null && !isLoading && !isHotLoading &&
            dailyPlaylists.isEmpty() && newSongs.isEmpty() && ranks.isEmpty()
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(contentError, color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                    if (onRetryContent != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "重试",
                            color = PrimaryBrown,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onRetryContent)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(
                top = 24.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = BottomBarReserved // 避开底栏 Dock
            )
        ) {
            // 顶栏标题
            item(key = "discover_header") {
                Column {
                    Text(
                        text = "发现",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "每日精选 · 全球新歌 · 权威榜单",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                    )
                }
            }

            // 每日推荐
            if (dailyPlaylists.isNotEmpty()) {
                item(key = "section_daily") { SectionTitle("每日推荐", "为你精选的调频", AccentAmber) }
                item(key = "row_daily") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        // 行号参与 key：同 id/空 id 兜底时避免 LazyList key 碰撞崩溃
                        itemsIndexed(dailyPlaylists, key = { i, it -> "d$i:${it.id}" }) { _, item ->
                            PlaylistCard(item = item, width = 124.dp, onClick = { onOpenPlaylist(item) })
                        }
                    }
                }
            }

            // 新歌速递
            if (newSongs.isNotEmpty()) {
                item(key = "section_newsongs") {
                    Column {
                        SectionTitle("新歌速递", "第一时间听见心动", AccentMint)
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            newSongs.take(8).forEach { song ->
                                NewSongRow(song = song, onClick = { onPlaySong(song) })
                            }
                        }
                    }
                }
            }

            // 排行榜
            if (ranks.isNotEmpty()) {
                item(key = "section_ranks") { SectionTitle("排行榜", "热门权威榜单", AccentCyan) }
                item(key = "row_ranks") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        // 行号参与 key：同 id/空 id 兜底时避免 LazyList key 碰撞崩溃
                        itemsIndexed(ranks.take(12), key = { i, it -> "r$i:${it.id}" }) { _, item ->
                            PlaylistCard(item = item, width = 124.dp, onClick = { onOpenRank(item) })
                        }
                    }
                }
            }

            // 热门歌单
            if (hotPlaylists.isNotEmpty()) {
                item(key = "section_hot") { SectionTitle("热门歌单", "大家都在循环的宝藏", AccentCoral) }
                items(hotRows.size, key = { "hot_row_$it" }) { rowIdx ->
                    val rowItems = hotRows[rowIdx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        rowItems.forEach { item ->
                            GridPlaylistCard(
                                item = item,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenPlaylist(item) },
                            )
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (isHotLoading && hotPlaylists.isEmpty()) {
                item(key = "hot_loading") { Text("热门歌单加载中…", color = TextSoft, style = MaterialTheme.typography.bodySmall) }
            }

            // 热门歌单加载失败：此前失败静默、区块直接消失，无任何重试入口
            if (!isHotLoading && hotPlaylists.isEmpty() && !hotError.isNullOrEmpty()) {
                item(key = "hot_error") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "热门歌单加载失败：$hotError",
                            color = TextSoft,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "重试",
                            color = PrimaryBrown,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onRetryHot)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, accentColor: Color) {
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                ),
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp
            ),
            color = TextSoft
        )
    }
}

@Composable
private fun NewSongRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(song.artwork, Modifier.size(48.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = song.subtitleText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp
                ),
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 强化右侧播放按钮视觉重量：圆形高对比底色（阴影已去除：列表 item 离屏渲染是掉帧源）
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(PrimaryBrown.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = PrimaryBrown,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PlaylistCard(item: PlaylistItem, width: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(width)
                .border(1.dp, Stone200.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            SongCover(item.coverUrl, Modifier.fillMaxSize(), corner = 10.dp)
            if (item.playCount != null) {
                // Level 3 微型播放量徽章 (4dp corner)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(9.dp),
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = formatCount(item.playCount),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = "${item.trackCount ?: 0} 首",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp
            ),
            color = TextSoft,
        )
    }
}

@Composable
private fun GridPlaylistCard(item: PlaylistItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(1.dp, Stone200.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            SongCover(item.coverUrl, Modifier.fillMaxSize(), corner = 10.dp)
            if (item.playCount != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(9.dp),
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = formatCount(item.playCount),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = "${item.trackCount ?: 0} 首",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp
            ),
            color = TextSoft
        )
    }
}

private fun formatCount(count: Long): String = formatPlayCountText(count)