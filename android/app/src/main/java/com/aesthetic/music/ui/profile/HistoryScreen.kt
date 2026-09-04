package com.aesthetic.music.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.BottomBarReserved
import com.aesthetic.music.ui.components.DangerTextButton
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 最近播放页（独立页面）：此前入口直接跳到歌单 Tab，与"我的歌单"混在一起；
 * 现在单独成页，支持单曲移除与一键清空（同步云端）。
 */
@Composable
fun HistoryScreen(
    songs: List<Song>,
    currentSong: Song?,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onRemoveOne: (Song) -> Unit,
    onClearAll: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp)
                .padding(bottom = BottomBarReserved),
        ) {
            // ===== 头部：返回 + 标题 + 播放全部 / 清空 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "最近播放",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        ),
                        color = TextPrimary,
                    )
                    Text(
                        text = "共 ${songs.size} 首",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                    )
                }
                if (songs.isNotEmpty()) {
                    DangerTextButton(text = "清空", onClick = { showClearConfirm = true })
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryBrown)
                            .clickable(onClick = onPlayAll)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "播放全部",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (songs.isEmpty()) {
                EmptyBox("还没有播放记录\n播过的歌会自动出现在这里")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(
                        songs,
                        // 历史列表本身按 platform+id 去重（VM 保证唯一），直接用业务键：
                        // 删除中间项时其后 item 不再因 index 前缀整体变化而重组/动画异常
                        key = { _, s -> s.platform + ":" + s.id },
                        contentType = { _, _ -> "history_song" },
                    ) { index, song ->
                        val playingNow = currentSong != null &&
                            song.id == currentSong.id && song.platform == currentSong.platform
                        HistoryRow(
                            index = index + 1,
                            song = song,
                            playingNow = playingNow,
                            onClick = { onPlaySong(song) },
                            onRemove = { onRemoveOne(song) },
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空最近播放") },
            text = { Text("将删除全部 ${songs.size} 条播放记录（含云端），此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearAll()
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    index: Int,
    song: Song,
    playingNow: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (playingNow) PrimaryBrown.copy(alpha = 0.5f) else Stone200.copy(alpha = 0.6f),
                RoundedCornerShape(10.dp),
            )
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.labelMedium,
            color = if (index <= 3) PrimaryBrown else TextSoft,
            fontWeight = if (index <= 3) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(22.dp),
        )
        SongCover(song.artwork, Modifier.size(44.dp), corner = 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = song.subtitleText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "移除",
                tint = TextMuted,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
