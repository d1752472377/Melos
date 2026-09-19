package com.aesthetic.music.ui.playlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.aesthetic.music.ui.components.PlaylistEditorResult
import com.aesthetic.music.ui.components.PlaylistEditorSheet
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.library.PlaylistResolveState
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

@Composable
fun PlaylistScreen(
    myPlaylists: List<MyPlaylist>,
    favoritePlaylists: List<PlaylistItem> = emptyList(),
    onOpenMyPlaylist: (String) -> Unit,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit = {},
    /** 新建歌单：名称 + 封面 + （可选的）平台导入原始记录 */
    onCreatePlaylist: (PlaylistEditorResult) -> Unit,
    /** 编辑已有歌单的名称 / 封面 */
    onEditPlaylist: (String, PlaylistEditorResult) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onOpenImport: () -> Unit,
    onPlayPlaylist: (List<Song>) -> Unit,
    /** 「新建歌单」表单里可选导入外部歌单的解析状态 */
    importPreview: PlaylistResolveState = PlaylistResolveState.Idle,
    onResolveImport: (String) -> Unit = {},
    onClearImport: () -> Unit = {},
    onToast: (String) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MyPlaylist?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<MyPlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<MyPlaylist?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarTop + 16.dp)
        ) {
            // ============ Header 与大标题 ============
            Text(
                text = "我的歌单",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(16.dp))

            // ============ 顶部汇总大卡片（设计语言同音乐收藏/播放历史/下载音乐） ============
            PlaylistSummaryCard(
                myPlaylistCount = myPlaylists.size,
                favoritePlaylistCount = favoritePlaylists.size,
                onCreatePlaylist = { showCreateSheet = true },
                onImportPlaylist = onOpenImport,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(16.dp))

            // ============ 自建 / 收藏 平滑切换胶囊 ============
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlaylistTabPill(
                    text = "自建歌单",
                    count = myPlaylists.size,
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                )
                PlaylistTabPill(
                    text = "收藏歌单",
                    count = favoritePlaylists.size,
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                )
            }

            Spacer(Modifier.height(14.dp))

            // ============ 内容区：动画 + 单个 LazyColumn ============
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(280)) { it } + fadeIn(tween(280)))
                            .togetherWith(
                                slideOutHorizontally(tween(280)) { -it } + fadeOut(tween(280))
                            )
                    } else {
                        (slideInHorizontally(tween(280)) { -it } + fadeIn(tween(280)))
                            .togetherWith(
                                slideOutHorizontally(tween(280)) { it } + fadeOut(tween(280))
                            )
                    }
                },
                label = "playlist_tab",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { tab ->
                if (tab == 0) {
                    MyPlaylistContent(
                        myPlaylists = myPlaylists,
                        onOpenMyPlaylist = onOpenMyPlaylist,
                        onPlayPlaylist = onPlayPlaylist,
                        onMore = { playlist ->
                            selectedPlaylist = playlist
                            showBottomSheet = true
                        }
                    )
                } else {
                    FavoritePlaylistContent(
                        favoritePlaylists = favoritePlaylists,
                        onOpenPlaylistDetail = onOpenPlaylistDetail
                    )
                }
            }
        }
    }
    // ================= 弹窗与抽屉 =================
    if (showBottomSheet && selectedPlaylist != null) {
        AppBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${selectedPlaylist?.songs?.size ?: 0} 首歌",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft
                )
                Spacer(Modifier.height(24.dp))

                BottomSheetAction(
                    icon = Icons.Filled.PlayArrow,
                    text = "播放全部",
                    onClick = {
                        selectedPlaylist?.let { playlist ->
                            if (playlist.songs.isNotEmpty()) {
                                onPlayPlaylist(playlist.songs)
                            }
                        }
                        showBottomSheet = false
                        selectedPlaylist = null
                    }
                )
                Spacer(Modifier.height(12.dp))
                BottomSheetAction(
                    icon = Icons.Filled.Edit,
                    text = "编辑名称与封面",
                    onClick = {
                        selectedPlaylist?.let { playlist ->
                            editTarget = playlist
                        }
                        showBottomSheet = false
                        selectedPlaylist = null
                    }
                )
                Spacer(Modifier.height(12.dp))
                BottomSheetAction(
                    icon = Icons.Filled.Delete,
                    text = "删除歌单",
                    textColor = AccentCoral,
                    onClick = {
                        selectedPlaylist?.let { playlist ->
                            deleteTarget = playlist
                        }
                        showBottomSheet = false
                        selectedPlaylist = null
                    }
                )
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

    // ================= 新建 / 编辑歌单表单（名称 + 本机封面 + 可选的平台导入） =================
    if (showCreateSheet) {
        PlaylistEditorSheet(
            title = "新建歌单",
            confirmText = "创建歌单",
            // 新建时才提供"从其他平台导入"，导入进来的歌单名/封面/歌曲作为原始记录
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

// ==================== 内容区（拆出来给 AnimatedContent 用） ====================

/** 自建歌单内容 */
@Composable
private fun MyPlaylistContent(
    myPlaylists: List<MyPlaylist>,
    onOpenMyPlaylist: (String) -> Unit,
    onPlayPlaylist: (List<Song>) -> Unit,
    onMore: (MyPlaylist) -> Unit,
) {
    if (myPlaylists.isEmpty()) {
        EmptyBox("还没有自建歌单\n点击上方「新建歌单」自己创建，或在表单里从其他平台导入歌单")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = BottomContentPadding,
        ) {
            items(myPlaylists, key = { it.id }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onOpenMyPlaylist(playlist.id) },
                    onPlay = {
                        if (playlist.songs.isNotEmpty()) {
                            onPlayPlaylist(playlist.songs)
                        }
                    },
                    onMore = { onMore(playlist) }
                )
            }
        }
    }
}

