package com.aesthetic.music.ui.cache

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 缓存列表页：列出已缓存歌曲（名字/大小），支持单个删除与一键清空。
 */
@Composable
fun CacheListScreen(
    songs: List<AudioCacheService.CachedSong>,
    onBack: () -> Unit,
    onRemoveOne: (String) -> Unit,
    onClearAll: () -> Unit,
) {
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
                .padding(bottom = 150.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(Surface, CircleShape)
                        .clickable(onClick = onBack)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextMuted,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "已缓存歌曲 (${songs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (songs.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("全部清除", color = Color(0xFFDC2626))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (songs.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = TextSoft,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("暂无缓存歌曲", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                        Text("播放过的歌曲会自动缓存，下次秒开", color = TextSoft, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn {
                    items(songs) { song ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Surface)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .background(com.aesthetic.music.ui.theme.TagBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = com.aesthetic.music.ui.theme.TagText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1)
                                Text(
                                    "${song.artist} · ${AudioCacheService.formatSize(song.size)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSoft,
                                    maxLines = 1,
                                )
                            }
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = TextSoft,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onRemoveOne(song.filePath) },
                            )
                        }
                    }
                }
            }
        }
    }
}
