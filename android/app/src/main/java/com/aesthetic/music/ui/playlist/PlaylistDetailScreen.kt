package com.aesthetic.music.ui.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.AppDialog
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LiquidGlassButton
import com.aesthetic.music.ui.components.LiquidGlassButtonStyle
import com.aesthetic.music.ui.components.LiquidGhostButton
import com.aesthetic.music.ui.components.PlatformTag
import com.aesthetic.music.ui.components.PlayingBars
import com.aesthetic.music.ui.components.PlaylistCoverImage
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.components.rememberDominantColors
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

enum class PlaylistSortOrder(val label: String) {
    DEFAULT("默认排序"),
    TITLE("按歌曲名 (A-Z)"),
    ARTIST("按歌手名 (A-Z)"),
}

private fun forceDarkColor(color: Color, targetLightness: Float = 0.12f): Color {
    val r = color.red.coerceIn(0f, 1f)
    val g = color.green.coerceIn(0f, 1f)
    val b = color.blue.coerceIn(0f, 1f)

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)

    if (max - min < 0.01f) {
        val l = targetLightness
        return Color(l, l, l, 1f)
    }

    val d = max - min

    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }

    val l_original = (max + min) / 2f
    val s_original = if (l_original > 0.5f) {
        d / (2f - max - min)
    } else if (l_original > 0f) {
        d / (max + min)
    } else {
        0f
    }

    val s = s_original.coerceIn(0f, 0.6f)
    val l = targetLightness

    fun hue2rgb(p: Float, q: Float, t: Float): Float {
        var tt = t
        if (tt < 0) tt += 1
        if (tt > 1) tt -= 1
        if (tt < 1f/6f) return p + (q - p) * 6f * tt
        if (tt < 1f/2f) return q
        if (tt < 2f/3f) return p + (q - p) * (2f/3f - tt) * 6f
        return p
    }

    val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
    val p = 2 * l - q

    return Color(
        red = hue2rgb(p, q, h + 1f/3f).coerceIn(0f, 1f),
        green = hue2rgb(p, q, h).coerceIn(0f, 1f),
        blue = hue2rgb(p, q, h - 1f/3f).coerceIn(0f, 1f),
        alpha = 1f
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistItem,
    songs: List<Song>,
    isLoading: Boolean,
    error: String?,
    isFavorite: Boolean,
    currentSong: Song?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onToggleFavoritePlaylist: () -> Unit,
    onMoreSong: (Song) -> Unit,
    onBackgroundColorChange: (Color?, Boolean) -> Unit = { _, _ -> },
) {

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val dominantColors = rememberDominantColors(
        url = playlist.coverUrl ?: songs.firstOrNull()?.artwork,
        defaultAccent = PrimaryBrown
    )

    LaunchedEffect(dominantColors) {
        android.util.Log.d("PlaylistDetail", "bgStart original: ${dominantColors.bgStart}")
        android.util.Log.d("PlaylistDetail", "bgEnd original: ${dominantColors.bgEnd}")
        android.util.Log.d("PlaylistDetail", "buttonColor original: ${dominantColors.buttonColor}")
    }

    val baseDarkBg = remember(dominantColors.bgEnd) {
        forceDarkColor(dominantColors.bgEnd, targetLightness = 0.12f)
    }

    LaunchedEffect(baseDarkBg) {
        onBackgroundColorChange(baseDarkBg, true)
    }

    DisposableEffect(Unit) {
        onDispose {
            onBackgroundColorChange(null, false)
        }
    }

    val accentColor = remember(dominantColors.buttonColor) {
        forceDarkColor(dominantColors.buttonColor, targetLightness = 0.3f)
    }

    LaunchedEffect(baseDarkBg) {
        android.util.Log.d("PlaylistDetail", "baseDarkBg after darken: $baseDarkBg")
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(PlaylistSortOrder.DEFAULT) }
    var showSortDialog by remember { mutableStateOf(false) }

    val displaySongs = remember(songs, searchQuery, sortOrder) {
        var list = songs
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
        }
        when (sortOrder) {
            PlaylistSortOrder.DEFAULT -> list
            PlaylistSortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
            PlaylistSortOrder.ARTIST -> list.sortedBy { it.artist.lowercase() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseDarkBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        ) {
            val coverUrl = playlist.coverUrl ?: songs.firstOrNull()?.artwork
            if (!coverUrl.isNullOrBlank()) {
                PlaylistCoverImage(
                    model = coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    corner = 0.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(baseDarkBg)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.45f),
                            0.35f to Color.Transparent,
                            0.65f to baseDarkBg.copy(alpha = 0.60f),
                            1.0f to baseDarkBg
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = BottomContentSpacer
            )
        ) {
            item(key = "playlist_header_info") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarPadding + 52.dp, start = AppSpacing.page, end = AppSpacing.page, bottom = AppSpacing.xxl)
                ) {
                    Spacer(Modifier.height(56.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .shadow(
                                    elevation = 10.dp,
                                    shape = RoundedCornerShape(18.dp),
                                    ambientColor = Color.Black.copy(0.45f),
                                    spotColor = Color.Black.copy(0.6f)
                                )
                                .clip(RoundedCornerShape(18.dp))
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(0.45f),
                                            Color.White.copy(0.12f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                )
                        ) {
                            val heroCover = playlist.coverUrl ?: songs.firstOrNull()?.artwork
                            if (!heroCover.isNullOrBlank()) {
                                PlaylistCoverImage(
                                    model = heroCover,
                                    modifier = Modifier.fillMaxSize(),
                                    corner = 18.dp
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = Color.White.copy(0.7f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(AppSpacing.xxl))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = playlist.name.ifBlank { "精选歌单" },
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.2.sp,
                                    lineHeight = 26.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(AppSpacing.sm))

                            if (!playlist.platform.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .padding(horizontal = AppSpacing.sm, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = playlist.platform.orEmpty().uppercase(),
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(0.9f)
                                        )
                                    )
                                }
                                Spacer(Modifier.height(AppSpacing.xs))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                Text(
                                    text = "${playlist.trackCount ?: songs.size} 首歌曲",
                                    style = TextStyle(
                                        fontSize = 12.5.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                if (playlist.playCount != null && playlist.playCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.5f))
                                    )
                                    Text(
                                        text = "${formatPlayCountText(playlist.playCount)} 次收听",
                                        style = TextStyle(
                                            fontSize = 12.5.sp,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(AppSpacing.page))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        LiquidGlassButton(
                            onClick = {
                                if (displaySongs.isNotEmpty()) {
                                    onPlayAll(displaySongs)
                                }
                            },
                            modifier = Modifier.weight(1.3f).height(44.dp),
                            style = LiquidGlassButtonStyle.Primary,
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
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
                            }
                        )

                        LiquidGhostButton(
                            text = "随机",
                            icon = Icons.Filled.Shuffle,
                            onClick = {
                                if (displaySongs.isNotEmpty()) {
                                    onPlayAll(displaySongs.shuffled())
                                }
                            },
                            height = 44.dp,
                            modifier = Modifier.weight(0.9f)
                        )

                        LiquidGhostButton(
                            text = "",
                            icon = Icons.Filled.Search,
                            onClick = {
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded) searchQuery = ""
                            },
                            height = 44.dp,
                            modifier = Modifier.size(44.dp)
                        )

                        LiquidGhostButton(
                            text = "",
                            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            tint = if (isFavorite) Rose else null,
                            onClick = onToggleFavoritePlaylist,
                            height = 44.dp,
                            modifier = Modifier.size(44.dp)
                        )

                        LiquidGhostButton(
                            text = "",
                            icon = Icons.Filled.SwapVert,
                            onClick = { showSortDialog = true },
                            height = 44.dp,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isSearchExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = AppSpacing.lg)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(AppSpacing.sm))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "搜索歌单内歌曲、歌手…",
                                                style = TextStyle(fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f))
                                            )
                                        }
                                        inner()
                                    }
                                )
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "清空",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { searchQuery = "" }
                                    )
                                }
                            }

                            if (searchQuery.isNotBlank()) {
                                Text(
                                    text = "共找到 ${displaySongs.size} 首匹配歌曲",
                                    style = TextStyle(fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f)),
                                    modifier = Modifier.padding(top = AppSpacing.xs, start = AppSpacing.xxs)
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading && songs.isEmpty()) {
                item(key = "loading_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "正在加载歌曲列表…",
                            style = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                        )
                    }
                }
            } else if (!error.isNullOrBlank() && songs.isEmpty()) {
                item(key = "error_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorBox(message = error, onRetry = onRetry)
                    }
                }
            } else if (displaySongs.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "未搜索到相关歌曲" else "歌单内暂无歌曲",
                            style = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = displaySongs,
                    key = { i, s -> "$i:${s.platform}:${s.id}" }
                ) { _, song ->
                    val isCurrent = song.id == currentSong?.id && song.platform == currentSong?.platform
                    PlaylistSongRow(
                        song = song,
                        isCurrent = isCurrent,
                        onClick = { onPlaySong(song, displaySongs) },
                        onMoreClick = { onMoreSong(song) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarPadding + AppSpacing.xs, start = AppSpacing.xxl, end = AppSpacing.xxl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.30f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }

    if (showSortDialog) {
        AppDialog(
            onDismissRequest = { showSortDialog = false },
            title = "歌单排序",
            confirmText = null,
            cancelText = "取消",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                PlaylistSortOrder.entries.forEach { option ->
                    val selected = sortOrder == option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) PrimaryBrown.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable {
                                sortOrder = option
                                showSortDialog = false
                            }
                            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) PrimaryBrown else TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = PrimaryBrown,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun PlaylistSongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val activeTitleColor = AppColors.sky
    val titleColor = if (isCurrent) activeTitleColor else Color.White
    val rowBg = if (isCurrent) AppColors.activeRowBgDark else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.page, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongCover(
            url = song.artwork,
            modifier = Modifier.size(48.dp),
            corner = 10.dp
        )

        Spacer(Modifier.width(AppSpacing.lg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!song.platform.isNullOrBlank()) {
                    PlatformTag(song.platform)
                    Spacer(Modifier.width(5.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(AppColors.crimson.copy(alpha = 0.25f))
                        .border(0.5.dp, AppColors.crimson.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = "SQ",
                        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppColors.coral)
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = song.subtitleText,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isCurrent) {
            PlayingBars(
                isPlaying = true,
                color = activeTitleColor,
                modifier = Modifier.height(13.dp).padding(horizontal = AppSpacing.xs),
            )
        }

        Spacer(Modifier.width(AppSpacing.xxs))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onMoreClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "更多操作",
                tint = Color.White.copy(alpha = 0.70f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