/** 收藏歌单内容 */
@Composable
private fun FavoritePlaylistContent(
    favoritePlaylists: List<PlaylistItem>,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
) {
    if (favoritePlaylists.isEmpty()) {
        EmptyBox("还没有收藏的歌单\n浏览歌单时点 ♡ 收藏，或用上方「导入歌单」粘贴链接/分享文案")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = BottomContentPadding,
        ) {
            items(favoritePlaylists, key = { it.id }) { playlist ->
                CollectedPlaylistRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylistDetail(playlist) }
                )
            }
        }
    }
}

// ==================== 子组件 ====================

/**
 * 顶部汇总大卡片：我的音乐库概览 + 新建歌单 / 导入歌单操作按钮（iOS 玻璃拟态风格）
 */
@Composable
private fun PlaylistSummaryCard(
    myPlaylistCount: Int,
    favoritePlaylistCount: Int,
    onCreatePlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(22.dp), elevation = 6.dp)
            .padding(18.dp),
    ) {
        // 标签
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(AccentAmber.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = "歌单资产",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = AccentAmber,
            )
        }

        Spacer(Modifier.height(10.dp))

        // 标题
        Text(
            text = "我的音乐库",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = TextPrimary,
        )

        Spacer(Modifier.height(4.dp))

        // 统计信息
        Text(
            text = "自建 $myPlaylistCount 个歌单 · 收藏 $favoritePlaylistCount 个歌单",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = TextSoft,
        )

        Spacer(Modifier.height(18.dp))

        // 操作按钮行：新建歌单 + 导入歌单
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. 新建歌单 (主要操作按钮)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(PrimaryBrown)
                    .clickable(onClick = onCreatePlaylist),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "新建歌单",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                )
            }

            // 2. 导入歌单 (次要操作按钮，玻璃拟态卡片)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .iosGlassCard(shape = RoundedCornerShape(19.dp), elevation = 2.dp)
                .clickable(onClick = onImportPlaylist),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "导入歌单",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TextPrimary,
                )
            }
        }
    }
}

/**
 * 分类胶囊标签（自建歌单 / 收藏歌单平滑切换）
 */
@Composable
private fun PlaylistTabPill(
    text: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val glassModifier = if (isSelected) {
        Modifier
            .background(PrimaryBrown, shape = shape)
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
    } else {
        Modifier.iosGlassCard(shape = shape, elevation = 2.dp)
    }

    Row(
        modifier = glassModifier
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else TextSoft,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextMuted,
        )
    }
}

/**
 * 自建歌单卡片（iOS 玻璃拟态风格）
 */
@Composable
private fun PlaylistRow(
    playlist: MyPlaylist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .iosGlassCard(shape = RoundedCornerShape(18.dp), elevation = 3.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongCover(
                url = playlist.coverUrl,
                modifier = Modifier.size(54.dp),
                corner = 12.dp,
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append("${playlist.songs.size} 首歌")
                        if (playlist.isImported) {
                            append(" · 来自 ")
                            append(PlaylistLinkParser.platformName(playlist.sourcePlatform))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSoft,
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = PrimaryBrown,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "更多操作",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 收藏歌单卡片（iOS 玻璃拟态风格）
 */
@Composable
private fun CollectedPlaylistRow(
    playlist: PlaylistItem,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .iosGlassCard(shape = RoundedCornerShape(18.dp), elevation = 3.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongCover(
                url = playlist.coverUrl,
                modifier = Modifier.size(54.dp),
                corner = 12.dp,
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                val playCountPart = if (playlist.playCount != null && playlist.playCount > 0) {
                    " · ${com.aesthetic.music.ui.components.formatPlayCountText(playlist.playCount)} 播放"
                } else ""
                Text(
                    text = "${playlist.trackCount ?: 0} 首歌$playCountPart",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSoft,
                )
            }
        }
    }
}

@Composable
private fun BottomSheetAction(
    icon: ImageVector,
    text: String,
    textColor: Color = TextPrimary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = textColor,
        )
    }
}