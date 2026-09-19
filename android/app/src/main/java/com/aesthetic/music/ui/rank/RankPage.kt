package com.aesthetic.music.ui.rank

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
import com.aesthetic.music.data.api.platform.RankCatalog
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 排行榜页（全玻璃拟态，对齐歌单广场顶栏与卡片规范）：
 * 1. 顶栏规范：返回键 + 24sp 大标题 + 右侧旋转刷新按钮；
 * 2. 平台胶囊栏：横向滚动 LazyRow，与歌单广场标签胶囊风格统一；
 * 3. 榜单列表：单列垂直卡片流，每条应用 iosGlassCard 玻璃拟态。
 */
@Composable
fun RankPage(
    platform: String,
    ranks: List<PlaylistItem>,
    isLoading: Boolean,
    error: String?,
    onSelectPlatform: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenRank: (PlaylistItem) -> Unit,
) {
    val current = RankCatalog.normalize(platform)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // ===== 顶栏（对齐歌单广场规范：返回键 + 大标题 + 刷新按钮） =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = statusBarTop + 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(4.dp)
                    .size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "排行榜",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            // 右侧刷新按钮
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "刷新",
                tint = if (isLoading) TextSoft else TextPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = !isLoading, onClick = onRetry)
                    .padding(6.dp)
                    .size(22.dp),
            )
        }

        // ===== 平台胶囊栏（横向滚动，对齐歌单广场 CategoryPill 规范） =====
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(RankCatalog.platforms, key = { it.id }) { item ->
                PlatformPill(
                    label = item.label,
                    selected = item.id == current,
                    onClick = { onSelectPlatform(item.id) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ===== 内容呈现（支持分状态展示） =====
        when {
            isLoading && ranks.isEmpty() -> LoadingBox(
                message = "正在获取${RankCatalog.label(current)}榜单…",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            ranks.isEmpty() && error != null -> ErrorBox(
                message = error,
                onRetry = onRetry,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            ranks.isEmpty() -> EmptyBox(
                message = "暂无排行榜",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            else -> RankList(
                ranks = ranks,
                refreshing = isLoading,
                onOpenRank = onOpenRank,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 平台选择胶囊（与歌单广场 CategoryPill 统一设计语言）
 */
@Composable
private fun PlatformPill(label: String, selected: Boolean, onClick: () -> Unit) {
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

@Composable
private fun RankList(
    ranks: List<PlaylistItem>,
    refreshing: Boolean,
    onOpenRank: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = ranks.groupBy { it.group }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 4.dp,
            bottom = BottomContentSpacer,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        grouped.forEach { (group, list) ->
            if (group != null && grouped.size > 1) {
                item(key = "group:$group") { GroupHeader(title = group, count = list.size) }
            }
            items(list, key = { "${it.platform}:${it.id}" }) { rank ->
                RankGlassCard(rank = rank, onClick = { onOpenRank(rank) })
            }
        }
        if (refreshing) {
            item(key = "refreshing") {
                Text(
                    text = "正在刷新…",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = TextPrimary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count 个",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextSoft,
        )
    }
}

/**
 * 单张排行榜玻璃拟态卡片（iOS 风格）
 */
@Composable
private fun RankGlassCard(rank: PlaylistItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(18.dp), elevation = 3.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongCover(url = rank.coverUrl, modifier = Modifier.size(64.dp), corner = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = rank.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val kindLabel = rankKindLabel(rank.kind ?: RankCatalog.kindOf(rank.name))
                    if (kindLabel.isNotEmpty()) {
                        KindBadge(text = kindLabel)
                        Spacer(Modifier.width(6.dp))
                    }
                    val meta = rankMetaText(rank)
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun KindBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PrimaryBrown.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = PrimaryBrown,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun rankMetaText(rank: PlaylistItem): String {
    val count = rank.trackCount?.takeIf { it > 0 }?.let { "$it 首" }
    val played = formatPlayCountText(rank.playCount?.takeIf { it > 0 }).takeIf { it.isNotEmpty() }
    return listOfNotNull(count, played).joinToString(" · ")
}

internal fun rankKindLabel(kind: String?): String = when (kind) {
    "hot" -> "热歌"
    "new" -> "新歌"
    "rising" -> "飙升"
    "original" -> "原创"
    else -> ""
}
