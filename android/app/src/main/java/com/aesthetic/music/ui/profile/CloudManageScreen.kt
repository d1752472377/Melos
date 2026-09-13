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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.SessionDto
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.Background
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
 * 账号与同步页。
 *
 * 这是"后端 = 只保存与同步用户数据"这件事在界面上的唯一入口，分三块：
 *  1. **服务器地址**：我这台设备连哪个后端（设备级配置，与账号无关）；
 *  2. **账号**：注册 / 登录 / 退出 / 登录设备管理（数据归属的唯一凭据）；
 *  3. **同步**：状态、立即同步，以及"到底同步了哪些数据"的清单。
 *
 * 旧版本这里只有一个"同步密钥"输入框——密钥模式下没有"用户"这个概念，
 * 服务端无法区分谁的数据，也就谈不上登录、退出、多设备管理。
 */
@Composable
fun CloudManageScreen(
    serverUrl: String,
    isServerConfigured: Boolean,
    isLoggedIn: Boolean,
    user: UserInfo?,
    isBusy: Boolean,
    authError: String?,
    sessions: List<SessionDto>,
    isLoadingSessions: Boolean,
    lastSyncedAt: Long,
    lastSyncStatus: String?,
    isSyncing: Boolean,
    isTesting: Boolean,
    onBack: () -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onTestConnection: ((Boolean, String) -> Unit) -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onClearAuthError: () -> Unit,
    onLoadSessions: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onLogout: () -> Unit,
    onLogoutAll: () -> Unit,
    onPerformSync: () -> Unit,
    onLogoutAndClearLocal: () -> Unit,
) {
    var urlInput by remember(serverUrl) { mutableStateOf(serverUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showSessions by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val lastSyncTimeText = if (lastSyncedAt > 0) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastSyncedAt))
    } else {
        "从未同步"
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("退出并清空本机数据？") },
            text = {
                Text(
                    "将删除本机上的收藏、最近播放与自建歌单，并重置同步进度。" +
                        "服务端保存的账号数据不会被删除，重新登录后会从云端拉回来。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onLogoutAndClearLocal()
                }) { Text("清空并退出", color = PrimaryBrown) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp)
                .padding(bottom = LocalBottomBarPadding.current)
                .verticalScroll(rememberScrollState()),
        ) {
            // ===== 顶部导航栏 =====
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, Stone200, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "账号与同步",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ===== 1. 服务器地址 =====
            SectionCard {
                SectionTitle("服务器", "后端只负责保存与同步你的数据，地址由你自填")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("服务器地址，例如 https://music.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        text = "保存地址",
                        filled = true,
                        enabled = urlInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSaveServerUrl(urlInput)
                            testResult = null
                        },
                    )
                    ActionButton(
                        text = if (isTesting) "测试中…" else "测试连接",
                        filled = false,
                        enabled = !isTesting,
                        modifier = Modifier.weight(1f),
                        onClick = { onTestConnection { _, msg -> testResult = msg } },
                    )
                }
                if (!testResult.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = testResult!!, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextSoft)
                }
                if (!isServerConfigured) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "未填写地址时，所有云接口都会被拦截（不会误连到别人的服务器）。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 2. 账号 =====
            SectionCard {
                if (isLoggedIn && user != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(AccentMint.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = AccentMint, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = user.nickname.ifBlank { user.username },
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                color = TextPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = buildString {
                                    append("@").append(user.username)
                                    append(" · ID ").append(user.id)
                                    if (!user.email.isNullOrBlank()) append(" · ").append(user.email)
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextSoft,
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton(
                            text = if (isSyncing) "同步中…" else "立即同步",
                            filled = true,
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f),
                            onClick = onPerformSync,
                        )
                        ActionButton(
                            text = if (showSessions) "收起设备" else "登录设备",
                            filled = false,
                            enabled = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showSessions = !showSessions
                                if (showSessions) onLoadSessions()
                            },
                        )
                    }

                    if (showSessions) {
                        Spacer(Modifier.height(10.dp))
                        if (isLoadingSessions) {
                            Text(text = "正在获取设备列表…", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextSoft)
                        } else if (sessions.isEmpty()) {
                            Text(text = "没有其它在线设备", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextSoft)
                        } else {
                            sessions.forEach { s ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Devices, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = s.deviceName.ifBlank { "未知设备" } + if (s.isCurrent) "（本机）" else "",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = TextPrimary,
                                        )
                                        Text(
                                            text = "最近活跃 " + formatTime(s.lastSeenAt),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = TextSoft,
                                        )
                                    }
                                    if (!s.isCurrent) {
                                        TextButton(onClick = { onRevokeSession(s.id) }) {
                                            Text("退出", fontSize = 12.sp, color = PrimaryBrown)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton("退出登录", filled = false, enabled = true, modifier = Modifier.weight(1f), onClick = onLogout)
                        ActionButton("退出全部设备", filled = false, enabled = true, modifier = Modifier.weight(1f), onClick = onLogoutAll)
                    }
                } else {
                    SectionTitle("登录账号", "登录后才能把数据同步到服务器（服务端只做存储与同步）")

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeChip("登录", mode == AuthMode.LOGIN) { mode = AuthMode.LOGIN; onClearAuthError() }
                        ModeChip("注册", mode == AuthMode.REGISTER) { mode = AuthMode.REGISTER; onClearAuthError() }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; onClearAuthError() },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; onClearAuthError() },
                        label = { Text(if (mode == AuthMode.REGISTER) "密码（至少 6 位）" else "密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (mode == AuthMode.REGISTER) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            label = { Text("昵称（可留空）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (!authError.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(text = authError!!, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = PrimaryBrown)
                    }

                    Spacer(Modifier.height(12.dp))
                    ActionButton(
                        text = when {
                            isBusy -> "请稍候…"
                            mode == AuthMode.REGISTER -> "注册并登录"
                            else -> "登录"
                        },
                        filled = true,
                        enabled = !isBusy && isServerConfigured,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (mode == AuthMode.REGISTER) onRegister(username, password, nickname)
                            else onLogin(username, password)
                        },
                    )

                    if (!isServerConfigured) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "请先在上方填写并保存服务器地址。",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 3. 同步状态与范围 =====
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (isLoggedIn && isServerConfigured) AccentMint.copy(alpha = 0.15f) else Stone100),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isLoggedIn && isServerConfigured) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                            contentDescription = null,
                            tint = if (isLoggedIn && isServerConfigured) AccentMint else TextMuted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                !isServerConfigured -> "未配置服务器"
                                !isLoggedIn -> "未登录"
                                else -> "同步已就绪"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when {
                                !isServerConfigured -> "填写服务器地址后可登录账号"
                                !isLoggedIn -> "登录账号后开始双向同步"
                                else -> serverUrl
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Stone100).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InfoRow("上次同步", lastSyncTimeText)
                    if (!lastSyncStatus.isNullOrBlank()) InfoRow("同步状态", lastSyncStatus!!)
                    InfoRow("同步方式", "增量 + 删除墓碑（改了什么传什么）")
                }

                Spacer(Modifier.height(14.dp))
                SectionTitle("同步哪些数据", "服务端只保存这些内容，不做其它处理")
                Spacer(Modifier.height(8.dp))
                SyncScopeItem("收藏歌曲", "歌曲信息（标题/歌手/专辑/平台），不含播放直链")
                SyncScopeItem("收藏的歌单", "歌单名称与网络封面；歌单内的歌曲由平台实时获取")
                SyncScopeItem("自建歌单", "歌单名称、封面、曲目与顺序（顺序是数据，必须一起同步）")
                SyncScopeItem("最近播放", "最近 100 条的歌曲信息，用来在多设备间延续")
                SyncScopeItem("听歌统计", "播放明细事件（歌名/听歌时长/时间），只上传不下载")
                SyncScopeItem("音源脚本", "你自己写/导入的 JS 脚本源码与启用状态")
                SyncScopeItem("下载记录", "哪台设备下载过哪首歌（文件本身不会上传）")
                SyncScopeItem("账号信息", "用户名、昵称、邮箱、头像地址（服务端为准）")

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "不会同步：音频文件本身、播放直链、本机文件路径。" +
                        "换设备后需要重新下载歌曲，下载记录只用来告诉你「下过哪些」。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = TextSoft,
                )
            }

            if (isLoggedIn) {
                Spacer(Modifier.height(16.dp))
                SectionCard {
                    SectionTitle("设备清理", "把本机个人数据抹掉，适合换机/转手前使用")
                    Spacer(Modifier.height(10.dp))
                    ActionButton(
                        text = "退出并清空本机数据",
                        filled = false,
                        enabled = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { confirmClear = true },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private enum class AuthMode { LOGIN, REGISTER }

private fun formatTime(ms: Long): String =
    if (ms <= 0) "未知"
    else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = TextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextSoft)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextSoft)
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextPrimary)
    }
}

@Composable
private fun SyncScopeItem(name: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.padding(top = 5.dp).size(5.dp).clip(RoundedCornerShape(3.dp)).background(AccentMint),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = name, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = TextPrimary)
            Text(text = detail, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp), color = TextSoft)
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PrimaryBrown else Stone100)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = if (selected) Color.White else TextMuted,
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (filled) Modifier.background(if (enabled) PrimaryBrown else Stone200) else Modifier.border(1.dp, Stone200, RoundedCornerShape(10.dp)).background(Surface))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = if (filled) Color.White else TextPrimary,
        )
    }
}
