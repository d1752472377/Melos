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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextFaint
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 歌单中心页（第三个导航 Tab）：
 * 1. 收藏的歌单（来自推荐/收藏的精选歌单列表）
 * 2. 我的自建歌单（支持创建、重命名、删除）
 *
 * 职责收敛：搜索已独立为全局搜索页（onOpenSearch），
 * 最近播放独立成 HistoryScreen（"我的"页进入），本页不再杂糅。
 */
@Composable
fun PlaylistScreen(
    myPlaylists: List<MyPlaylist>,
    collectedPlaylists: List<PlaylistItem>,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlaylistDetail: (PlaylistItem) -> Unit,
    onOpenMyPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onOpenImport: () -> Unit,
) {
    // 歌单页面主 Tab：0 = 收藏的歌单, 1 = 自建歌单
    var playlistTab by remember { mutableIntStateOf(0) }

    // 新建与重命名弹窗状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<MyPlaylist?>(null) }
    var renameText by remember { mutableStateOf("") }
    // 删除歌单二次确认（误触 32dp 小热区即永久丢失歌单，必须确认）
    var deleteTarget by remember { mutableStateOf<MyPlaylist?>(null) }

    // 收藏歌单按两列分行（remember 提前算好：LazyColumn 内容 lambda 里不能调 @Composable）
    val collectedRows = remember(collectedPlaylists) { collectedPlaylists.chunked(2) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = 24.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = LocalBottomBarPadding.current // 避开底栏悬浮 Dock
            )
        ) {
            // 1. 顶栏标题与操作按钮
            item(key = "playlist_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "歌单",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${collectedPlaylists.size} 个热门歌单 · ${myPlaylists.size} 个自建",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 搜索按钮：跳转全局搜索页
                        Row(
                            modifier = Modifier
                                .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .clickable(onClick = onOpenSearch)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索", tint = TextSoft, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("搜索", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = TextSoft)
                        }

                        // 新建歌单按钮 (Level 2 Secondary Action)
                        Row(
                            modifier = Modifier
                                .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .clickable { showCreateDialog = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "新建", tint = PrimaryBrown, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("新建", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = PrimaryBrown, fontWeight = FontWeight.SemiBold)
                        }

                        // 导入歌单按钮 (Level 2 Secondary Action)
                        Row(
                            modifier = Modifier
                                .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .clickable(onClick = onOpenImport)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "导入", tint = TextSoft, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("导入", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = TextSoft)
                        }
                    }
                }
            }

            // 2. Tab 切换：收藏歌单 / 自建歌单
            item(key = "playlist_sub_tabs") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlaylistTabItem(
                            title = "热门歌单",
                            count = collectedPlaylists.size,
                            selected = playlistTab == 0,
                            onClick = { playlistTab = 0 }
                        )
                        PlaylistTabItem(
                            title = "自建歌单",
                            count = myPlaylists.size,
                            selected = playlistTab == 1,
                            onClick = { playlistTab = 1 }
                        )
                    }
                }

                // 5. 歌单内容列表
                if (playlistTab == 0) {
                    // 收藏的歌单列表
                    if (collectedPlaylists.isEmpty()) {
                        item(key = "collected_empty") {
                            EmptyBox("暂无热门歌单，稍后再来看看")
                        }
                    } else {
                        // 行号作 key（必然唯一）：null-id 歌单兜底 "" 会在两行都为空 id 时碰撞崩溃
                        // （collectedRows 已在函数体 remember 计算好，此处不能调用 @Composable）
                        items(collectedRows.size, key = { "collected_row_$it" }) { rowIdx ->
                            val pair = collectedRows[rowIdx]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                pair.forEach { item ->
                                    CollectedPlaylistItemCard(
                                        modifier = Modifier.weight(1f),
                                        playlist = item,
                                        onClick = { onOpenPlaylistDetail(item) }
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    // 自建歌单列表
                    if (myPlaylists.isEmpty()) {
                        item(key = "my_playlists_empty") {
                            EmptyBox("暂无自建歌单，点击右上角「新建」创建")
                        }
                    } else {
                        items(myPlaylists, key = { it.id }) { playlist ->
                            MyPlaylistRow(
                                playlist = playlist,
                                onClick = { onOpenMyPlaylist(playlist.id) },
                                onRename = {
                                    renameTarget = playlist
                                    renameText = playlist.name
                                },
                                onDelete = { deleteTarget = playlist }
                            )
                        }
                    }
                }
            }
        }

    // 删除歌单确认对话框
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除歌单") },
            text = {
                // 已同步云端的歌单：删除会连同云端一并删除，必须明示
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
private fun PlaylistTabItem(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (selected) TextPrimary else TextSoft
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = if (selected) PrimaryBrown else TextFaint
            )
        }
        Spacer(Modifier.height(4.dp))
        if (selected) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(PrimaryBrown)
            )
        } else {
            Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun CollectedPlaylistItemCard(
    modifier: Modifier = Modifier,
    playlist: PlaylistItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Stone200.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            SongCover(
                url = playlist.coverUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                corner = 10.dp
            )
            if (playlist.playCount != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = formatPlayCount(playlist.playCount),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(1.dp))

        Text(
            text = "${playlist.trackCount ?: 0} 首歌曲",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp
            ),
            color = TextSoft
        )
    }
}

@Composable
private fun MyPlaylistRow(
    playlist: MyPlaylist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongCover(
            url = playlist.coverUrl ?: playlist.songs.firstOrNull()?.artwork,
            modifier = Modifier.size(50.dp),
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
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp
                ),
                color = TextSoft
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRename),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "重命名", tint = TextMuted, modifier = Modifier.size(15.dp))
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = TextFaint, modifier = Modifier.size(15.dp))
            }
        }
    }
}


// 与发现页共用同一实现（含显式 Locale），见 Components.formatPlayCountText
private fun formatPlayCount(count: Long): String = formatPlayCountText(count)