package com.aesthetic.music.ui.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.data.userapi.ScriptMetadata
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 个人中心：本地优先仪表盘 + 私有云同步状态
 */
@Composable
fun ProfileScreen(
    localStats: LocalStats,
    historyCount: Int,
    favoritesCount: Int,
    cacheCount: Int,
    cacheSize: Long,
    isSyncConfigured: Boolean,
    isSyncing: Boolean,
    activeScript: ScriptMetadata?,
    onOpenSettings: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenCacheList: () -> Unit,
    onOpenMyPlaylists: () -> Unit,
    onSyncClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = 24.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = LocalBottomBarPadding.current
            )
        ) {
            // 顶栏
            item(key = "profile_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "个人中心",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "本地优先 · 私有云同步 · 自由音源",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface)
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 用户中心信息卡片
            item(key = "profile_user_card") {
                UserCard(
                    isSyncConfigured = isSyncConfigured,
                    isSyncing = isSyncing,
                    activeScript = activeScript,
                    historyCount = historyCount,
                    favoritesCount = favoritesCount,
                    onSyncClick = onSyncClick,
                    onOpenSettings = onOpenSettings,
                )
            }

            // 听歌统计卡片
            item(key = "profile_stats_card") {
                StatsCard(localStats = localStats)
            }

            // 资料库卡片
            item(key = "profile_library_card") {
                LibraryCard(
                    historyCount = historyCount,
                    favoritesCount = favoritesCount,
                    cacheCount = cacheCount,
                    cacheSize = cacheSize,
                    onOpenRecent = onOpenRecent,
                    onOpenFavorites = onOpenFavorites,
                    onOpenCacheList = onOpenCacheList,
                    onOpenMyPlaylists = onOpenMyPlaylists,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun UserCard(
    isSyncConfigured: Boolean,
    isSyncing: Boolean,
    activeScript: ScriptMetadata?,
    historyCount: Int,
    favoritesCount: Int,
    onSyncClick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val bgGradient = if (Palette.isDark) {
        Brush.linearGradient(listOf(Color(0xFF3730A3), Color(0xFF1E1B4B)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF5352ED), Color(0xFF7052FF)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = PrimaryBrown.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(14.dp))
            .background(bgGradient)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Melos 个人音乐",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (activeScript != null) "音源: [${activeScript.name} v${activeScript.version}]" else "暂未安装音源脚本",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1
                )
                Text(
                    text = "最近播放 $historyCount 首 · 收藏 $favoritesCount 首",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            // 操作按钮
            Box(
                modifier = Modifier
                    .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.1f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable {
                        if (isSyncConfigured) onSyncClick() else onOpenSettings()
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = if (isSyncConfigured) (if (isSyncing) "同步中…" else "⟳ 一键同步") else "☁️ 配置同步",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = PrimaryBrown
                )
            }
        }
    }
}

@Composable
private fun StatsCard(localStats: LocalStats) {
    val totalPlayMs = localStats.totalPlayMs
    val totalPlays = localStats.totalPlays
    val todayPlayMs = localStats.dayPlayMs

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(AccentMint)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "听歌统计",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .border(0.5.dp, Stone200, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Stone100)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "本机本地",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextSoft
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem(label = "累计听歌", value = formatDuration(totalPlayMs), accent = AccentAmber, modifier = Modifier.weight(1f))
            StatItem(label = "播放次数", value = "${totalPlays}次", accent = AccentCoral, modifier = Modifier.weight(1f))
            StatItem(label = "今日收听", value = formatDuration(todayPlayMs), accent = AccentCyan, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = accent
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextSoft
        )
    }
}

@Composable
private fun LibraryCard(
    historyCount: Int,
    favoritesCount: Int,
    cacheCount: Int,
    cacheSize: Long,
    onOpenRecent: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenCacheList: () -> Unit,
    onOpenMyPlaylists: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(vertical = 4.dp),
    ) {
        LibraryItem(
            icon = Icons.Filled.History,
            iconTint = AccentCyan,
            title = "最近播放",
            badge = "${historyCount}首",
            onClick = onOpenRecent
        )
        LibraryItem(
            icon = Icons.Filled.Favorite,
            iconTint = AccentCoral,
            title = "我的收藏",
            badge = "${favoritesCount}首",
            onClick = onOpenFavorites
        )
        LibraryItem(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            iconTint = AccentAmber,
            title = "我的歌单",
            badge = "自建歌单",
            onClick = onOpenMyPlaylists
        )
        LibraryItem(
            icon = Icons.Filled.DownloadDone,
            iconTint = AccentMint,
            title = "本地缓存",
            badge = if (cacheCount > 0) "${cacheCount}首 · ${AudioCacheService.formatSize(cacheSize)}" else "暂无",
            onClick = onOpenCacheList
        )
    }
}

@Composable
private fun LibraryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    badge: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = badge,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextSoft
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0分"
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    return if (h > 0) "${h}小时${m % 60}分" else "${m}分"
}