package com.aesthetic.music.ui.rank

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.platform.RankCatalog
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.SecondaryPageTopBar
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.components.glassBorder
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import java.util.concurrent.TimeUnit
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

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
    loadedAt: Long = 0L,
    now: Long = System.currentTimeMillis(),
) {
    val current = RankCatalog.normalize(platform)
    val spotlight = rankSpotlight(ranks)

    Box(modifier = Modifier.fillMaxSize()) {
        HomeAmbientGlowBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            SecondaryPageTopBar(
                title = "排行榜",
                onBack = onBack,
                trailing = {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "刷新",
                        tint = if (isLoading) TextSoft else TextPrimary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = !isLoading, onClick = onRetry)
                            .padding(AppSpacing.xs)
                            .size(22.dp),
                    )
                },
            )

            PlatformTabCard(
                current = current,
                onSelectPlatform = onSelectPlatform,
                modifier = Modifier.padding(horizontal = AppSpacing.page),
            )

            Spacer(Modifier.height(AppSpacing.xl))

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

                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 2.dp,
                        bottom = BottomContentSpacer,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                ) {
                    if (spotlight != null) {
                        item(key = "spotlight:${spotlight.platform}:${spotlight.id}") {
                            SpotlightCard(
                                rank = spotlight,
                                loadedAt = loadedAt,
                                now = now,
                                onClick = { onOpenRank(spotlight) },
                            )
                        }
                    }

                    val rest = ranks.filterNot {
                        spotlight != null && it.id == spotlight.id && it.platform == spotlight.platform
                    }
                    item(key = "section_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = AppSpacing.sm, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "发现更多榜单",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.4).sp,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${ranks.size} 个榜单",
                                fontSize = 13.sp,
                                color = TextSoft,
                            )
                        }
                    }
                    items(rest, key = { "${it.platform}:${it.id}" }) { rank ->
                        RankChartCard(
                            rank = rank,
                            loadedAt = loadedAt,
                            now = now,
                            onClick = { onOpenRank(rank) },
                        )
                    }
                    if (rest.isEmpty()) {
                        item(key = "only_one") {
                            Text(
                                text = "这个平台只有一个榜单",
                                fontSize = 13.sp,
                                color = TextSoft,
                                modifier = Modifier.padding(vertical = AppSpacing.lg),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformTabCard(
    current: String,
    onSelectPlatform: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = Palette.isDark
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(18.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RankCatalog.platforms.forEach { item ->
            val selected = item.id == current
            val brand = platformBrandColor(item.id)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) (if (dark) Color.White.copy(alpha = 0.14f) else Color.White) else Color.Transparent)
                    .clickable { onSelectPlatform(item.id) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(brand.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = brand,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Spacer(Modifier.width(AppSpacing.xs))
                Text(
                    text = item.label.replace("音乐", ""),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) TextPrimary else TextSoft,
                    maxLines = 1,
                )
            }
        }
    }
}

internal fun platformBrandColor(platformId: String): Color = when (platformId) {
    RankCatalog.NETEASE -> AppColors.brandNeteaseRank
    RankCatalog.QQ -> AppColors.brandQQ
    RankCatalog.KUGOU -> AppColors.brandKugouRank
    RankCatalog.KUWO -> AppColors.brandKuwoRank
    else -> AppColors.slate400
}

internal fun rankSpotlight(ranks: List<PlaylistItem>): PlaylistItem? =
    ranks.firstOrNull { (it.kind ?: RankCatalog.kindOf(it.name)) == "rising" }
        ?: ranks.firstOrNull()

@Composable
private fun SpotlightCard(
    rank: PlaylistItem,
    loadedAt: Long,
    now: Long,
    onClick: () -> Unit,
) {
    val dark = Palette.isDark
    val (accent, accentSoft) = rankAccentColors(rank.kind ?: RankCatalog.kindOf(rank.name))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = if (dark) 0.20f else 0.14f),
                        accentSoft.copy(alpha = if (dark) 0.06f else 0.04f),
                    )
                ),
                shape = RoundedCornerShape(24.dp),
            )
            .clickable(onClick = onClick)
            .padding(AppSpacing.page),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "此刻焦点",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "CHART SPOTLIGHT",
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Medium,
                color = TextSoft,
            )
        }

        Spacer(Modifier.height(AppSpacing.xxl))

        Row(verticalAlignment = Alignment.CenterVertically) {
            RankCover(
                rank = rank,
                accent = accent,
                accentSoft = accentSoft,
                size = 100.dp,
                corner = 20.dp,
                showName = true,
            )
            Spacer(Modifier.width(AppSpacing.xxxl))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rank.name,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = rankUpdatedLabel(loadedAt, now),
                    fontSize = 13.sp,
                    color = TextSoft,
                )
                Spacer(Modifier.height(AppSpacing.xl))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "探索榜单",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(AppSpacing.xxxl))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(accent.copy(alpha = 0.16f)),
        )
        Spacer(Modifier.height(AppSpacing.xl))
        Text(
            text = rankDescription(rank),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = TextSoft,
        )
    }
}

