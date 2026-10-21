package com.aesthetic.music.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.AppDialog
import com.aesthetic.music.ui.components.BottomContentPadding
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ListSearchBar
import com.aesthetic.music.ui.components.PlaylistEditorResult
import com.aesthetic.music.ui.components.PlaylistEditorSheet
import com.aesthetic.music.ui.components.QuadCover
import com.aesthetic.music.ui.components.SelectionBottomSheet
import com.aesthetic.music.ui.components.SelectionItem
import com.aesthetic.music.ui.components.PlaylistCoverImage
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.library.PlaylistResolveState
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextFaint
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/** 统一展示的歌单模型（合并自建与收藏） */
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

/**
 * 我的歌单页（参考现代卡片设计图 2 & 图 5 重塑）：
 * 1. 顶部大卡片：2x2四宫格封面 + "我的歌单" + "☲ 全部 ∨" 平台筛选胶囊 + 批量与搜索图标 + 2列统计（歌单/歌曲）+ 新建歌单/导入歌单双操作胶囊；
 * 2. 规范的“选择平台”抽屉（BottomSheet），带各平台数量统计与选中红勾；
 * 3. 歌单列表白底圆角容器 + 平台标签药丸 + ▶ 播放与 ··· 更多操作。
 */
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
    /** 分享歌单（名称 + 副标题），由宿主决定分享渠道 */
    onSharePlaylist: (name: String, subtitle: String) -> Unit = { _, _ -> },
    /** 「更新」收藏的歌单：重新从平台拉取名/封面/曲目数 */
    onRefreshFavorite: (PlaylistItem) -> Unit = {},
    /** 取消收藏（或移除收藏歌单的本地记录） */
    onRemoveFavorite: (PlaylistItem) -> Unit = {},
    importPreview: PlaylistResolveState = PlaylistResolveState.Idle,
    onResolveImport: (String) -> Unit = {},
    onClearImport: () -> Unit = {},
    onToast: (String) -> Unit = {},
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MyPlaylist?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<UnifiedPlaylistItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MyPlaylist?>(null) }

    // 平台筛选：ALL, CUSTOM, 或具体平台名称
    var selectedPlatformFilter by remember { mutableStateOf("ALL") }
    var showPlatformSheet by remember { mutableStateOf(false) }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    // 排序升降序：true 为默认正序，false 为倒序
    var sortAscending by remember { mutableStateOf(true) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) searchFocus.requestFocus()
    }

    // 全部歌单（合并自建与收藏）
    val allUnifiedPlaylists = remember(myPlaylists, favoritePlaylists) {
        val list = mutableListOf<UnifiedPlaylistItem>()
        myPlaylists.forEach { list.add(UnifiedPlaylistItem.Custom(it)) }
        favoritePlaylists.forEach { list.add(UnifiedPlaylistItem.Favorited(it)) }
        list
    }

    // 分类统计（用于分类 BottomSheet 与胶囊计数）
    // 维度只有两个：自建（我的歌单，含从平台导入的）与收藏（在平台上收藏进来的歌单）。
    // 不再按平台切分——「自建」和「网易云」是两种不同维度，混在一排里用户无法判断该点哪个。
    val playlistStats = remember(allUnifiedPlaylists) {
        val totalCount = allUnifiedPlaylists.size
        val customCount = allUnifiedPlaylists.count { it is UnifiedPlaylistItem.Custom }
        val favoriteCount = totalCount - customCount
        Triple(totalCount, customCount, favoriteCount)
    }

    // 筛选
    val filteredByCategory = remember(allUnifiedPlaylists, selectedPlatformFilter) {
        when (selectedPlatformFilter) {
            "ALL" -> allUnifiedPlaylists
            "CUSTOM" -> allUnifiedPlaylists.filter { it is UnifiedPlaylistItem.Custom }
            "FAVORITE" -> allUnifiedPlaylists.filter { it is UnifiedPlaylistItem.Favorited }
            else -> allUnifiedPlaylists
        }
    }

    // 搜索过滤
    val filtered = remember(filteredByCategory, searchQuery, sortAscending) {
        val q = searchQuery.trim()
        val list = if (q.isEmpty()) {
            filteredByCategory
        } else {
            filteredByCategory.filter { it.name.contains(q, ignoreCase = true) }
        }
        if (sortAscending) list else list.reversed()
    }

    // 统计数据（歌单总数、歌曲总数）
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
                .padding(top = statusBarTop + 16.dp)
        ) {
            // ===== 1. 顶部 Apple Bento 网格媒体库大卡片 =====
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
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(14.dp))

            // ===== 2. 搜索栏展开 =====
            if (isSearchExpanded) {
                ListSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "搜索歌单名称…",
                    focusRequester = searchFocus,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(14.dp))
            }

            // ===== 3. 列表标题栏 =====
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

                // 数量与排序切换
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { sortAscending = !sortAscending }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
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

            Spacer(Modifier.height(10.dp))

            // ===== 4. 歌单列表容器 =====
            if (allUnifiedPlaylists.isEmpty()) {
                EmptyBox("还没有歌单\n点击上方「新建歌单」或「导入歌单」添加")
            } else if (filtered.isEmpty()) {
                EmptyBox("没有找到与「${searchQuery.trim()}」相关的歌单")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp)
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
                                    // 两种歌单都要能打开动作弹窗：收藏的歌单也需要「播放 / 分享 / 更新 / 取消收藏」
                                    selectedItem = item
                                    showActionSheet = true
                                }
                            )

                            if (index < filtered.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 78.dp, end = 16.dp),
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

    // ===== 分类筛选抽屉（对齐参考图 4 的「选择平台」弹窗版式，但维度改成归属） =====
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
                icon = Icons.Filled.PlaylistAdd
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

    // ===== 歌单操作底部弹窗 =====
    // ===== 歌单操作底部弹窗（对齐参考图 5/6 的版式）=====
    // 版式：顶部一张「歌单信息条」（封面 + 名称 + 数量·分类），下面一组动作。
    // 与参考图的差别：**没有「赞助用户」这一项**，封面也不需要任何付费门槛——
    // 用户自选的封面会被压成 288px 的 JPEG 内联副本（见 PlaylistCoverStore.encodeCoverDataUrl），
    // 跟着歌单记录一起走云端同步，换设备登录即可看到同一张封面。
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
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                // 歌单信息条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceSoft)
                        .padding(12.dp),
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
                    Spacer(Modifier.width(12.dp))
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
                            .padding(4.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 动作组（一张圆角卡，条目间 1dp 分隔线）
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

    // ===== 删除确认弹窗 =====
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

    // ===== 新建歌单表单 =====
    if (showCreateSheet) {
        PlaylistEditorSheet(
            title = "新建歌单",
            confirmText = "创建歌单",
            showPlatformImport = true,
            importState = importPreview,
            onResolveImport = onResolveImport,
            onClearImport = onClearImport,
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

    // ===== 编辑歌单表单 =====
    editTarget?.let { target ->
        PlaylistEditorSheet(
            title = "编辑歌单",
            confirmText = "保存",
            initialName = target.name,
            initialCover = target.cover,
            onDismiss = { editTarget = null },
            onConfirm = { result ->
                val id = target.id
                editTarget = null
                onEditPlaylist(id, result)
            },
            onToast = onToast,
        )
    }
}

/**
 * 顶部 Hero 大卡片（对应参考图 2）
 */
@Composable
private fun PlaylistHeaderCard(
    covers: List<String>,
    platformLabel: String,
    onPlatformClick: () -> Unit,
    onSearchClick: () -> Unit,
    playlistCount: Int,
    songCount: Int,
    onCreatePlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = Palette.isDark

    Column(
        modifier = modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(24.dp), elevation = 4.dp)
            .padding(18.dp)
    ) {
        // 第一行：左侧 2x2 拼图 + 标题/筛选药丸 + 右侧管理与搜索
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuadCover(
                covers = covers,
                size = 76.dp,
                corner = 16.dp,
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "我的歌单",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                // 平台下拉药丸胶囊
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceSoft)
                        .clickable(onClick = onPlatformClick)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = platformLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextPrimary
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 右上角操作区：搜索小按钮
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SurfaceSoft)
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 第二行：内嵌 2 列统计卡片（歌单数 / 歌曲数）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isDark) SurfaceSoft.copy(alpha = 0.5f) else Surface)
                .height(IntrinsicSize.Min)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistStatsColumn(
                label = "歌单",
                value = "$playlistCount",
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .background(Stone200.copy(alpha = 0.45f))
            )

            PlaylistStatsColumn(
                label = "歌曲",
                value = "$songCount",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // 第三行：新建歌单（墨绿实心）与导入歌单（浅白底）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：新建歌单 (墨绿色背景胶囊)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2C5E43))
                    .clickable(onClick = onCreatePlaylist),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "新建歌单",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }

            // 右：导入歌单 (浅白底胶囊)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) SurfaceSoft else Surface)
                    .clickable(onClick = onImportPlaylist),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "导入歌单",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary
                )
            }
        }
    }
}

