package com.aesthetic.music.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Refresh
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
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 歌单广场（图 2 + 图 3 规范）：
 * - 顶部：返回键 + 歌单广场标题 + 右侧刷新按钮；
 * - 分类：移除原先的侧边抽屉，改为横向滚动的标签胶囊栏（图 2）；
 * - 排序：最热 / 最新 精致胶囊切换；
 * - 列表：单列垂直 iOS 玻璃拟态卡片流（图 3），透出全局底层环境光背景与状态栏毛玻璃折射。
 */
@Composable
fun PlaylistPage(
    sheetTags: List<String>,
    sheetCat: String,
    sheetOrder: String,
    playlists: List<PlaylistItem>,
    isLoading: Boolean,
    error: String?,
    onSelectCat: (String) -> Unit,
    onSelectOrder: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        HomeAmbientGlowBackground()
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // ===== 顶部导航栏（返回键 + 大标题 + 刷新按钮，见图 2） =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = statusBarTop + 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .padding(4.dp)
                        .size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = "歌单广场",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            // 右侧刷新按钮（图 2）
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "刷新歌单",
                tint = TextPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRetry)
                    .padding(6.dp)
                    .size(22.dp)
            )
        }

        // ===== 分类标签胶囊栏（横向滚动，彻底替代抽屉，见图 2） =====
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(sheetTags, key = { it }) { tag ->
                val selected = tag == sheetCat
                CategoryPill(
                    label = tag,
                    selected = selected,
                    onClick = { onSelectCat(tag) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ===== 排序切换（最热 / 最新，精致紧凑胶囊） =====
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OrderChip(label = "最热", selected = sheetOrder == "hot") {
                onSelectOrder("hot")
            }
            OrderChip(label = "最新", selected = sheetOrder == "new") {
                onSelectOrder("new")
            }
        }

        Spacer(Modifier.height(6.dp))

        // ===== 歌单卡片列表（单列 iOS 玻璃拟态卡片流，见图 3） =====
        when {
            isLoading && playlists.isEmpty() -> {
                LoadingBox(message = "正在加载歌单…", modifier = Modifier.weight(1f).fillMaxWidth())
            }
            error != null && playlists.isEmpty() -> {
                ErrorBox(message = error, onRetry = onRetry, modifier = Modifier.weight(1f).fillMaxWidth())
            }
            playlists.isEmpty() -> {
                EmptyBox(message = "该分类下暂无歌单", modifier = Modifier.weight(1f).fillMaxWidth())
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 8.dp,
                        bottom = BottomContentSpacer,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(playlists, key = { "${it.platform}:${it.id}" }) { playlist ->
                        PlaylistGlassCard(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist) },
                        )
                    }
                }
            }
        }
    }
}
}


/**
 * 分类标签胶囊（图 2 样式）：
 * 选中态：微高光浮起卡片质感，字体加粗加黑
 * 未选中态：半透明平滑融入背景
 */
@Composable
private fun CategoryPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isDark = Palette.isDark
    val bgModifier = if (selected) {
        Modifier.iosGlassCard(shape = RoundedCornerShape(14.dp), elevation = 3.dp)
    } else {
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) SurfaceSoft.copy(alpha = 0.5f) else Surface.copy(alpha = 0.65f))
    }

    Box(
        modifier = Modifier
            .then(bgModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) TextPrimary else TextSoft,
        )
    }
}

/** 最热/最新胶囊 */
@Composable
private fun OrderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PrimaryBrown else SurfaceSoft.copy(alpha = 0.75f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else TextSoft,
        )
    }
}

/**
 * 单条 iOS 玻璃拟态歌单卡片（图 3 规范）：
 * - 左侧：大圆角歌单封面；
 * - 右侧上方：歌单标题 + 展开箭头；
 * - 右侧中间：歌单描述/文案，最多两行省略；
 * - 右侧下方：耳机播放量软胶囊 + 歌曲总数。
 */
@Composable
private fun PlaylistGlassCard(
    playlist: PlaylistItem,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 4.dp)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧大圆角封面
            SongCover(
                url = playlist.coverUrl,
                modifier = Modifier
                    .size(76.dp)
                    .aspectRatio(1f),
                corner = 14.dp,
            )

            Spacer(Modifier.width(14.dp))

            // 右侧信息流
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // 第一行：标题 + 右侧箭头
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Spacer(Modifier.height(4.dp))

                // 第二行：描述文案（两行截断）
                val descText = playlist.description?.takeIf { it.isNotBlank() }
                    ?: "${playlist.name} · 精选合辑，畅享好音乐"
                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(8.dp))

                // 第三行：播放量胶囊 + 曲目数
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 耳机播放量胶囊
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceSoft.copy(alpha = 0.85f))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Headphones,
                            contentDescription = null,
                            tint = TextSoft,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = formatPlayCountText(playlist.playCount),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSoft,
                        )
                    }

                    // 歌曲总数
                    val trackCount = playlist.trackCount?.takeIf { it > 0 }
                    if (trackCount != null) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "$trackCount 首",
                            fontSize = 11.sp,
                            color = TextMuted,
                        )
                    }
                }
            }
        }
    }
}