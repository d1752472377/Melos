package com.aesthetic.music.ui.rank

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 排行榜页：顶部平台 tag（网易云 / QQ / 酷狗 / 酷我）+ 该平台榜单列表，点榜单进曲目详情。
 *
 * 重写要点：
 * 1. 以前只有网易云一个数据源，现在顶部 tag 直接切平台，每个平台保留自己的榜单结构；
 * 2. QQ 的「巅峰榜/地区榜/…」与酷我的「官方/特色/…」是分组的，按 [PlaylistItem.group] 分组显示，
 *    没有分组的平台（网易云/酷狗）自动退化为平铺列表，不需要两套布局代码；
 * 3. 加载中 / 失败（可重试）/ 空结果三态齐全：以前失败只会看到一行"暂无排行榜"，
 *    用户会以为该平台真的没有榜单。
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
                    .clickable(onClick = onBack),
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
                text = "排行榜",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            // 手动刷新：榜单是"天级"数据，平时不需要刷，但接口偶发返回空时需要能自救
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = !isLoading, onClick = onRetry),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    tint = if (isLoading) TextSoft else TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // 平台 tag：横向可滚动，四个平台在窄屏也不会被压变形
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RankCatalog.platforms.forEach { item ->
                PlatformTag(
                    label = item.label,
                    selected = item.id == current,
                    onClick = { onSelectPlatform(item.id) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            // 有旧数据时切平台：列表还在，顶部 tag 立刻响应，避免整页闪成 loading
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

@Composable
private fun RankList(
    ranks: List<PlaylistItem>,
    refreshing: Boolean,
    onOpenRank: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // groupBy 用 LinkedHashMap：分组标题保持接口给的原始顺序（巅峰榜 → 地区榜 → …）
    val grouped = ranks.groupBy { it.group }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 6.dp,
            bottom = LocalBottomBarPadding.current + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        grouped.forEach { (group, list) ->
            if (group != null && grouped.size > 1) {
                item(key = "group:$group") { GroupHeader(title = group, count = list.size) }
            }
            items(list, key = { "${it.platform}:${it.id}" }) { rank ->
                RankRow(rank = rank, onClick = { onOpenRank(rank) })
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
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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

@Composable
private fun PlatformTag(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) PrimaryBrown else Surface)
            .border(1.dp, if (selected) PrimaryBrown else Stone200, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) Color.White else TextPrimary,
        )
    }
}

@Composable
private fun RankRow(rank: PlaylistItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Stone100.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(url = rank.coverUrl, modifier = Modifier.size(64.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = rank.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(4.dp))
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
    }
}

@Composable
private fun KindBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PrimaryBrown.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = PrimaryBrown,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 榜单次要信息：曲目数 + 播放量，两者都拿不到时返回空串（不显示空行）。
 *
 * 0 与负数按"接口没给数据"处理：酷我/酷狗部分榜单的 play_times 会是 0，
 * 直接显示成 "0" 会让人以为这个榜单没人听，不如不显示。
 */
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
