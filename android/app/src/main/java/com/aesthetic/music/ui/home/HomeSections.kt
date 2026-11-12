package com.aesthetic.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.theme.AppColors
import com.aesthetic.music.ui.theme.AppSpacing

/**
 * 首页区块的标题行：左边标题，右边小箭头（点击进入对应页面）。
 * 外层卡片负责水平内边距，这里不再重复加。
 */
@Composable
private fun SectionHeader(
    title: String,
    onOpenMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = textHigh(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f),
        )
        if (onOpenMore != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "查看全部",
                    tint = textLow(),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** 首页卡片容器：和「新歌首发」用同一套玻璃卡样式 */
@Composable
private fun HomeSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl)
            .glassCard(shape = RoundedCornerShape(20.dp))
            .padding(AppSpacing.xl),
        content = content,
    )
}

/**
 * 猜你喜欢：一行歌曲，点击单曲直接播放，右上角箭头进推荐页。
 */
@Composable
fun GuessLikeSection(
    songs: List<Song>,
    onOpenMore: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return
    HomeSectionCard(modifier = modifier) {
        SectionHeader(title = "猜你喜欢", onOpenMore = onOpenMore)
        Spacer(Modifier.size(AppSpacing.lg))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            items(songs.take(10), key = { "${it.platform}:${it.id}" }) { song ->
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .clickable { onPlaySong(song, songs) },
                ) {
                    Box {
                        CoverBox(
                            url = song.artwork,
                            size = 104.dp,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(AppSpacing.xs)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.92f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = AppColors.ink,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                    Spacer(Modifier.size(AppSpacing.sm))
                    Text(
                        text = song.title,
                        color = textHigh(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = song.artist,
                        color = textMedium(),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 随机歌单：一行歌单卡，右上角箭头进歌单广场。
 */
@Composable
fun RandomPlaylistSection(
    playlists: List<PlaylistItem>,
    onOpenMore: () -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playlists.isEmpty()) return
    HomeSectionCard(modifier = modifier) {
        SectionHeader(title = "随机歌单", onOpenMore = onOpenMore)
        Spacer(Modifier.size(AppSpacing.lg))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            items(playlists.take(10), key = { "${it.platform}:${it.id}" }) { playlist ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clickable { onOpenPlaylist(playlist) },
                ) {
                    CoverBox(
                        url = playlist.coverUrl,
                        size = 120.dp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.size(AppSpacing.sm))
                    Text(
                        text = playlist.name,
                        color = textHigh(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        lineHeight = 17.sp,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverBox(url: String?, size: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .background(textHigh().copy(alpha = 0.06f)),
    )
}
