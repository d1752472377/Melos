package com.aesthetic.music.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 歌单中心页（重新设计）：
 * 只展示自建歌单，空状态提示新建或导入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    myPlaylists: List<MyPlaylist>,
    onOpenMyPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onOpenImport: () -> Unit,
    onPlayPlaylist: (List<Song>) -> Unit,
) {
    // 新建与重命名弹窗状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<MyPlaylist?>(null) }
    var renameText by remember { mutableStateOf("") }

    // 底部抽屉状态
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<MyPlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<MyPlaylist?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (myPlaylists.isEmpty()) {
            // 空状态
            EmptyPlaylistState(
                onCreatePlaylist = { showCreateDialog = true },
                onOpenImport = onOpenImport
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = 24.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = LocalBottomBarPadding.current
                )
            ) {
                // 顶部标题
                item(key = "playlist_header") {
                    Column {
                        Text(
                            text = "我的歌单",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${myPlaylists.size} 个歌单",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft
                        )
                    }
                }

                // 操作按钮行
                item(key = "playlist_actions") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionButton(
                            text = "新建歌单",
                            icon = {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = PrimaryBrown,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = { showCreateDialog = true }
                        )

                        ActionButton(
                            text = "导入歌单",
                            icon = {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = TextSoft,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            onClick = onOpenImport
                        )
                    }
                }

                // 歌单列表
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
        }
    }

    // 底部抽屉：歌单操作
    if (showBottomSheet && selectedPlaylist != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                selectedPlaylist = null
            },
            containerColor = Surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                // 歌单名称
                Text(
                    text = selectedPlaylist?.name ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
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

                Spacer(Modifier.height(16.dp))

                // 操作项
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

                Spacer(Modifier.height(8.dp))

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

                Spacer(Modifier.height(8.dp))

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

    // 删除歌单确认对话框
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除歌单") },
            text = {
                val syncNote = if (target.cloudId.isNotBlank())
                    "\n该歌单已同步云端，删除后云端也将删除"
                else ""
                Text(
                    "确认删除《${target.name}》？\n歌单内 ${target.songs.size} 首歌曲记录将一并移除，此操作不可恢复。$syncNote",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlaylist(target.id)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    // 新建歌单对话框
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
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
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPlaylistName.trim().isNotEmpty(),
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim())
                            showCreateDialog = false
                            newPlaylistName = ""
                        }
                    }
                ) {
                    Text(
                        "创建",
                        color = if (newPlaylistName.isNotBlank()) PrimaryBrown else TextMuted
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newPlaylistName = ""
                    }
                ) { Text("取消", color = TextSoft) }
            }
        )
    }

    // 重命名歌单对话框
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名歌单") },
            text = {
                BasicTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = TextPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Stone100)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenamePlaylist(target.id, renameText.trim())
                            renameTarget = null
                        }
                    }
                ) { Text("保存", color = PrimaryBrown) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消", color = TextSoft) }
            }
        )
    }
}

// ==================== 子组件 ====================

@Composable
private fun EmptyPlaylistState(
    onCreatePlaylist: () -> Unit,
    onOpenImport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "还没有收藏的歌单",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "新建或导入，把喜欢的歌单收进来",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp
            ),
            color = TextSoft
        )
        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                text = "新建歌单",
                icon = {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = PrimaryBrown,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = onCreatePlaylist
            )
            ActionButton(
                text = "导入歌单",
                icon = {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint = TextSoft,
                        modifier = Modifier.size(15.dp)
                    )
                },
                onClick = onOpenImport
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = TextPrimary
        )
    }
}

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
            .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongCover(
            url = playlist.coverUrl ?: playlist.songs.firstOrNull()?.artwork,
            modifier = Modifier.size(64.dp),
            corner = 12.dp
        )

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "${playlist.songs.size} 首歌",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp
                ),
                color = TextSoft
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(PrimaryBrown)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(4.dp))

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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            ),
            color = textColor
        )
    }
}