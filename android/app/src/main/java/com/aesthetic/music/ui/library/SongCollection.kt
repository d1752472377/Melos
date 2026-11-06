package com.aesthetic.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.BottomContentPadding
import com.aesthetic.music.ui.components.QuadCover
import com.aesthetic.music.ui.components.SelectionBottomSheet
import com.aesthetic.music.ui.components.SelectionItem
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.WaveBars
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AppRadius
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

/**
 * 收藏 / 历史两页共用的“歌曲集合”骨架。
 *
 * 只抽两页完全相同的部分：顶栏、汇总卡、统计列、歌曲行、列表容器、排序抽屉。
 * 页面标题、排序选项、空态文案、清空按钮仍留在各自页面，避免变成万能页面。
 */

@Composable
fun SongCollectionTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.page),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun SongCollectionHeaderCard(
    title: String,
    covers: List<String>,
    sortTitle: String,
    onSortClick: () -> Unit,
    onSearchClick: () -> Unit,
    songCount: Int,
    artistCount: Int,
    albumCount: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = Palette.isDark

    Column(
        modifier = modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(AppRadius.hero), elevation = 4.dp)
            .padding(AppSpacing.xxxl)
    ) {
        // 第一行：左侧 2x2 拼图 + 标题/排序药丸 + 右侧搜索按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuadCover(
                covers = covers,
                size = 76.dp,
                corner = 16.dp,
            )

            Spacer(Modifier.width(AppSpacing.xl))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(AppSpacing.sm))

                // 排序下拉药丸
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.md))
                        .background(SurfaceSoft)
                        .clickable(onClick = onSortClick)
                        .padding(horizontal = AppSpacing.md, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(AppSpacing.xxs))
                    Text(
                        text = sortTitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = TextPrimary,
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // 右上角搜索小按钮
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SurfaceSoft)
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.xxl))

        // 第二行：内嵌 3 列统计卡片
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadius.lg))
                .background(if (isDark) SurfaceSoft.copy(alpha = 0.5f) else Surface)
                .height(IntrinsicSize.Min)
                .padding(vertical = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongCollectionStatColumn(
                label = "歌曲",
                value = "$songCount",
                modifier = Modifier.weight(1f),
            )

            SongCollectionStatDivider()

            SongCollectionStatColumn(
                label = "歌手",
                value = "$artistCount",
                modifier = Modifier.weight(1f),
            )

            SongCollectionStatDivider()

            SongCollectionStatColumn(
                label = "专辑",
                value = "$albumCount",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(AppSpacing.xxl))

        // 第三行：双胶囊操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左：播放全部（深色背景实心胶囊）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(AppRadius.lg))
                    .background(AppColors.coverPlateDark)
                    .clickable(onClick = onPlayAll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(AppSpacing.xs))
                Text(
                    text = "播放全部",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                )
            }

            // 右：随机播放（浅白底胶囊）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(AppRadius.lg))
                    .background(if (isDark) SurfaceSoft else Surface)
                    .clickable(onClick = onShuffle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(AppSpacing.xs))
                Text(
                    text = "随机播放",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun SongCollectionStatDivider() {
    Box(
        modifier = Modifier
            .width(0.8.dp)
            .fillMaxHeight()
            .padding(vertical = AppSpacing.xxs)
            .background(Stone200.copy(alpha = 0.45f)),
    )
}

@Composable
private fun SongCollectionStatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(TextSoft),
            )
            Spacer(Modifier.width(AppSpacing.xxs))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = TextSoft,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = TextPrimary,
        )
    }
}

@Composable
fun SongCollectionList(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlaySong: (Song, List<Song>) -> Unit,
    onMoreSong: (Song) -> Unit,
    modifier: Modifier = Modifier,
    listKey: (Int, Song) -> Any = { _, song -> "${song.platform}:${song.id}" },
    contentType: (Int, Song) -> String = { _, _ -> "song" },
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(AppRadius.card))
                .background(Surface),
            contentPadding = BottomContentPadding,
        ) {
            itemsIndexed(
                songs,
                key = listKey,
                contentType = contentType,
            ) { index, song ->
                val isCurrent = currentSong != null &&
                        song.id == currentSong.id && song.platform == currentSong.platform

                SongCollectionRow(
                    song = song,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying && isCurrent,
                    onClick = { onPlaySong(song, songs) },
                    onMore = { onMoreSong(song) },
                )

                if (index < songs.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp, end = AppSpacing.xxl),
                        thickness = 0.6.dp,
                        color = Stone200.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SongCollectionRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 封面 + 播放状态波形指示
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            SongCover(song.artwork, Modifier.fillMaxSize(), corner = 10.dp)

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(AppRadius.sm))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        WaveBars(color = Color.White, barWidth = 2.5.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "已暂停",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(AppSpacing.lg))

        // 歌曲信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isCurrent) AccentCoral else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(3.dp))

            val subtitleText = when {
                isCurrent && !isPlaying -> "已暂停 · ${song.artist.ifBlank { "未知歌手" }}"
                isCurrent && isPlaying -> "播放中 · ${song.artist.ifBlank { "未知歌手" }}"
                else -> song.subtitleText
            }

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = if (isCurrent) AccentCoral.copy(alpha = 0.85f) else TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 右侧三点更多按钮
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "更多操作",
                tint = TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun <T> SongCollectionSortSheet(
    title: String,
    currentTitle: String,
    items: List<SelectionItem<T>>,
    selectedId: T,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
    SelectionBottomSheet(
        headerIcon = Icons.AutoMirrored.Filled.Sort,
        title = title,
        subtitle = "当前：$currentTitle",
        items = items,
        selectedId = selectedId,
        onSelect = onSelect,
        onDismissRequest = onDismissRequest,
    )
}
