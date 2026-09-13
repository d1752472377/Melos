package com.aesthetic.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Rose
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 单曲操作相关公共组件。
 *
 * 这两块原先分别是 PlaylistDetailScreen 的私有实现（SongActionBottomSheet / AddToPlaylistBottomSheet）。
 * 搜索页也需要同一套「三个点 → 底部抽屉」交互，且歌单详情与专辑详情已在复用，共三处调用点，
 * 因此抽到公共层，避免第三份拷贝继续发散。
 */

/** 单曲操作抽屉：顶部歌曲信息 + 一组动作 */
@Composable
fun SongActionSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    /** 可选：打开该歌曲的播放卡片（历史等列表页用） */
    onViewCard: (() -> Unit)? = null,
    /** 可选：把该歌曲从当前列表移除（历史页用） */
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "移除",
) {
    val dismissAfter: (() -> Unit) -> () -> Unit = { action -> { action(); onDismiss() } }

    AppBottomSheet(onDismissRequest = onDismiss) {
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

            onViewCard?.let { viewCard ->
                SongActionMenuItem(
                    icon = Icons.Filled.Album,
                    title = "查看卡片",
                    onClick = dismissAfter(viewCard)
                )
            }

            SongActionMenuItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "下一首播放",
                onClick = dismissAfter(onPlayNext)
            )

            SongActionMenuItem(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                iconTint = if (isFavorite) Rose else TextPrimary,
                title = if (isFavorite) "取消收藏" else "收藏歌曲",
                onClick = dismissAfter(onToggleFavorite)
            )

            SongActionMenuItem(
                icon = Icons.Filled.Download,
                title = "下载歌曲",
                onClick = dismissAfter(onDownload)
            )

            SongActionMenuItem(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "加入播放队列",
                onClick = dismissAfter(onAddToQueue)
            )

            SongActionMenuItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "添加到歌单",
                onClick = onAddToPlaylist
            )

            onRemove?.let { remove ->
                SongActionMenuItem(
                    icon = Icons.Filled.Delete,
                    iconTint = Rose,
                    title = removeLabel,
                    onClick = dismissAfter(remove)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 抽屉里的单行动作项 */
@Composable
fun SongActionMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    iconTint: Color = TextPrimary,
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

/** 把歌曲收藏到自建歌单的抽屉，内含「新建歌单」二级弹窗 */
@Composable
fun AddToPlaylistSheet(
    song: Song,
    myPlaylists: List<MyPlaylist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onCreateAndAdd: (String) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AppBottomSheet(onDismissRequest = onDismiss) {
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
        AppDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = "新建歌单",
            confirmText = "创建并添加",
            onConfirm = {
                val name = newPlaylistName.trim()
                if (name.isNotEmpty()) {
                    onCreateAndAdd(name)
                    showCreateDialog = false
                    newPlaylistName = ""
                }
            },
            confirmEnabled = newPlaylistName.trim().isNotEmpty(),
            cancelText = "取消",
            onCancel = {
                showCreateDialog = false
                newPlaylistName = ""
            },
        ) {
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
        }
    }
}
