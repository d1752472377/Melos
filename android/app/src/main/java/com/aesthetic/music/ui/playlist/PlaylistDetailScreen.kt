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
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.components.rememberDominantColors
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/** 歌单排序规则 */
enum class PlaylistSortOrder(val label: String) {
    DEFAULT("默认排序"),
    TITLE("按歌曲名 (A-Z)"),
    ARTIST("按歌手名 (A-Z)"),
}

/**
 * 将颜色强制压暗到指定亮度
 * 提取色相和饱和度，但将亮度强制降低
 */
private fun forceDarkColor(color: Color, targetLightness: Float = 0.12f): Color {
    val r = color.red.coerceIn(0f, 1f)
    val g = color.green.coerceIn(0f, 1f)
    val b = color.blue.coerceIn(0f, 1f)

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)

    // 如果是灰色（r=g=b），直接返回深灰色
    if (max - min < 0.01f) {
        val l = targetLightness
        return Color(l, l, l, 1f)
    }

    val d = max - min

    // 计算色相 (0-1)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }

    // 计算原始饱和度
    val l_original = (max + min) / 2f
    val s_original = if (l_original > 0.5f) {
        d / (2f - max - min)
    } else if (l_original > 0f) {
        d / (max + min)
    } else {
        0f
    }

    // 使用原始饱和度但降低亮度
    val s = s_original.coerceIn(0f, 0.6f) // 限制饱和度不要过高
    val l = targetLightness

    // HSL 转 RGB
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

