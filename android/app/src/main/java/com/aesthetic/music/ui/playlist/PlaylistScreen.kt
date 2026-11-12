package com.aesthetic.music.ui.playlist

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.PlaylistLinkParser
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.data.settings.PlaylistCoverPick
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.AppDialog
import com.aesthetic.music.ui.components.BottomContentPadding
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ListSearchBar
import com.aesthetic.music.ui.components.SelectionBottomSheet
import com.aesthetic.music.ui.components.SelectionItem
import com.aesthetic.music.ui.components.PlaylistCoverImage
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.library.PlaylistResolveState
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

private sealed class UnifiedPlaylistItem {
    abstract val id: String
    abstract val name: String
    abstract val songCount: Int
    abstract val coverUrl: String?
    abstract val platformTag: String

    data class Custom(val playlist: MyPlaylist) : UnifiedPlaylistItem() {
        override val id: String get() = playlist.id
        override val name: String get() = playlist.name
        override val songCount: Int get() = playlist.songs.size
        override val coverUrl: String? get() = playlist.coverUrl
        override val platformTag: String
            get() = if (playlist.isImported) {
                PlaylistLinkParser.platformName(playlist.sourcePlatform)
            } else {
                "自建"
            }
    }

    data class Favorited(val item: PlaylistItem) : UnifiedPlaylistItem() {
        override val id: String get() = item.id
        override val name: String get() = item.name
        override val songCount: Int get() = item.trackCount?.toInt() ?: 0
        override val coverUrl: String? get() = item.coverUrl
        override val platformTag: String get() = PlaylistLinkParser.platformName(item.platform ?: "").ifBlank { "收藏" }
    }
}

