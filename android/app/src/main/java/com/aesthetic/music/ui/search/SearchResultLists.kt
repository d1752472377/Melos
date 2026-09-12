package com.aesthetic.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.PlatformTag
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

// ==================== 结果列表 ====================

@Composable
fun SongResultList(
    songs: List<Song>,
    currentSong: Song?,
    emptyHint: String,
    onPlay: (Song) -> Unit,
    onMore: (Song) -> Unit,
) {
    if (songs.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(
            songs,
            key = { i, s -> "$i:${s.platform}:${s.id}" },
            contentType = { _, _ -> "song" },
        ) { _, song ->
            val playingNow = currentSong != null &&
                song.id == currentSong.id && song.platform == currentSong.platform
            ResultSongRow(
                song = song,
                isPlaying = playingNow,
                onPlay = { onPlay(song) },
                onMore = { onMore(song) },
            )
        }
    }
}

@Composable
fun ArtistResultList(artists: List<ArtistItem>, emptyHint: String, onOpen: (ArtistItem) -> Unit) {
    if (artists.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(artists, key = { i, a -> "$i:${a.platform}:${a.id}" }) { _, artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
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
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TextSoft, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
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
                PlatformTag(artist.platform)
            }
        }
    }
}

@Composable
fun PlaylistResultList(playlists: List<PlaylistItem>, emptyHint: String, onOpen: (PlaylistItem) -> Unit) {
    if (playlists.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(playlists, key = { i, p -> "$i:${p.platform}:${p.id}" }) { _, p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
                    .clickable { onOpen(p) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongCover(p.coverUrl, Modifier.size(52.dp), corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    val count = buildString {
                        p.trackCount?.let { append("${it}首") }
                        val played = formatPlayCountText(p.playCount)
                        if (played.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            // 注意：中文字符属于 Kotlin 标识符，模板必须用 ${} 包住变量名
                            append("${played}次播放")
                        }
                    }
                    if (count.isNotBlank()) {
                        Text(count, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextSoft)
                    }
                }
                PlatformTag(p.platform)
            }
        }
    }
}

@Composable
fun AlbumResultList(albums: List<AlbumItem>, emptyHint: String, onOpen: (AlbumItem) -> Unit) {
    if (albums.isEmpty()) {
        EmptyHint(emptyHint)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(albums, key = { i, a -> "$i:${a.platform}:${a.id}" }) { _, album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
                    .clickable { onOpen(album) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongCover(album.coverUrl, Modifier.size(52.dp), corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        album.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
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
                PlatformTag(album.platform)
            }
        }
    }
}

/**
 * 单曲/歌词搜索结果行。
 * 封面 48dp + 15sp 标题；平台改为标题下方的彩色小标签；最右侧「...」打开单曲操作抽屉。
 */
@Composable
private fun ResultSongRow(
    song: Song,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    val subtitle = listOfNotNull(
        song.artist.ifBlank { null },
        song.album.ifBlank { null },
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isPlaying) PrimaryBrown.copy(alpha = 0.5f) else Stone200.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(song.artwork, Modifier.size(48.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isPlaying) PrimaryBrown else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!song.platform.isNullOrBlank()) {
                    PlatformTag(song.platform)
                    if (subtitle.isNotBlank()) Spacer(Modifier.width(6.dp))
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreHoriz,
                contentDescription = "更多操作",
                tint = TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ==================== 杂项 ====================

@Composable
private fun EmptyHint(text: String) {
    // 统一空态组件（各结果类型文案不变；emptyHint/searchError 仍只在列表为空时传入）
    EmptyBox(message = text)
}
