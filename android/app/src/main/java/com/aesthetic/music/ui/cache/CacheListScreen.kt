package com.aesthetic.music.ui.cache

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.ui.components.DangerCapsuleButton
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.BottomContentPadding
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextFaint
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

@Composable
fun CacheListScreen(
    songs: List<AudioCacheService.CachedSong>,
    onRemoveOne: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    val uniqueSongs = remember(songs) { songs.distinctBy { it.filePath } }
    val totalSizeText = AudioCacheService.formatSize(uniqueSongs.sumOf { it.size })
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.page)
                .padding(top = AppSpacing.section)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "本地缓存",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (songs.isNotEmpty()) {
                    DangerCapsuleButton(text = "清空全部", onClick = { showClearConfirm = true })
                }
            }
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                text = "已缓存 ${songs.size} 首歌曲",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextSoft
            )

            Spacer(Modifier.height(AppSpacing.xxl))

            if (songs.isEmpty()) {
                EmptyBox("暂无缓存歌曲\n播放过的歌曲会自动缓存，下次秒开")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    contentPadding = BottomContentPadding
                ) {
                    items(uniqueSongs, key = { s -> s.filePath }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .padding(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (Palette.isDark) AppColors.successSurfaceDark else AppColors.successSurfaceLight),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = AccentMint,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(AppSpacing.lg))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
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
                                    text = "${song.artist} · ${AudioCacheService.formatSize(song.size)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable { onRemoveOne(song.filePath) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = TextFaint,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                     }
                 }
             }
         }
     }

    if (showClearConfirm) {
        AppConfirmDialog(
            onDismissRequest = { showClearConfirm = false },
            title = "清空全部缓存",
            message = "将删除已缓存的 ${songs.size} 首歌曲（共 $totalSizeText）。\n下次播放这些歌曲时需要重新联网加载。",
            confirmText = "清空",
            onConfirm = {
                showClearConfirm = false
                onClearAll()
            },
        )
    }
}