/**
 * 统计单列
 */
@Composable
private fun PlaylistStatsColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(TextSoft)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = TextSoft
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary
        )
    }
}

/**
 * 歌单列表单项（对应参考图 2）：
 * 圆角封面、歌单名、平台小胶囊 + 歌曲数、右侧播放三角与三点
 */
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
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面：有封面展示封面，无封面展示灰色圆角加音符
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

        Spacer(Modifier.width(14.dp))

        // 歌单标题与副标题信息
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

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 平台小标签胶囊
                val isCustom = item.platformTag == "自建"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCustom) SurfaceSoft else Color(0xFFE8F5E9))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.platformTag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (isCustom) TextMuted else Color(0xFF2E7D32)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "${item.songCount} 首",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = TextSoft
                )
            }
        }

        // 播放小三角
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

        Spacer(Modifier.width(4.dp))

        // 三点菜单
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
private fun BottomSheetActionItem(
    icon: ImageVector,
    text: String,
    textColor: Color = TextPrimary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = textColor
        )
    }
}

/**
 * 动作弹窗里的一行：图标 + 标题（可选副标题）+ 文本色。
 *
 * 与旧的 [BottomSheetActionItem] 的区别：多了可选副标题（「修改封面」「更新」需要一句解释），
 * 且条目之间由外层卡片的 1dp 分隔线切分，不再靠 10dp 的 Spacer 堆出间距——
 * 参考图里那一组动作是一张整卡，条目之间只有一条细线。
 */
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
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
        modifier = Modifier.padding(start = 50.dp, end = 16.dp),
        thickness = 0.6.dp,
        color = Stone200.copy(alpha = 0.45f)
    )
}