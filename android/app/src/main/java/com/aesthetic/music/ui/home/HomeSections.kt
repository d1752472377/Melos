package com.aesthetic.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.KeyboardArrowRight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.theme.AppColors
import com.aesthetic.music.ui.theme.AppSpacing

/** 首页区块的标题行：左边标题，右边小箭头（点击进入对应页面） */
@Composable
private fun SectionHeader(
    title: String,
    onOpenMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = textHigh(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.weight(1f),
        )
        if (onOpenMore != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowRight,
                    contentDescription = "查看全部",
                    tint = textLow(),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
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
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "猜你喜欢", onOpenMore = onOpenMore)
        Spacer(Modifier.size(AppSpacing.lg))
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppSpacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
            items(songs.take(10), key = { "${it.platform}:${it.id}" }) { song ->
                Column(
                    modifier = Modifier
                        .width(112.dp)
                        .clickable { onPlaySong(song, songs) },
                ) {
                    Box {
                        CoverBox(
                            url = song.artwork,
                            size = 112.dp,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(AppSpacing.sm)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.92f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = AppColors.ink,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.size(AppSpacing.sm))
                    Text(
                        text = song.title,
                        color = textHigh(),
                        fontSize = 14.sp,
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
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "随机歌单", onOpenMore = onOpenMore)
        Spacer(Modifier.size(AppSpacing.lg))
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppSpacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
            items(playlists.take(10), key = { "${it.platform}:${it.id}" }) { playlist ->
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clickable { onOpenPlaylist(playlist) },
                ) {
                    CoverBox(
                        url = playlist.coverUrl,
                        size = 132.dp,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)),
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
private fun CoverBox(url: String?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .background(textHigh().copy(alpha = 0.06f)),
    )
}
