package com.aesthetic.music.ui.category

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 分类页（覆盖层）：默认展示分类列表，点击分类进入该分类的歌单列表。
 * 歌单列表复用方块卡片，点击歌单跳转歌单详情。
 */
@Composable
fun CategoryPage(
    categories: List<HomeCategoryDto>,
    selectedName: String?,
    playlists: List<PlaylistItem>,
    isLoadingPlaylists: Boolean,
    error: String?,
    onBack: () -> Unit,
    onOpenCategory: (HomeCategoryDto) -> Unit,
    onBackToList: () -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onRetryCategory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = if (selectedName != null) onBackToList else onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = selectedName ?: "分类歌单",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
            )
        }

        if (selectedName == null) {
            // 分类列表
            if (categories.isEmpty()) {
                EmptyBox(message = "暂无分类", modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalBottomBarPadding.current + 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(categories, key = { it.id }) { category ->
                        CategoryCard(category = category, onClick = { onOpenCategory(category) })
                    }
                }
            }
        } else {
            // 分类歌单列表
            when {
                isLoadingPlaylists && playlists.isEmpty() -> {
                    LoadingBox(message = "正在加载歌单…", modifier = Modifier.weight(1f).fillMaxWidth())
                }
                error != null && playlists.isEmpty() -> {
                    ErrorBox(message = error, onRetry = onRetryCategory, modifier = Modifier.weight(1f).fillMaxWidth())
                }
                playlists.isEmpty() -> {
                    EmptyBox(message = "该分类下暂无歌单", modifier = Modifier.weight(1f).fillMaxWidth())
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = LocalBottomBarPadding.current + 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(playlists, key = { "${it.platform}:${it.id}" }) { playlist ->
                            CategoryPlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist) })
                        }
                    }
                }
            }
        }
    }
}

/** 分类卡片：封面 + 标题 + 金句 */
@Composable
private fun CategoryCard(category: HomeCategoryDto, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        val cover = category.coverUrl ?: category.playlist?.coverUrl
        SongCover(
            url = cover,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f),
            corner = 14.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = category.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (category.quote.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = category.quote,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 分类歌单卡片（方块 + 名称 + 播放量） */
@Composable
private fun CategoryPlaylistCard(playlist: PlaylistItem, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        SongCover(
            url = playlist.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            corner = 14.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val played = formatPlayCountText(playlist.playCount)
        if (played.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = played,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}