@Composable
private fun RankChartCard(
    rank: PlaylistItem,
    loadedAt: Long,
    now: Long,
    onClick: () -> Unit,
) {
    val dark = Palette.isDark
    val (accent, accentSoft) = rankAccentColors(rank.kind ?: RankCatalog.kindOf(rank.name))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(22.dp))
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = if (dark) 0.16f else 0.11f),
                        Color.Transparent,
                    )
                ),
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .padding(AppSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankCover(
            rank = rank,
            accent = accent,
            accentSoft = accentSoft,
            size = 84.dp,
            corner = 18.dp,
            showName = true,
        )
        Spacer(Modifier.width(AppSpacing.xl))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rank.name,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSoft,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                text = rankDescription(rank),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = TextSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(AppSpacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UpdatedChip(text = rankUpdatedLabel(loadedAt, now), accent = accent)
                val count = rank.trackCount?.takeIf { it > 0 }
                if (count != null) {
                    Spacer(Modifier.width(AppSpacing.md))
                    Text(
                        text = "$count 首",
                        fontSize = 12.sp,
                        color = TextSoft,
                    )
                }
                val played = formatPlayCountText(rank.playCount?.takeIf { it > 0 })
                if (played.isNotEmpty()) {
                    Spacer(Modifier.width(AppSpacing.md))
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = TextSoft,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = played,
                        fontSize = 12.sp,
                        color = TextSoft,
                    )
                }
            }
        }
    }
}

@Composable
private fun RankCover(
    rank: PlaylistItem,
    accent: Color,
    accentSoft: Color,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    showName: Boolean,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(elevation = 6.dp, shape = shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accentSoft, accent))),
        contentAlignment = Alignment.Center,
    ) {
        if (!rank.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(rank.coverUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = rank.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (showName) {
            Text(
                text = rank.name.removeSuffix("榜"),
                fontSize = (size.value * 0.19f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = AppSpacing.xs),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
        )
    }
}

@Composable
private fun UpdatedChip(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = accent,
        )
    }
}

internal fun rankAccentColors(kind: String?): Pair<Color, Color> = when (kind) {
    "rising" -> AppColors.rankRisingStart to AppColors.rankRisingEnd
    "new" -> AppColors.rankNewStart to AppColors.rankNewEnd
    "original" -> AppColors.rankOriginalStart to AppColors.skySoft
    "hot" -> AppColors.rankHotStart to AppColors.rankHotEnd
    else -> AppColors.slate500 to AppColors.slate300
}

internal fun rankDescription(rank: PlaylistItem): String {
    val fromApi = rank.description?.trim()
    if (!fromApi.isNullOrEmpty()) return fromApi
    return when (rank.kind ?: RankCatalog.kindOf(rank.name)) {
        "rising" -> "近期热度上升最快的单曲，按涨幅排序，每天更新。"
        "new" -> "最近发行的新歌，按收听热度排序，每周更新。"
        "original" -> "独立原创音乐人作品榜，每周更新。"
        "hot" -> "当下最热的单曲，按播放量排序，每天更新。"
        else -> "平台精选榜单，定期更新。"
    }
}

internal fun rankUpdatedLabel(loadedAt: Long, now: Long): String {
    if (loadedAt <= 0L) return "刚刚更新"
    val delta = (now - loadedAt).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 60 -> "刚刚更新"
        hours < 24 -> "${hours} 小时前更新"
        days < 30 -> "${days} 天前更新"
        else -> "较久未更新"
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
