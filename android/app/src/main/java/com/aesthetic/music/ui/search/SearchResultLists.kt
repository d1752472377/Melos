package com.aesthetic.music.ui.search

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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.SongRow
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

// ==================== 统一卡片容器 ====================

/**
 * 结果列表统一外层圆角白卡：
 * 包含“搜索结果 XX 首”标题栏，以及四周圆角（20dp）白底卡片容器，与收藏/歌单/历史页面设计统一。
 */
@Composable
private fun ResultCard(
    bottomContentPadding: Dp,
    title: String? = null,
    countText: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        if (!title.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                )
                if (!countText.isNullOrBlank()) {
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextSoft,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface)
            ) {
                content()
            }
        }
    }
}

/** 结果行之间的浅色分隔线，左侧缩进由参数决定，右侧统一缩进 12dp。 */
@Composable
private fun ResultDivider(startIndent: Int) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent.dp, end = 12.dp),
        thickness = 0.5.dp,
        color = Stone200.copy(alpha = 0.55f),
    )
}

// ==================== 结果列表 ====================

@Composable
fun SongResultList(
    songs: List<Song>,
    currentSong: Song?,
    emptyHint: String,
    onPlay: (Song) -> Unit,
    onMore: (Song) -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
    if (songs.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    ResultCard(
        bottomContentPadding = bottomContentPadding,
        title = "搜索结果",
        countText = "${songs.size} 首"
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Top,
            contentPadding = PaddingValues(bottom = bottomContentPadding),
        ) {
            itemsIndexed(
                songs,
                key = { i, s -> "$i:${s.platform}:${s.id}" },
                contentType = { _, _ -> "song" },
            ) { index, song ->
                val playingNow = currentSong != null &&
                        song.id == currentSong.id && song.platform == currentSong.platform
                SongRow(
                    song = song,
                    isPlaying = playingNow,
                    showPlatformTag = true,
                    onPlay = { onPlay(song) },
                    actions = {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onMore(song) },
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
                    ResultDivider(startIndent = 70)
                }
            }
        }
    }
}

@Composable
fun ArtistResultList(
    artists: List<ArtistItem>,
    emptyHint: String,
    onOpen: (ArtistItem) -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
    if (artists.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    ResultCard(
        bottomContentPadding = bottomContentPadding,
        title = "搜索结果",
        countText = "${artists.size} 位歌手"
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Top,
            contentPadding = PaddingValues(bottom = bottomContentPadding),
        ) {
            itemsIndexed(artists, key = { i, a -> "$i:${a.platform}:${a.id}" }) { index, artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(artist) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!artist.avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = artist.avatar,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Stone100),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Stone100),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = TextSoft,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = buildString {
                            if (!artist.alias.isNullOrBlank()) {
                                append(artist.alias)
                            }
                            val counts = listOfNotNull(
                                artist.musicSize?.takeIf { it > 0 }?.let { "${it}首歌" },
                                artist.albumSize?.takeIf { it > 0 }?.let { "${it}张专辑" },
                            ).joinToString(" · ")
                            if (counts.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(counts)
                            }
                        }
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (index != artists.lastIndex) {
                    ResultDivider(startIndent = 68)
                }
            }
        }
    }
}

@Composable
fun PlaylistResultList(
    playlists: List<PlaylistItem>,
    emptyHint: String,
    onOpen: (PlaylistItem) -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
    if (playlists.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    ResultCard(
        bottomContentPadding = bottomContentPadding,
        title = "搜索结果",
        countText = "${playlists.size} 个歌单"
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Top,
            contentPadding = PaddingValues(bottom = bottomContentPadding),
        ) {
            itemsIndexed(playlists, key = { i, p -> "$i:${p.platform}:${p.id}" }) { index, p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(p) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongCover(p.coverUrl, Modifier.size(52.dp), corner = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val count = buildString {
                            p.trackCount?.let { append("${it}首") }
                            val played = formatPlayCountText(p.playCount)
                            if (played.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append("${played}次播放")
                            }
                        }
                        if (count.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                count,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (index != playlists.lastIndex) {
                    ResultDivider(startIndent = 74)
                }
            }
        }
    }
}

@Composable
fun AlbumResultList(
    albums: List<AlbumItem>,
    emptyHint: String,
    onOpen: (AlbumItem) -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
    if (albums.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    ResultCard(
        bottomContentPadding = bottomContentPadding,
        title = "搜索结果",
        countText = "${albums.size} 张专辑"
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Top,
            contentPadding = PaddingValues(bottom = bottomContentPadding),
        ) {
            itemsIndexed(albums, key = { i, a -> "$i:${a.platform}:${a.id}" }) { index, album ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(album) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongCover(album.coverUrl, Modifier.size(52.dp), corner = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            album.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            listOfNotNull(album.artist.ifBlank { null }, album.size?.let { "${it}首" })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (index != albums.lastIndex) {
                    ResultDivider(startIndent = 74)
                }
            }
        }
    }
}

// ==================== 杂项 ====================

@Composable
private fun EmptyHint(text: String) {
    EmptyBox(message = text)
}