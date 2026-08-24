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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DownloadDone
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.api.UserStatsDto
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.player.AudioCacheService
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
 * 个人中心：精致仪表盘质感 + 多维度活泼色彩。
 */
@Composable
fun ProfileScreen(
    user: UserInfo?,
    localStats: LocalStats,
    cloudStats: UserStatsDto?,
    cloudStatsLoading: Boolean = false,
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
                top = 24.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 150.dp // 留足底栏避让高度
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
                            text = "我的音乐资产与听歌足迹",
                            style = MaterialTheme.typography.bodySmall,
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

            // 用户信息卡片 (Level 1 Hero Card: 14dp)
            item(key = "profile_user_card") {
                UserCard(
                    user = user,
                    historyCount = historyCount,
                    favoritesCount = favoritesCount,
                    onLogin = onLogin,
                    onEditNickname = { showNicknameDialog = true },
                    onLogout = onLogout,
                )
            }

            // 听歌统计卡片 (仪表盘质感: 12dp)
            item(key = "profile_stats_card") {
                StatsCard(
                    localStats = localStats,
                    cloudStats = cloudStats,
                    isLoggedIn = user != null,
                    cloudLoading = cloudStatsLoading,
                )
            }

            // 资料库卡片 (列表容器: 12dp)
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
                    text = if (user != null) user.nickname.ifBlank { user.username } else "Melos 音乐人",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (user != null) "最近播放 $historyCount 首 · 收藏 $favoritesCount 首"
                    else "登录后享受云端收藏与播放同步",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            if (user == null) {
                // Level 1 主操作白底胶囊
                Box(
                    modifier = Modifier
                        .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.1f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .clickable(onClick = onLogin)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "登录 / 注册",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = PrimaryBrown
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(onClick = onEditNickname)
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "昵称",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(onClick = onLogout)
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "退出",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(
    localStats: LocalStats,
    cloudStats: UserStatsDto?,
    isLoggedIn: Boolean,
    cloudLoading: Boolean = false,
) {
    val totalPlayMs = if (isLoggedIn && cloudStats != null) cloudStats.totalPlayMs else localStats.totalPlayMs
    val totalPlays = if (isLoggedIn && cloudStats != null) cloudStats.totalPlays else localStats.totalPlays
    val todayPlayMs = if (isLoggedIn && cloudStats != null) cloudStats.todayPlayMs else localStats.dayPlayMs
    val topArtist = cloudStats?.topArtists?.firstOrNull()?.artist

    // 徽标三态：已登录但云端数据未就绪时数值其实是本机的，
    // 不能标"云端实时"造成自相矛盾（原先正是这个 bug）
    val badgeText: String
    val badgeColor: Color
    when {
        isLoggedIn && cloudStats != null -> { badgeText = "云端实时"; badgeColor = AccentAmber }
        isLoggedIn && cloudLoading -> { badgeText = "云端同步中…"; badgeColor = AccentCyan }
        else -> { badgeText = "本机统计"; badgeColor = TextSoft }
    }

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
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = badgeColor
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth()) {
            StatItem("累计时长", StatsStore.formatDuration(totalPlayMs), AccentMint, Modifier.weight(1f))
            StatItem("累计播放", "$totalPlays 次", AccentCyan, Modifier.weight(1f))
            StatItem("今日专注", StatsStore.formatDuration(todayPlayMs), AccentCoral, Modifier.weight(1f))
        }

        if (!topArtist.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Stone100.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "最常听歌手：$topArtist",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = TextSoft,
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, barColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(barColor)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp
            ),
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
            .background(Surface),
    ) {
        LibraryRow(
            icon = Icons.Filled.Favorite,
            iconTint = AccentCoral,
            iconBg = if (Palette.isDark) Color(0xFF33171D) else Color(0xFFFFEEF1),
            label = "我的收藏",
            subtitle = "$favoritesCount 首心动歌曲",
            onClick = onOpenFavorites
        )
        Divider()
        LibraryRow(
            icon = Icons.Filled.History,
            iconTint = AccentCyan,
            iconBg = if (Palette.isDark) Color(0xFF142436) else Color(0xFFEEF5FF),
            label = "最近播放",
            subtitle = "$historyCount 首足迹",
            onClick = onOpenRecent
        )
        Divider()
        LibraryRow(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            iconTint = AccentAmber,
            iconBg = if (Palette.isDark) Color(0xFF332512) else Color(0xFFFFF7EB),
            label = "我的歌单",
            subtitle = "自建歌单 · 支持多端同步",
            onClick = onOpenMyPlaylists,
        )
        Divider()
        LibraryRow(
            icon = Icons.Filled.DownloadDone,
            iconTint = AccentMint,
            iconBg = if (Palette.isDark) Color(0xFF142E25) else Color(0xFFEBFBF5),
            label = "本地缓存",
            subtitle = "$cacheCount 首 · ${AudioCacheService.formatSize(cacheSize)}",
            onClick = onOpenCacheList,
        )
        Divider()
        LibraryRow(
            icon = Icons.Filled.Settings,
            iconTint = PrimaryBrown,
            iconBg = if (Palette.isDark) Color(0xFF28233C) else Color(0xFFF1F0FE),
            label = "应用设置",
            subtitle = "音质输出、外观主题与悬浮胶囊",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun LibraryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp
                ),
                color = TextSoft,
                maxLines = 1
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSoft, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun NicknameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf(current) }
    val canSave = input.trim().isNotEmpty()
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(Stone100)
                    .padding(12.dp),
            )
        },
        confirmButton = {
            // 空白昵称会被 VM 静默丢弃，直接禁用保存避免"点了没反应"的困惑
            TextButton(
                onClick = { onConfirm(input.trim()) },
                enabled = canSave,
            ) { Text("保存", color = PrimaryBrown, fontWeight = FontWeight.Bold) }
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
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(Stone200.copy(alpha = 0.5f))
    )
}