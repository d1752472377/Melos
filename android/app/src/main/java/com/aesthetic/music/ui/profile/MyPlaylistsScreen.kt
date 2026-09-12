package com.aesthetic.music.ui.profile

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistLinkParser
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.ui.components.AppDialog
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.PlaylistEditorResult
import com.aesthetic.music.ui.components.PlaylistEditorSheet
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.library.PlaylistResolveState
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 我的歌单页：自建歌单管理（创建 / 打开 / 编辑名称与封面 / 删除 / 把当前歌加入歌单）。
 */
@Composable
fun MyPlaylistsScreen(
    playlists: List<MyPlaylist>,
    currentSong: Song?,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onCreate: (PlaylistEditorResult) -> Unit,
    onEdit: (String, PlaylistEditorResult) -> Unit,
    onDelete: (String) -> Unit,
    onAddCurrent: (String, Song) -> Unit,
    importPreview: PlaylistResolveState = PlaylistResolveState.Idle,
    onResolveImport: (String) -> Unit = {},
    onClearImport: () -> Unit = {},
    onToast: (String) -> Unit = {},
) {
    var showCreate by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MyPlaylist?>(null) }
    // 删除歌单二次确认（误触即永久删除，含已同步的云端歌单）
    var deleteTarget by remember { mutableStateOf<MyPlaylist?>(null) }

    // 此处加上了 .background(Background)，防止底层页面不小心透出来
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 28.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = LocalBottomBarPadding.current // 保证下滑到底部时不会被底栏播放器遮挡
            )
        ) {
            // 顶栏 Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "我的歌单",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                        )
                        Text(
                            text = "共 ${playlists.size} 个自建歌单",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft
                        )
                    }
                    Box(
                        modifier = Modifier
                            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = PrimaryBrown.copy(alpha = 0.3f))
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryBrown)
                            .clickable { showCreate = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "新建歌单",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 空状态
            if (playlists.isEmpty()) {
                item {
                    EmptyBox("还没有自建歌单\n点击右上角「新建歌单」开始整理你的音乐")
                }
            } else {
                items(playlists, key = { it.id }) { p ->
                    PlaylistRowItem(
                        playlist = p,
                        currentSong = currentSong,
                        onOpen = { onOpenPlaylist(p.id) },
                        onAddCurrent = { onAddCurrent(p.id, it) },
                        onRename = { editTarget = p },
                        onDelete = { deleteTarget = p }
                    )
                }
            }
        }
    }

    if (showCreate) {
        PlaylistEditorSheet(
            title = "新建歌单",
            confirmText = "创建歌单",
            showPlatformImport = true,
            importState = importPreview,
            onResolveImport = onResolveImport,
            onClearImport = onClearImport,
            onDismiss = {
                showCreate = false
                onClearImport()
            },
            onConfirm = {
                showCreate = false
                onClearImport()
                onCreate(it)
            },
            onToast = onToast,
        )
    }

    // 删除歌单确认对话框
    deleteTarget?.let { target ->
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = "删除歌单",
            confirmText = "删除",
            onConfirm = {
                onDelete(target.id)
                deleteTarget = null
            },
            destructive = true,
        ) {
            val syncNote = if (target.cloudId.isNotBlank())
                "\n该歌单已同步云端，删除后云端歌单也会一并删除。"
            else ""
            Text(
                "确认删除《${target.name}》？\n歌单内 ${target.songs.size} 首歌曲记录将一并移除。$syncNote\n此操作不可恢复。",
            )
        }
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
                onEdit(id, result)
            },
            onToast = onToast,
        )
    }
}

@Composable
private fun PlaylistRowItem(
    playlist: MyPlaylist,
    currentSong: Song?,
    onOpen: () -> Unit,
    onAddCurrent: (Song) -> Unit,
    onRename: () -> Unit,
    onDelete: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onOpen)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(playlist.coverUrl, Modifier.size(46.dp), corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Text(
                // 导入的歌单标出来源平台，方便和本地自建歌单区分
                text = buildString {
                    append("${playlist.songs.size} 首")
                    if (playlist.isImported) {
                        append(" · 来自")
                        append(PlaylistLinkParser.platformName(playlist.sourcePlatform))
                    }
                    if (playlist.cloudId.isNotBlank()) append(" · 已同步")
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
            )
        }

        if (currentSong != null) {
            Box(
                modifier = Modifier
                    .border(1.dp, Stone200, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface)
                    .clickable { onAddCurrent(currentSong) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = PrimaryBrown, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("加入当前", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = PrimaryBrown, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        // 编辑按钮
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onRename),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "编辑名称与封面",
                tint = TextSoft,
                modifier = Modifier.size(15.dp),
            )
        }

        // 删除按钮
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { onDelete(playlist.id) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除",
                tint = TextSoft,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}