@Composable
fun PlaylistScreen(
    myPlaylists: List<MyPlaylist>,
    favoritePlaylists: List<PlaylistItem> = emptyList(),
    onOpenMyPlaylist: (String) -> Unit,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit = {},
    onCreatePlaylist: (PlaylistEditorResult) -> Unit,
    onEditPlaylist: (String, PlaylistEditorResult) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onOpenImport: () -> Unit,
    onPlayPlaylist: (List<Song>) -> Unit,
    onSharePlaylist: (name: String, subtitle: String) -> Unit = { _, _ -> },
    onRefreshFavorite: (PlaylistItem) -> Unit = {},
    onRemoveFavorite: (PlaylistItem) -> Unit = {},
    importPreview: PlaylistResolveState = PlaylistResolveState.Idle,
    onResolveImport: (String) -> Unit = {},
    onClearImport: () -> Unit = {},
    onPickCover: (Uri) -> PlaylistCoverPick? = { null },
    onDeleteCover: (String?) -> Unit = {},
    onToast: (String) -> Unit = {},
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MyPlaylist?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<UnifiedPlaylistItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MyPlaylist?>(null) }

    var selectedPlatformFilter by remember { mutableStateOf("ALL") }
    var showPlatformSheet by remember { mutableStateOf(false) }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    var sortAscending by remember { mutableStateOf(true) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) searchFocus.requestFocus()
    }

    val allUnifiedPlaylists = remember(myPlaylists, favoritePlaylists) {
        val list = mutableListOf<UnifiedPlaylistItem>()
        myPlaylists.forEach { list.add(UnifiedPlaylistItem.Custom(it)) }
        favoritePlaylists.forEach { list.add(UnifiedPlaylistItem.Favorited(it)) }
        list
    }

    val playlistStats = remember(allUnifiedPlaylists) {
        val totalCount = allUnifiedPlaylists.size
        val customCount = allUnifiedPlaylists.count { it is UnifiedPlaylistItem.Custom }
        val favoriteCount = totalCount - customCount
        Triple(totalCount, customCount, favoriteCount)
    }

    val filteredByCategory = remember(allUnifiedPlaylists, selectedPlatformFilter) {
        when (selectedPlatformFilter) {
            "ALL" -> allUnifiedPlaylists
            "CUSTOM" -> allUnifiedPlaylists.filter { it is UnifiedPlaylistItem.Custom }
            "FAVORITE" -> allUnifiedPlaylists.filter { it is UnifiedPlaylistItem.Favorited }
            else -> allUnifiedPlaylists
        }
    }

    val filtered = remember(filteredByCategory, searchQuery, sortAscending) {
        val q = searchQuery.trim()
        val list = if (q.isEmpty()) {
            filteredByCategory
        } else {
            filteredByCategory.filter { it.name.contains(q, ignoreCase = true) }
        }
        if (sortAscending) list else list.reversed()
    }

    val totalSongCount = remember(myPlaylists, favoritePlaylists) {
        myPlaylists.sumOf { it.songs.size } + favoritePlaylists.sumOf { (it.trackCount ?: 0L).toInt() }
    }

    val topCovers = remember(allUnifiedPlaylists) {
        allUnifiedPlaylists.mapNotNull { it.coverUrl.takeIf { url -> !url.isNullOrBlank() } }.take(4)
    }

    val filterLabel = remember(selectedPlatformFilter) {
        when (selectedPlatformFilter) {
            "ALL" -> "全部"
            "CUSTOM" -> "自建"
            else -> selectedPlatformFilter
        }
    }

    Box(Modifier.fillMaxSize()) {
        HomeAmbientGlowBackground()
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = statusBarTop + AppSpacing.xxl)
        ) {
            LibraryBentoSection(
                playlistCount = allUnifiedPlaylists.size,
                songCount = totalSongCount,
                customCount = playlistStats.second,
                favoriteCount = allUnifiedPlaylists.size - playlistStats.second,
                selectedFilter = selectedPlatformFilter,
                onSelectFilter = { selectedPlatformFilter = it },
                topCovers = topCovers,
                onPlayAll = {
                    val allSongs = allUnifiedPlaylists.flatMap { item ->
                        when (item) {
                            is UnifiedPlaylistItem.Custom -> item.playlist.songs
                            else -> emptyList()
                        }
                    }
                    if (allSongs.isNotEmpty()) onPlayPlaylist(allSongs)
                },
                onShufflePlay = {
                    val allSongs = allUnifiedPlaylists.flatMap { item ->
                        when (item) {
                            is UnifiedPlaylistItem.Custom -> item.playlist.songs
                            else -> emptyList()
                        }
                    }.shuffled()
                    if (allSongs.isNotEmpty()) onPlayPlaylist(allSongs)
                },
                onToggleSearch = {
                    isSearchExpanded = !isSearchExpanded
                    if (!isSearchExpanded) searchQuery = ""
                },
                onCreatePlaylist = { showCreateSheet = true },
                onImportPlaylist = onOpenImport,
                modifier = Modifier.padding(horizontal = AppSpacing.page)
            )

            Spacer(Modifier.height(AppSpacing.xl))

            if (isSearchExpanded) {
                ListSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "搜索歌单名称…",
                    focusRequester = searchFocus,
                    modifier = Modifier.padding(horizontal = AppSpacing.page),
                )
                Spacer(Modifier.height(AppSpacing.xl))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "全部歌单",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { sortAscending = !sortAscending }
                        .padding(horizontal = AppSpacing.xs, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filtered.size}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextSoft,
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "排序",
                        tint = TextSoft,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

            if (allUnifiedPlaylists.isEmpty()) {
                EmptyBox("还没有歌单\n点击上方「新建歌单」或「导入歌单」添加")
            } else if (filtered.isEmpty()) {
                EmptyBox("没有找到与「${searchQuery.trim()}」相关的歌单")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = AppSpacing.page)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Surface),
                        contentPadding = BottomContentPadding
                    ) {
                        itemsIndexed(
                            filtered,
                            key = { _, pl -> pl.id },
                        ) { index, item ->
                            PlaylistRowItem(
                                item = item,
                                onClick = {
                                    when (item) {
                                        is UnifiedPlaylistItem.Custom -> onOpenMyPlaylist(item.playlist.id)
                                        is UnifiedPlaylistItem.Favorited -> onOpenPlaylistDetail(item.item)
                                    }
                                },
                                onPlay = {
                                    when (item) {
                                        is UnifiedPlaylistItem.Custom -> {
                                            if (item.playlist.songs.isNotEmpty()) {
                                                onPlayPlaylist(item.playlist.songs)
                                            }
                                        }
                                        is UnifiedPlaylistItem.Favorited -> {
                                            onOpenPlaylistDetail(item.item)
                                        }
                                    }
                                },
                                onMore = {
                                    selectedItem = item
                                    showActionSheet = true
                                }
                            )

                            if (index < filtered.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 78.dp, end = AppSpacing.xxl),
                                    thickness = 0.6.dp,
                                    color = Stone200.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlatformSheet) {
        val (total, custom, favorite) = playlistStats
        val options = listOf(
            SelectionItem(
                id = "ALL",
                title = "全部",
                subtitle = "$total 个歌单",
                icon = Icons.AutoMirrored.Filled.QueueMusic
            ),
            SelectionItem(
                id = "CUSTOM",
                title = "自建",
                subtitle = "$custom 个歌单",
                icon = Icons.AutoMirrored.Filled.PlaylistAdd
            ),
            SelectionItem(
                id = "FAVORITE",
                title = "收藏",
                subtitle = "$favorite 个歌单",
                icon = Icons.Filled.FavoriteBorder
            ),
        )

        SelectionBottomSheet(
            headerIcon = Icons.AutoMirrored.Filled.QueueMusic,
            title = "选择分类",
            subtitle = "只看自建或收藏的歌单",
            items = options,
            selectedId = selectedPlatformFilter,
            onSelect = { selectedPlatformFilter = it },
            onDismissRequest = { showPlatformSheet = false }
        )
    }

    val actionItem: UnifiedPlaylistItem? = selectedItem
    if (showActionSheet && actionItem != null) {
        AppBottomSheet(
            onDismissRequest = {
                showActionSheet = false
                selectedItem = null
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.page)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceSoft)
                        .padding(AppSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(48.dp)) {
                        if (!actionItem.coverUrl.isNullOrBlank()) {
                            PlaylistCoverImage(actionItem.coverUrl, Modifier.fillMaxSize(), corner = 10.dp)
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Stone200.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(AppSpacing.lg))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = actionItem.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "${actionItem.songCount} 首 · ${actionItem.platformTag}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextSoft
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                showActionSheet = false
                                selectedItem = null
                            }
                            .padding(AppSpacing.xxs)
                    )
                }

                Spacer(Modifier.height(AppSpacing.md))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .padding(vertical = 2.dp)
                ) {
                    PlaylistSheetAction(
                        icon = Icons.Filled.PlayArrow,
                        text = "播放",
                        onClick = {
                            when (val target = actionItem) {
                                is UnifiedPlaylistItem.Custom -> {
                                    if (target.playlist.songs.isNotEmpty()) {
                                        onPlayPlaylist(target.playlist.songs)
                                    } else {
                                        onToast("这个歌单还没有歌曲")
                                    }
                                }
                                is UnifiedPlaylistItem.Favorited -> onOpenPlaylistDetail(target.item)
                                else -> Unit
                            }
                            showActionSheet = false
                            selectedItem = null
                        }
                    )
                    PlaylistSheetAction(
                        icon = Icons.Filled.Share,
                        text = "分享歌单",
                        onClick = {
                            onSharePlaylist(
                                actionItem.name,
                                "${actionItem.songCount} 首 · ${actionItem.platformTag}"
                            )
                            showActionSheet = false
                            selectedItem = null
                        }
                    )
                    if (actionItem is UnifiedPlaylistItem.Custom) {
                        val playlist = actionItem.playlist
                        PlaylistSheetAction(
                            icon = Icons.Filled.DriveFileRenameOutline,
                            text = "重命名",
                            onClick = {
                                editTarget = playlist
                                showActionSheet = false
                                selectedItem = null
                            }
                        )
                        PlaylistSheetAction(
                            icon = Icons.Filled.Image,
                            text = "修改封面",
                            subtitle = "选择一张本地图片作为歌单封面",
                            onClick = {
                                editTarget = playlist
                                showActionSheet = false
                                selectedItem = null
                            }
                        )
                    } else if (actionItem is UnifiedPlaylistItem.Favorited) {
                        PlaylistSheetAction(
                            icon = Icons.Filled.Refresh,
                            text = "更新",
                            subtitle = "重新从平台拉取歌单信息",
                            onClick = {
                                onRefreshFavorite(actionItem.item)
                                showActionSheet = false
                                selectedItem = null
                            }
                        )
                        PlaylistSheetAction(
                            icon = Icons.Filled.FavoriteBorder,
                            text = "取消收藏",
                            onClick = {
                                onRemoveFavorite(actionItem.item)
                                showActionSheet = false
                                selectedItem = null
                            }
                        )
                    }
                    PlaylistSheetAction(
                        icon = Icons.Filled.Delete,
                        text = if (actionItem is UnifiedPlaylistItem.Custom) "删除" else "移除本地记录",
                        textColor = AccentCoral,
                        onClick = {
                            if (actionItem is UnifiedPlaylistItem.Custom) {
                                deleteTarget = actionItem.playlist
                            } else if (actionItem is UnifiedPlaylistItem.Favorited) {
                                onRemoveFavorite(actionItem.item)
                            }
                            showActionSheet = false
                            selectedItem = null
                        }
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = "删除歌单",
            confirmText = "删除",
            onConfirm = {
                onDeletePlaylist(target.id)
                deleteTarget = null
            },
            destructive = true,
        ) {
            val syncNote = if (target.cloudId.isNotBlank())
                "\n该歌单已同步云端，删除后云端也将删除"
            else ""
            Text(
                "确认删除《${target.name}》？\n歌单内 ${target.songs.size} 首歌曲记录将一并移除，此操作不可恢复。$syncNote",
            )
        }
    }

    if (showCreateSheet) {
        PlaylistEditorSheet(
            title = "新建歌单",
            confirmText = "创建歌单",
            showPlatformImport = true,
            importState = importPreview,
            onResolveImport = onResolveImport,
            onClearImport = onClearImport,
            onPickCover = onPickCover,
            onDeleteCover = onDeleteCover,
            onDismiss = {
                showCreateSheet = false
                onClearImport()
            },
            onConfirm = { result ->
                showCreateSheet = false
                onClearImport()
                onCreatePlaylist(result)
            },
            onToast = onToast,
        )
    }

    editTarget?.let { target ->
        PlaylistEditorSheet(
            title = "编辑歌单",
            confirmText = "保存",
            initialName = target.name,
            initialCover = target.cover,
            onDismiss = { editTarget = null },
            onPickCover = onPickCover,
            onDeleteCover = onDeleteCover,
            onConfirm = { result ->
                val id = target.id
                editTarget = null
                onEditPlaylist(id, result)
            },
            onToast = onToast,
        )
    }
}

@Composable
private fun PlaylistRowItem(
    item: UnifiedPlaylistItem,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xxl, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!item.coverUrl.isNullOrBlank()) {
                PlaylistCoverImage(item.coverUrl, Modifier.fillMaxSize(), corner = 10.dp)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(AppSpacing.xl))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(AppSpacing.xxs))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val isCustom = item.platformTag == "自建"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCustom) SurfaceSoft else AppColors.successSurfaceSoft)
                        .padding(horizontal = AppSpacing.xs, vertical = 2.dp)
                ) {
                    Text(
                        text = item.platformTag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (isCustom) TextMuted else AppColors.successText
                    )
                }

                Spacer(Modifier.width(AppSpacing.sm))

                Text(
                    text = "${item.songCount} 首",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = TextSoft
                )
            }
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(AppSpacing.xxs))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "更多操作",
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlaylistSheetAction(
    icon: ImageVector,
    text: String,
    textColor: Color = TextPrimary,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.xl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AppSpacing.xl))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = textColor
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 50.dp, end = AppSpacing.xxl),
        thickness = 0.6.dp,
        color = Stone200.copy(alpha = 0.45f)
    )
}