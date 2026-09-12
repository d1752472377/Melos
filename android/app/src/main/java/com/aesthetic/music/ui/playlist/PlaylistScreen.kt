package com.aesthetic.music.ui.playlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.AppDialog
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

private val DividerColor = Color(0xFFEEEEEE)

@Composable
fun PlaylistScreen(
    myPlaylists: List<MyPlaylist>,
    favoritePlaylists: List<PlaylistItem> = emptyList(),
    onOpenMyPlaylist: (String) -> Unit,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit = {},
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onOpenImport: () -> Unit,
    onPlayPlaylist: (List<Song>) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<MyPlaylist?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<MyPlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<MyPlaylist?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp)
        ) {
            // ============ Header（固定不滚动） ============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "歌单",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = TextPrimary
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabText(
                        text = "自建歌单",
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    Spacer(Modifier.width(16.dp))
                    TabText(
                        text = "收藏歌单",
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )

                    Spacer(Modifier.weight(1f))

                    if (selectedTab == 0) {
                        SimpleAction(
                            text = "新建歌单",
                            icon = Icons.Filled.Add,
                            onClick = { showCreateDialog = true }
                        )
                    } else {
                        SimpleAction(
                            text = "导入歌单",
                            icon = Icons.Filled.Download,
                            onClick = onOpenImport
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

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
                    .weight(1f) // 关键：给内部 LazyColumn 一个确定的高度
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
                    text = "重命名",
                    onClick = {
                        selectedPlaylist?.let { playlist ->
                            renameTarget = playlist
                            renameText = playlist.name
                        }
                        showBottomSheet = false
                        selectedPlaylist = null
                    }
                )
                Spacer(Modifier.height(12.dp))
                BottomSheetAction(
                    icon = Icons.Filled.Delete,
                    text = "删除歌单",
                    textColor = Color(0xFFE53935),
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

    if (showCreateDialog) {
        AppDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = "新建歌单",
            confirmText = "创建",
            onConfirm = {
                if (newPlaylistName.isNotBlank()) {
                    onCreatePlaylist(newPlaylistName.trim())
                    showCreateDialog = false
                    newPlaylistName = ""
                }
            },
            confirmEnabled = newPlaylistName.trim().isNotEmpty(),
        ) {
            BasicTextField(
                value = newPlaylistName,
                onValueChange = { if (it.length <= 30) newPlaylistName = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Stone100)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }

    renameTarget?.let { target ->
        AppDialog(
            onDismissRequest = { renameTarget = null },
            title = "重命名歌单",
            confirmText = "保存",
            onConfirm = {
                if (renameText.isNotBlank()) {
                    onRenamePlaylist(target.id, renameText.trim())
                    renameTarget = null
                }
            },
        ) {
            BasicTextField(
                value = renameText,
                onValueChange = { renameText = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Stone100)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
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
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        if (myPlaylists.isEmpty()) {
            item(key = "my_empty") {
                EmptyStateView(
                    title = "还没有自建歌单",
                    subtitle = "点击上方「新建歌单」创建你的第一个歌单",
                    icon = Icons.Filled.LibraryMusic
                )
            }
        } else {
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
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        if (favoritePlaylists.isEmpty()) {
            item(key = "fav_empty") {
                EmptyStateView(
                    title = "还没有收藏的歌单",
                    subtitle = "浏览歌单时点击 ♡ 即可收藏到这里",
                    icon = Icons.Filled.FavoriteBorder
                )
            }
        } else {
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

@Composable
private fun TabText(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = if (isSelected) 17.sp else 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        ),
        color = if (isSelected) PrimaryBrown else TextSoft,
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun SimpleAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryBrown,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            color = PrimaryBrown,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: MyPlaylist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongCover(
                url = playlist.coverUrl ?: playlist.songs.firstOrNull()?.artwork,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                corner = 12.dp
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${playlist.songs.size} 首歌",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextSoft
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = PrimaryBrown,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "更多操作",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 90.dp, end = 20.dp),
            thickness = 0.5.dp,
            color = DividerColor
        )
    }
}

@Composable
private fun CollectedPlaylistRow(
    playlist: PlaylistItem,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongCover(
                url = playlist.coverUrl,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                corner = 12.dp
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val playCountPart = if (playlist.playCount != null && playlist.playCount > 0) {
                    " · ${com.aesthetic.music.ui.components.formatPlayCountText(playlist.playCount)} 播放"
                } else ""
                Text(
                    text = "${playlist.trackCount ?: 0} 首歌$playCountPart",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextSoft
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 90.dp, end = 20.dp),
            thickness = 0.5.dp,
            color = DividerColor
        )
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            color = textColor
        )
    }
}

@Composable
private fun EmptyStateView(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
            color = TextSoft
        )
    }
}