package com.aesthetic.music.ui.newsongs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.music.data.api.platform.NewSongRegions
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.SecondaryPageTopBar
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.AppColors
import com.aesthetic.music.ui.theme.AppSpacing

/**
 * 新歌首发：按地区看最近上架的歌曲，点一首直接播放。
 */
@Composable
fun NewSongsScreen(
    controller: NewSongsController,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
) {
    val region by controller.region.collectAsState()
    val songs by controller.songs.collectAsState()
    val loading by controller.loading.collectAsState()
    val error by controller.error.collectAsState()

    LaunchedEffect(Unit) { controller.ensureLoaded() }

    Box(Modifier.fillMaxSize()) {
        HomeAmbientGlowBackground()

        Column(Modifier.fillMaxSize()) {
            SecondaryPageTopBar(
                title = "新歌首发",
                onBack = onBack,
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { controller.refresh() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            tint = textMedium(),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )

            RegionChips(
                regions = controller.regions,
                selected = region,
                onSelect = controller::select,
            )

            Spacer(Modifier.height(AppSpacing.lg))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.page),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "最近上架",
                    color = textHigh(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (songs.isNotEmpty()) {
                    Text(
                        text = "${songs.size} 首新歌",
                        color = textLow(),
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

            when {
                loading && songs.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = AppColors.skyDeep,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp),
                    )
                }

                songs.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error ?: "暂无新歌",
                            color = textMedium(),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(AppSpacing.lg))
                        Text(
                            text = "重新加载",
                            color = AppColors.skyDeep,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { controller.refresh() }
                                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                        )
                    }
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppSpacing.page,
                        end = AppSpacing.page,
                        bottom = AppSpacing.section,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xl),
                ) {
                    items(songs, key = { "${it.platform}:${it.id}" }) { song ->
                        NewSongTile(song = song, onClick = { onPlaySong(song, songs) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionChips(
    regions: List<NewSongRegions.Region>,
    selected: NewSongRegions.Region,
    onSelect: (NewSongRegions.Region) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = AppSpacing.page),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        items(regions, key = { it.id }) { item ->
            val active = item.id == selected.id
            Text(
                text = item.label,
                color = if (active) textHigh() else textMedium(),
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(
                        if (active) textHigh().copy(alpha = 0.08f)
                        else textHigh().copy(alpha = 0.03f)
                    )
                    .clickable { onSelect(item) }
                    .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.sm),
            )
        }
    }
}

@Composable
private fun NewSongTile(song: Song, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = song.artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(textHigh().copy(alpha = 0.06f)),
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            text = song.title,
            color = textHigh(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = song.artist,
            color = textMedium(),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
