package com.aesthetic.music.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.SessionDto
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.SecondaryPageTopBar
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 账号与云端同步页（云端优先现代重构）。
 *
 * 彻底移除技术说教与冗余配置：
 * 1. 后端服务默认连接，无需手动输入服务器地址或测试连通性；
 * 2. 移除离线优先时代的"清空本地数据"，全面以云端账号为准；
 * 3. 聚焦核心诉求：账号状态、一键云同步、多端在线设备管理、退出登录。
 */
@Composable
fun CloudManageScreen(
    user: UserInfo? = null,
    sessions: List<SessionDto> = emptyList(),
    isLoadingSessions: Boolean = false,
    lastSyncedAt: Long = 0,
    lastSyncStatus: String? = null,
    isSyncing: Boolean = false,
    onBack: () -> Unit = {},
    onLoadSessions: () -> Unit = {},
    onRevokeSession: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    onLogoutAll: () -> Unit = {},
    onPerformSync: () -> Unit = {},
) {
    var showSessions by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onLoadSessions()
    }

    val lastSyncTimeText = if (lastSyncedAt > 0) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastSyncedAt))
    } else {
        "刚刚"
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "退出登录？",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    text = "退出当前账号后，需重新验证账号才能继续使用 Melos。您的云端数据已安全保存。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("退出登录", color = PrimaryBrown, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消", color = TextMuted)
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        HomeAmbientGlowBackground()
        Column(modifier = Modifier.fillMaxSize()) {
            // ===== 顶栏：与排行榜/歌单广场等二级页完全一致（见 SecondaryPageTopBar）=====
            SecondaryPageTopBar(title = "账号与云端同步", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = BottomContentSpacer)
                    .verticalScroll(rememberScrollState()),
            ) {
            // ===== 1. 账号概览与同步状态卡片 =====
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(PrimaryBrown.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = PrimaryBrown,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = user?.nickname?.ifBlank { user.username } ?: "Melos 用户",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = TextPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            // 云端连接健康徽章
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentMint.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(AccentMint),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "云端已连接",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = AccentMint,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "@${user?.username ?: "user"} · ID ${user?.id ?: "-"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextSoft,
                        )
                        if (!user?.email.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = ProfileFormat.maskEmail(user?.email),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextMuted,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 同步状态信息
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Stone100)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "上次同步时间",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextSoft,
                        )
                        Text(
                            text = lastSyncTimeText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            color = TextPrimary,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "当前状态",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextSoft,
                        )
                        Text(
                            text = if (isSyncing) "正在同步…" else (lastSyncStatus ?: "数据已是最新"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSyncing) PrimaryBrown else AccentMint,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSyncing) Stone200 else PrimaryBrown)
                            .clickable(enabled = !isSyncing, onClick = onPerformSync)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isSyncing) "同步中…" else "立即同步",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                            .background(Surface)
                            .clickable {
                                showSessions = !showSessions
                                if (showSessions) onLoadSessions()
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Devices,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (showSessions) "收起设备" else "登录设备 (${if (sessions.isNotEmpty()) sessions.size.toString() else "多端"})",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                color = TextPrimary,
                            )
                        }
                    }
                }
            }

            // ===== 2. 活跃多端设备列表 =====
            if (showSessions) {
                Spacer(Modifier.height(16.dp))
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "多端登录管理",
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                color = TextPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "当前在 ${sessions.size.coerceAtLeast(1)} 台设备上保持在线",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextSoft,
                            )
                        }
                        if (sessions.any { !it.isCurrent }) {
                            TextButton(onClick = onLogoutAll) {
                                Text("下线其它全部", fontSize = 12.sp, color = PrimaryBrown)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (isLoadingSessions) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PrimaryBrown, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在获取多端会话…", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = TextSoft)
                        }
                    } else if (sessions.isEmpty()) {
                        Text(
                            text = "暂无其他在线设备",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextSoft,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        sessions.forEach { s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (s.isCurrent) Stone100 else Surface)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (s.isCurrent) PrimaryBrown else TextMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = s.deviceName.ifBlank { "未知设备" },
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            color = TextPrimary,
                                        )
                                        if (s.isCurrent) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(AccentMint.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = "本机",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                    color = AccentMint,
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "最近活跃 · " + formatTime(s.lastSeenAt),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = TextSoft,
                                    )
                                }
                                if (!s.isCurrent) {
                                    TextButton(onClick = { onRevokeSession(s.id) }) {
                                        Text("下线", fontSize = 12.sp, color = PrimaryBrown)
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            // ===== 3. 云端漫游数据清单 =====
            Spacer(Modifier.height(16.dp))
            SectionCard {
                Column {
                    Text(
                        text = "云端漫游数据",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "全端自动保持最新，换机登录即时漫游",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                    )
                }

                Spacer(Modifier.height(12.dp))

                CloudSyncScopeItem(
                    icon = Icons.Filled.MusicNote,
                    title = "我的收藏与自建歌单",
                    subtitle = "收藏曲目、自建歌单列表与自定义排布顺序",
                )
                CloudSyncScopeItem(
                    icon = Icons.Filled.History,
                    title = "最近播放与漫游",
                    subtitle = "跨设备接续播放，延续音乐体验",
                )
                CloudSyncScopeItem(
                    icon = Icons.Filled.BarChart,
                    title = "听歌时长与统计分析",
                    subtitle = "精准沉淀听歌数据与音乐报告",
                )
                CloudSyncScopeItem(
                    icon = Icons.Filled.Tune,
                    title = "个人资料与偏好",
                    subtitle = "头像、昵称、个性签名及应用偏好",
                )
                CloudSyncScopeItem(
                    icon = Icons.Filled.Extension,
                    title = "音源脚本与插件",
                    subtitle = "已导入的音源插件与配置状态",
                )

                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Stone100)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "提示：所有数据均与您的账号安全绑定，登录任意设备即可完整同步，无需手动备份或迁移。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = TextSoft,
                    )
                }
            }

            // ===== 4. 退出登录卡片 =====
            Spacer(Modifier.height(16.dp))
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "退出当前账号",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "退出后需重新验证账号登录",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                            .background(Surface)
                            .clickable { showLogoutDialog = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "退出登录",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = TextPrimary,
                        )
                    }
                }
            }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String =
    if (ms <= 0) "未知"
    else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun CloudSyncScopeItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Stone100),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryBrown,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
            )
        }
    }
}
