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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
import com.aesthetic.music.ui.components.SongCover
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
    importPreview: PlaylistResolveState = PlaylistResolveState.Idle,
    onResolveImport: (String) -> Unit = {},
    onClearImport: () -> Unit = {},
    onToast: (String) -> Unit = {},
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MyPlaylist?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<MyPlaylist?>(null) }
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

    // 平台统计（用于图 5 BottomSheet）
    val platformStats = remember(allUnifiedPlaylists) {
        val totalCount = allUnifiedPlaylists.size
        val customCount = allUnifiedPlaylists.count { it is UnifiedPlaylistItem.Custom && !it.playlist.isImported }
        // 外部导入或收藏的平台统计
        val otherPlatformCounts = allUnifiedPlaylists
            .filter { !(it is UnifiedPlaylistItem.Custom && !it.playlist.isImported) }
            .groupBy { it.platformTag }
            .mapValues { it.value.size }

        Triple(totalCount, customCount, otherPlatformCounts)
    }

    // 筛选
    val filteredByPlatform = remember(allUnifiedPlaylists, selectedPlatformFilter) {
        when (selectedPlatformFilter) {
            "ALL" -> allUnifiedPlaylists
            "CUSTOM" -> allUnifiedPlaylists.filter { it is UnifiedPlaylistItem.Custom && !it.playlist.isImported }
            else -> allUnifiedPlaylists.filter { it.platformTag == selectedPlatformFilter }
        }
    }

    // 搜索过滤
    val filtered = remember(filteredByPlatform, searchQuery, sortAscending) {
        val q = searchQuery.trim()
        val list = if (q.isEmpty()) {
            filteredByPlatform
        } else {
            filteredByPlatform.filter { it.name.contains(q, ignoreCase = true) }
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = statusBarTop + 16.dp)
        ) {
            // ===== 1. 顶部大卡片 =====
            PlaylistHeaderCard(
                covers = topCovers,
                platformLabel = filterLabel,
                onPlatformClick = { showPlatformSheet = true },
                onSearchClick = {
                    isSearchExpanded = !isSearchExpanded
                    if (!isSearchExpanded) searchQuery = ""
                },
                playlistCount = allUnifiedPlaylists.size,
                songCount = totalSongCount,
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
                                    when (item) {
                                        is UnifiedPlaylistItem.Custom -> {
                                            selectedPlaylist = item.playlist
                                            showActionSheet = true
                                        }
                                        is UnifiedPlaylistItem.Favorited -> {
                                            onOpenPlaylistDetail(item.item)
                                        }
                                    }
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

    // ===== 平台筛选抽屉（对应参考图 5） =====
    if (showPlatformSheet) {
        val (total, custom, others) = platformStats
        val options = mutableListOf<SelectionItem<String>>()

        options.add(
            SelectionItem(
                id = "ALL",
                title = "全部",
                subtitle = "$total 个歌单",
                icon = Icons.AutoMirrored.Filled.QueueMusic
            )
        )
        options.add(
            SelectionItem(
                id = "CUSTOM",
                title = "自建",
                subtitle = "$custom 个歌单",
                icon = Icons.Filled.PlaylistAdd
            )
        )
        others.forEach { (platform, count) ->
            options.add(
                SelectionItem(
                    id = platform,
                    title = platform,
                    subtitle = "$count 个歌单",
                    icon = Icons.Filled.MusicNote
                )
            )
        }

        SelectionBottomSheet(
            headerIcon = Icons.AutoMirrored.Filled.QueueMusic,
            title = "选择平台",
            subtitle = "只看某个平台的歌单",
            items = options,
            selectedId = selectedPlatformFilter,
            onSelect = { selectedPlatformFilter = it },
            onDismissRequest = { showPlatformSheet = false }
        )
    }

    // ===== 歌单操作底部弹窗 =====
    if (showActionSheet && selectedPlaylist != null) {
        AppBottomSheet(
            onDismissRequest = {
                showActionSheet = false
                selectedPlaylist = null
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = selectedPlaylist?.name ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${selectedPlaylist?.songs?.size ?: 0} 首歌",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft
                )
                Spacer(Modifier.height(20.dp))

                BottomSheetActionItem(
                    icon = Icons.Filled.PlayArrow,
                    text = "播放全部",
                    onClick = {
                        selectedPlaylist?.let { playlist ->
                            if (playlist.songs.isNotEmpty()) {
                                onPlayPlaylist(playlist.songs)
                            }
                        }
                        showActionSheet = false
                        selectedPlaylist = null
                    }
                )
                Spacer(Modifier.height(10.dp))
                BottomSheetActionItem(
                    icon = Icons.Filled.Edit,
                    text = "编辑名称与封面",
                    onClick = {
                        selectedPlaylist?.let { playlist ->
                            editTarget = playlist
                        }
                        showActionSheet = false
                        selectedPlaylist = null
                    }
                )
                Spacer(Modifier.height(10.dp))
                BottomSheetActionItem(
                    icon = Icons.Filled.Delete,
                    text = "删除歌单",
                    textColor = AccentCoral,
                    onClick = {
                        selectedPlaylist?.let { playlist ->
                            deleteTarget = playlist
                        }
                        showActionSheet = false
                        selectedPlaylist = null
                    }
                )
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
                SongCover(item.coverUrl, Modifier.fillMaxSize(), corner = 10.dp)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
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