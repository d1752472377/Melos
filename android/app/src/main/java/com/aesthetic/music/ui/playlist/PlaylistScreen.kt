package com.aesthetic.music.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

// 补充：建议在 Theme 中定义这些颜色，这里为了演示直接硬编码
private val AppBackground = Color(0xFFF7F8FA)
private val CardBackground = Color.White
private val TabSelectedBg = Color(0xFFE8E0D8) // 浅棕色底
private val TabSelectedText = Color(0xFF5D4037) // 深棕色字

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

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp), // 增加间距
            contentPadding = PaddingValues(
                top = 32.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = LocalBottomBarPadding.current + 20.dp
            )
        ) {
            // ================= 顶部 Header =================
            item(key = "playlist_header") {
                Column {
                    // 标题行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "我的音乐",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = TextPrimary
                        )
                        Spacer(Modifier.weight(1f))
                        // 数量统计放在右侧，显得更精致
                        Text(
                            text = if (selectedTab == 0) "${myPlaylists.size} 个歌单" else "${favoritePlaylists.size} 个收藏",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // 胶囊 Tab 切换
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.6f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TabButton(
                            text = "自建歌单",
                            isSelected = selectedTab == 0,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTab = 0 }
                        )
                        TabButton(
                            text = "收藏歌单",
                            isSelected = selectedTab == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTab = 1 }
                        )
                    }
                }
            }

            // ================= 操作按钮区 =================
            if (selectedTab == 0) {
                item(key = "playlist_actions") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionButton(
                            text = "新建歌单",
                            icon = Icons.Filled.Add,
                            isPrimary = true,
                            onClick = { showCreateDialog = true }
                        )
                        ActionButton(
                            text = "导入歌单",
                            icon = Icons.Filled.Download,
                            isPrimary = false,
                            onClick = onOpenImport
                        )
                    }
                }

                // ================= 自建歌单列表 =================
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
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onOpenMyPlaylist(playlist.id) },
                            onPlay = {
                                if (playlist.songs.isNotEmpty()) {
                                    onPlayPlaylist(playlist.songs)
                                }
                            },
                            onMore = {
                                selectedPlaylist = playlist
                                showBottomSheet = true
                            }
                        )
                    }
                }
            } else {
                // ================= 收藏歌单列表 =================
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
                        CollectedPlaylistCard(
                            playlist = playlist,
                            onClick = { onOpenPlaylistDetail(playlist) }
                        )
                    }
                }
            }
        }
    }

    // ================= 弹窗与抽屉 (保持原有逻辑，仅微调样式) =================
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

// ==================== 重新设计的子组件 ====================

/** 胶囊形状的 Tab 按钮 */
@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) TabSelectedBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) TabSelectedText else TextSoft
        )
    }
}

/** 重新设计的操作按钮 */
@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPrimary) PrimaryBrown else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPrimary) Color.White else PrimaryBrown,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (isPrimary) Color.White else TextPrimary
        )
    }
}

/** 自建歌单卡片 */
@Composable
private fun PlaylistCard(
    playlist: MyPlaylist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .background(CardBackground)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面增加投影
        SongCover(
            url = playlist.coverUrl ?: playlist.songs.firstOrNull()?.artwork,
            modifier = Modifier
                .size(64.dp)
                .shadow(4.dp, RoundedCornerShape(14.dp)),
            corner = 14.dp
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${playlist.songs.size} 首歌",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = TextSoft
            )
        }

        // 播放按钮：使用浅色底 + 图标，更精致
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(PrimaryBrown.copy(alpha = 0.1f))
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

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
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
}

/** 收藏歌单卡片 */
@Composable
private fun CollectedPlaylistCard(
    playlist: PlaylistItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .background(CardBackground)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongCover(
            url = playlist.coverUrl,
            modifier = Modifier
                .size(64.dp)
                .shadow(4.dp, RoundedCornerShape(14.dp)),
            corner = 14.dp
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
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
}

/** 底部抽屉操作项 */
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

/** 统一的空状态视图 */
@Composable
private fun EmptyStateView(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color.White),
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