package com.aesthetic.music.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.api.UserStatsDto
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.ui.theme.AmberLight
import com.aesthetic.music.ui.theme.PlayerBg
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TagBg
import com.aesthetic.music.ui.theme.TagText
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 个人中心：真实用户信息 + 真实听歌统计 + 资料库入口。
 */
@Composable
fun ProfileScreen(
    user: UserInfo?,
    localStats: LocalStats,
    cloudStats: UserStatsDto?,
    historyCount: Int,
    favoritesCount: Int,
    cacheCount: Int,
    cacheSize: Long,
    onOpenSettings: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenCacheList: () -> Unit,
    onOpenMyPlaylists: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onUpdateNickname: (String) -> Unit,
) {
    var showNicknameDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = 28.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 150.dp // 留足底栏避让高度，确保可完全向上滑出
            )
        ) {
            // 顶栏
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "个人中心",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Surface)
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 用户信息卡片
            item {
                UserCard(
                    user = user,
                    historyCount = historyCount,
                    favoritesCount = favoritesCount,
                    onLogin = onLogin,
                    onEditNickname = { showNicknameDialog = true },
                    onLogout = onLogout,
                )
            }

            // 听歌统计卡片
            item {
                StatsCard(localStats = localStats, cloudStats = cloudStats, isLoggedIn = user != null)
            }

            // 资料库卡片（含设置统一入口）
            item {
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

    if (showNicknameDialog) {
        NicknameDialog(
            current = user?.nickname ?: "",
            onDismiss = { showNicknameDialog = false },
            onConfirm = { nickname ->
                onUpdateNickname(nickname)
                showNicknameDialog = false
            },
        )
    }
}

@Composable
private fun UserCard(
    user: UserInfo?,
    historyCount: Int,
    favoritesCount: Int,
    onLogin: () -> Unit,
    onEditNickname: () -> Unit,
    onLogout: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(PrimaryBrown, PlayerBg)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (user != null) user.nickname.ifBlank { user.username } else "Melos · Listener",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (user != null) "最近听过 $historyCount 首 · 收藏 $favoritesCount 首"
                    else "登录后可云同步收藏、历史与歌单",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            if (user == null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .clickable(onClick = onLogin)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        "登录 / 注册",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryBrown,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable(onClick = onEditNickname)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "昵称",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable(onClick = onLogout)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "退出",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(localStats: LocalStats, cloudStats: UserStatsDto?, isLoggedIn: Boolean) {
    val totalPlayMs = if (isLoggedIn && cloudStats != null) cloudStats.totalPlayMs else localStats.totalPlayMs
    val totalPlays = if (isLoggedIn && cloudStats != null) cloudStats.totalPlays else localStats.totalPlays
    val todayPlayMs = if (isLoggedIn && cloudStats != null) cloudStats.todayPlayMs else localStats.dayPlayMs
    val topArtist = cloudStats?.topArtists?.firstOrNull()?.artist

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("听歌统计", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                if (isLoggedIn) "云端同步" else "本机统计",
                style = MaterialTheme.typography.labelSmall,
                color = if (isLoggedIn) AmberLight else TextSoft,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            StatItem("累计时长", StatsStore.formatDuration(totalPlayMs), Modifier.weight(1f))
            StatItem("累计播放", "$totalPlays 次", Modifier.weight(1f))
            StatItem("今日", StatsStore.formatDuration(todayPlayMs), Modifier.weight(1f))
        }
        if (!topArtist.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "最常听：$topArtist",
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSoft)
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
            .clip(RoundedCornerShape(16.dp))
            .background(Surface),
    ) {
        LibraryRow(Icons.Filled.History, "最近播放", "$historyCount 首", onClick = onOpenRecent)
        Divider()
        LibraryRow(Icons.Filled.Favorite, "我的收藏", "$favoritesCount 首", onClick = onOpenFavorites)
        Divider()
        LibraryRow(
            Icons.AutoMirrored.Filled.QueueMusic,
            "我的歌单",
            "自建歌单 · 支持云同步",
            onClick = onOpenMyPlaylists,
        )
        Divider()
        LibraryRow(
            Icons.AutoMirrored.Filled.QueueMusic,
            "本地缓存",
            "$cacheCount 首 · ${AudioCacheService.formatSize(cacheSize)}",
            onClick = onOpenCacheList,
        )
        Divider()
        LibraryRow(
            Icons.Filled.Settings,
            "应用设置",
            "音质、外观主题与悬浮胶囊",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun LibraryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(TagBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TagText, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSoft, maxLines = 1)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSoft, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun NicknameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改昵称", fontWeight = FontWeight.Bold) },
        text = {
            BasicTextField(
                value = input,
                onValueChange = { if (it.length <= 24) input = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Stone100)
                    .padding(12.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) { Text("保存", color = PrimaryBrown, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSoft) }
        },
    )
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(Stone100)
    )
}