/**
 * 现代沉浸式歌单详情页：
 * 1. 顶部大图展示歌单封面，底部背景色提取自歌单图片色系并平滑过渡；
 * 2. 歌单级操作：播放全部、歌单收藏、歌单排序、歌单内实时搜索；
 * 3. 歌曲级操作（点击「...」）：收藏歌曲、添加到自建歌单（底部抽屉选择）、加入播放队列、下一首播放、下载；
 * 4. 自建歌单抽屉：底部弹层列出自建歌单，支持选择入库与一键新建。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistItem,
    songs: List<Song>,
    isLoading: Boolean,
    error: String?,
    isFavorite: Boolean,
    myPlaylists: List<MyPlaylist>,
    currentSong: Song?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onToggleFavoritePlaylist: () -> Unit,
    onToggleFavoriteSong: (Song) -> Unit,
    isSongFavorite: (Song) -> Boolean,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onDownloadSong: (Song) -> Unit,
    onAddToPlaylist: (String, Song) -> Unit,
    onCreatePlaylist: (String, Song?) -> Unit,
    onBackgroundColorChange: (Color?, Boolean) -> Unit = { _, _ -> },
) {


    val context = LocalContext.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // ===== 色系提取 =====
    val dominantColors = rememberDominantColors(
        url = playlist.coverUrl ?: songs.firstOrNull()?.artwork,
        defaultAccent = PrimaryBrown
    )

    // 添加调试日志
    LaunchedEffect(dominantColors) {
        android.util.Log.d("PlaylistDetail", "bgStart original: ${dominantColors.bgStart}")
        android.util.Log.d("PlaylistDetail", "bgEnd original: ${dominantColors.bgEnd}")
        android.util.Log.d("PlaylistDetail", "buttonColor original: ${dominantColors.buttonColor}")
    }

    // 在计算 baseDarkBg 后添加
    val baseDarkBg = remember(dominantColors.bgEnd) {
        forceDarkColor(dominantColors.bgEnd, targetLightness = 0.12f)
    }

    // 通知外部背景色变化
    LaunchedEffect(baseDarkBg) {
        onBackgroundColorChange(baseDarkBg, true)
    }

    DisposableEffect(Unit) {
        onDispose {
            onBackgroundColorChange(null, false)
        }
    }

    // 压暗按钮强调色
    val accentColor = remember(dominantColors.buttonColor) {
        forceDarkColor(dominantColors.buttonColor, targetLightness = 0.3f)
    }

    // 调试日志
    LaunchedEffect(baseDarkBg) {
        android.util.Log.d("PlaylistDetail", "baseDarkBg after darken: $baseDarkBg")
    }


    // ===== 歌单内搜索与排序状态 =====
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(PlaylistSortOrder.DEFAULT) }
    var showSortDialog by remember { mutableStateOf(false) }

    // 过滤与排序后的歌曲列表
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

    // ===== 抽屉状态 =====
    // 选中的歌曲单曲操作菜单
    var songForActionMenu by remember { mutableStateOf<Song?>(null) }
    // 选中的歌曲加入自建歌单抽屉
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseDarkBg)
    ) {
        // 1. 顶部大图封面背景（含渐变过度遮罩）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        ) {
            val coverUrl = playlist.coverUrl ?: songs.firstOrNull()?.artwork
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverUrl)
                        .crossfade(300)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(baseDarkBg)
                )
            }

            // 渐变蒙版：顶部微暗保证返回按钮清晰，底部平滑融入 baseDarkBg
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

        // 2. 主体滚动内容
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = LocalBottomBarPadding.current + 20.dp
            )
        ) {
            // 头部留白与顶部信息区
            item(key = "playlist_header_info") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarPadding + 52.dp, start = 20.dp, end = 20.dp, bottom = 16.dp)
                ) {
                    Spacer(Modifier.height(80.dp))

                    // 歌单标题
                    Text(
                        text = playlist.name.ifBlank { "精选歌单" },
                        style = TextStyle(
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.4.sp,
                            lineHeight = 30.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(8.dp))

                    // 歌曲数量与播放量
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${playlist.trackCount ?: songs.size} 首",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Medium
                            )
                        )
                        if (playlist.playCount != null && playlist.playCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.45f))
                            )
                            Text(
                                text = "${formatPlayCountText(playlist.playCount)} 播放",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // 核心操作栏（播放全部 + 搜索 + 收藏 + 排序）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 播放全部按钮（胶囊设计）
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.22f))
                                .clickable {
                                    if (displaySongs.isNotEmpty()) {
                                        onPlayAll(displaySongs)
                                    }
                                }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "播放全部",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "播放全部",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        // 歌单内搜索按钮
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSearchExpanded) Color.White.copy(alpha = 0.35f)
                                    else Color.White.copy(alpha = 0.18f)
                                )
                                .clickable {
                                    isSearchExpanded = !isSearchExpanded
                                    if (!isSearchExpanded) searchQuery = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "搜索歌曲",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        // 歌单收藏按钮
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFavorite) Rose.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.18f)
                                )
                                .clickable(onClick = onToggleFavoritePlaylist),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (isFavorite) "取消收藏歌单" else "收藏歌单",
                                tint = if (isFavorite) Rose else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        // 排序按钮
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (sortOrder != PlaylistSortOrder.DEFAULT) accentColor.copy(alpha = 0.6f)
                                    else Color.White.copy(alpha = 0.18f)
                                )
                                .clickable { showSortDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = "排序",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 歌单内搜索输入框（折叠展开动画）
                    AnimatedVisibility(
                        visible = isSearchExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
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
                                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 加载/错误/内容态展示
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
                // 歌曲行列表
                itemsIndexed(
                    items = displaySongs,
                    key = { i, s -> "$i:${s.platform}:${s.id}" }
                ) { _, song ->
                    val isCurrent = song.id == currentSong?.id && song.platform == currentSong?.platform
                    PlaylistSongRow(
                        song = song,
                        isCurrent = isCurrent,
                        onClick = { onPlaySong(song, displaySongs) },
                        onMoreClick = { songForActionMenu = song }
                    )
                }
            }
        }

        // 3. 悬浮顶部返回与操作条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarPadding + 6.dp, start = 16.dp, end = 16.dp),
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

    // ===== 排序选择弹窗 =====
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            containerColor = Surface,
            title = {
                Text(
                    text = "歌单排序",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                .padding(horizontal = 12.dp, vertical = 10.dp),
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
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("取消", color = TextSoft)
                }
            }
        )
    }

    // ===== 单曲更多操作底部抽屉 (SongActionBottomSheet) =====
    songForActionMenu?.let { song ->
        val isSongFav = isSongFavorite(song)
        SongActionBottomSheet(
            song = song,
            isFavorite = isSongFav,
            onDismiss = { songForActionMenu = null },
            onToggleFavorite = {
                onToggleFavoriteSong(song)
                songForActionMenu = null
            },
            onAddToPlaylist = {
                val target = song
                songForActionMenu = null
                songForAddToPlaylist = target
            },
            onAddToQueue = {
                onAddToQueue(song)
                songForActionMenu = null
            },
            onPlayNext = {
                onPlayNext(song)
                songForActionMenu = null
            },
            onDownload = {
                onDownloadSong(song)
                songForActionMenu = null
            }
        )
    }

    // ===== 添加到自建歌单抽屉 (AddToPlaylistBottomSheet) =====
    songForAddToPlaylist?.let { song ->
        AddToPlaylistBottomSheet(
            song = song,
            myPlaylists = myPlaylists,
            onDismiss = { songForAddToPlaylist = null },
            onSelectPlaylist = { playlistId ->
                onAddToPlaylist(playlistId, song)
                songForAddToPlaylist = null
            },
            onCreateAndAdd = { newName ->
                onCreatePlaylist(newName, song)
                songForAddToPlaylist = null
            }
        )
    }
}

// ==================== 歌曲行组件 ====================

@Composable
private fun PlaylistSongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面
        SongCover(
            url = song.artwork,
            modifier = Modifier.size(48.dp),
            corner = 8.dp
        )

        Spacer(Modifier.width(12.dp))

        // 歌曲与歌手/专辑
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) PrimaryBrown else Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
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

        Spacer(Modifier.width(8.dp))

        // 更多操作按钮 "..."
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

// ==================== 歌曲更多操作底部抽屉 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongActionBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onDownload: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp)
        ) {
            // 顶部歌曲信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongCover(song.artwork, Modifier.size(46.dp), corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = song.subtitleText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = Stone200
            )

            // 操作列表
            ActionMenuItem(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                iconTint = if (isFavorite) Rose else TextPrimary,
                title = if (isFavorite) "取消收藏" else "收藏歌曲",
                onClick = onToggleFavorite
            )

            ActionMenuItem(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "添加到歌单",
                onClick = onAddToPlaylist
            )

            ActionMenuItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "下一首播放",
                onClick = onPlayNext
            )

            ActionMenuItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "加入播放队列",
                onClick = onAddToQueue
            )

            ActionMenuItem(
                icon = Icons.Filled.Download,
                title = "下载歌曲",
                onClick = onDownload
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = TextPrimary,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            color = TextPrimary
        )
    }
}

// ==================== 添加到自建歌单抽屉 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistBottomSheet(
    song: Song,
    myPlaylists: List<MyPlaylist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onCreateAndAdd: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp)
        ) {
            // 标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "收藏到歌单",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "将《${song.title}》加入自建歌单",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = PrimaryBrown, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("新建歌单", color = PrimaryBrown, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                thickness = 0.5.dp,
                color = Stone200
            )

            // 自建歌单列表
            if (myPlaylists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无自建歌单，点击右上角新建",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSoft
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    itemsIndexed(myPlaylists, key = { _, p -> p.id }) { _, playlist ->
                        val alreadyIn = playlist.songs.any { it.id == song.id && it.platform == song.platform }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPlaylist(playlist.id) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongCover(
                                url = playlist.coverUrl ?: playlist.songs.firstOrNull()?.artwork,
                                modifier = Modifier.size(46.dp),
                                corner = 8.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${playlist.songs.size} 首歌曲",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextSoft
                                )
                            }
                            if (alreadyIn) {
                                Text(
                                    text = "已包含",
                                    style = TextStyle(fontSize = 11.sp, color = TextMuted),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Stone100)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    // 新建歌单对话框
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            containerColor = Surface,
            title = { Text("新建歌单") },
            text = {
                BasicTextField(
                    value = newPlaylistName,
                    onValueChange = { if (it.length <= 30) newPlaylistName = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = TextPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Stone100)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (newPlaylistName.isEmpty()) {
                            Text("请输入歌单名称", color = TextMuted, fontSize = 14.sp)
                        }
                        inner()
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPlaylistName.trim().isNotEmpty(),
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            onCreateAndAdd(name)
                            showCreateDialog = false
                            newPlaylistName = ""
                        }
                    }
                ) {
                    Text("创建并添加", color = if (newPlaylistName.isNotBlank()) PrimaryBrown else TextMuted)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newPlaylistName = ""
                }) {
                    Text("取消", color = TextSoft)
                }
            }
        )

    }

}
