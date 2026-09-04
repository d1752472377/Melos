package com.aesthetic.music.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.data.userapi.ScriptMetadata
import com.aesthetic.music.ui.components.BottomBarReserved
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TagBg
import com.aesthetic.music.ui.theme.TagText
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
    }

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    quality: QualityLevel,
    floatingCapsuleEnabled: Boolean,
    activeScript: ScriptMetadata? = null,
    isSyncConfigured: Boolean = false,
    syncServerUrl: String = "",
    syncToken: String = "",
    lastSyncedAt: Long = 0L,
    lastSyncStatus: String? = null,
    isSyncing: Boolean = false,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onQualityChange: (QualityLevel) -> Unit,
    onFloatingCapsuleChange: (Boolean) -> Unit,
    onCheckUpdate: (onResult: (String) -> Unit) -> Unit,
    onSaveSyncConfig: (String, String) -> Unit = { _, _ -> },
    onTestSyncConnection: ((Boolean, String) -> Unit) -> Unit = {},
    onPerformSync: () -> Unit = {},
    onImportScriptText: (String, String?) -> Unit = { _, _ -> },
    onImportScriptUrl: (String, (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onUnloadScript: () -> Unit = {},
) {
    var showQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showScriptImportDialog by remember { mutableStateOf(false) }
    var showSyncConfigDialog by remember { mutableStateOf(false) }
    var showUnloadConfirm by remember { mutableStateOf(false) }

    var syncTestResult by remember { mutableStateOf<String?>(null) }
    var testingSync by remember { mutableStateOf(false) }

    var updateStatus by remember { mutableStateOf("点击检查是否有新版本") }
    var checking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = BottomBarReserved),
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "设置与音源",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(10.dp))

        // ===== 1. 自定义音源脚本管理 =====
        SectionCard(title = "📜 自定义音源脚本 (Local-First)") {
            if (activeScript != null) {
                // 脚本信息卡
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Stone100)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeScript.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentMint.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v${activeScript.version}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = AccentMint
                            )
                        }
                    }
                    if (activeScript.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = activeScript.description,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "支持平台: ${activeScript.supportedSources.joinToString(", ")} · QuickJS 沙箱本地解析",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextMuted
                    )
                }

                SettingRow(
                    icon = Icons.Filled.Code,
                    iconTint = PrimaryBrown,
                    label = "导入 / 替换音源脚本",
                    subtitle = "可粘贴 JS 完整代码或输入订阅直链",
                    onClick = { showScriptImportDialog = true }
                )
                if (isSyncConfigured) {
                    SettingRow(
                        icon = Icons.Filled.Sync,
                        iconTint = PrimaryBrown,
                        label = "从私有服务器同步音源脚本",
                        subtitle = "拉取服务端后台当前激活测试通过的音源",
                        onClick = onPerformSync
                    )
                }
                SettingRow(
                    icon = Icons.Filled.Delete,
                    iconTint = MaterialTheme.colorScheme.error,
                    label = "卸载当前脚本",
                    subtitle = "清空本地沙箱中的脚本",
                    onClick = { showUnloadConfirm = true }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Stone100)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⚠️ 尚未安装音源脚本",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Melos 本身不内置任何音乐直链分发服务。播放歌曲需依赖本地音源脚本（兼容 LX Music 音源规范），所有歌曲解析均在手机本地执行并直连官方 CDN。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft
                    )
                }
                SettingRow(
                    icon = Icons.Filled.Code,
                    iconTint = PrimaryBrown,
                    label = "＋ 导入自定义音源脚本",
                    subtitle = "粘贴 JS 源码或输入订阅下载直链",
                    onClick = { showScriptImportDialog = true }
                )
                if (isSyncConfigured) {
                    SettingRow(
                        icon = Icons.Filled.Sync,
                        iconTint = PrimaryBrown,
                        label = "☁️ 从私有云同步脚本与歌单",
                        subtitle = "一键获取私有服务器托管的健康音源",
                        onClick = onPerformSync
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ===== 2. 私有云同步 =====
        SectionCard(title = "☁️ 私有云同步配置 (个人自建)") {
            SettingRow(
                icon = Icons.Filled.Cloud,
                iconTint = PrimaryBrown,
                label = "私有服务器连接",
                subtitle = if (isSyncConfigured) syncServerUrl else "未配置（点击填入地址与密钥）",
                onClick = { showSyncConfigDialog = true }
            )
            if (isSyncConfigured) {
                SettingRow(
                    icon = Icons.Filled.Sync,
                    iconTint = PrimaryBrown,
                    label = "一键双向数据同步",
                    subtitle = when {
                        isSyncing -> "正在同步中…"
                        lastSyncedAt > 0 -> "上次: ${formatTime(lastSyncedAt)} · ${lastSyncStatus ?: "正常"}"
                        else -> "同步音源脚本、收藏曲目、歌单及历史"
                    },
                    onClick = onPerformSync
                )
                SettingRow(
                    icon = Icons.Filled.Info,
                    iconTint = PrimaryBrown,
                    label = "测试服务器连通性",
                    subtitle = syncTestResult ?: "点击检测服务器响应",
                    onClick = {
                        if (testingSync) return@SettingRow
                        testingSync = true
                        syncTestResult = "正在测试…"
                        onTestSyncConnection { ok, msg ->
                            testingSync = false
                            syncTestResult = if (ok) "✅ $msg" else "❌ $msg"
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ===== 3. 外观 =====
        SectionCard(title = "外观与主题") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Stone100)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val isSelected = themeMode == mode
                    val animatedBg by animateColorAsState(
                        targetValue = if (isSelected) Surface else Color.Transparent,
                        animationSpec = tween(150),
                        label = "theme_tab_bg",
                    )
                    val animatedTextColor by animateColorAsState(
                        targetValue = if (isSelected) PrimaryBrown else TextSoft,
                        animationSpec = tween(150),
                        label = "theme_tab_text",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(animatedBg)
                            .clickable { onThemeModeChange(mode) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = animatedTextColor
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ===== 4. 桌面胶囊 =====
        SectionCard(title = "系统与交互") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryBrown.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Widgets, contentDescription = null, tint = PrimaryBrown, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "桌面悬浮胶囊",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = if (floatingCapsuleEnabled) "播放时跨 App 悬浮显示（需悬浮窗权限）" else "在桌面上便捷控制播放",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                    )
                }
                Switch(
                    checked = floatingCapsuleEnabled,
                    onCheckedChange = onFloatingCapsuleChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryBrown,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Stone200,
                        uncheckedBorderColor = Color.Transparent
                    ),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ===== 5. 播放与音质 =====
        SectionCard(title = "播放与音效") {
            SettingRow(
                icon = Icons.Filled.MusicNote,
                iconTint = PrimaryBrown,
                label = "音质等级",
                subtitle = quality.label,
                onClick = { showQualityDialog = true }
            )
        }

        Spacer(Modifier.height(14.dp))

        // ===== 6. 关于 =====
        SectionCard(title = "关于软件") {
            SettingRow(
                icon = Icons.Filled.SystemUpdate,
                iconTint = PrimaryBrown,
                label = "检查更新",
                subtitle = updateStatus,
                onClick = {
                    if (checking) return@SettingRow
                    checking = true
                    updateStatus = "检查中…"
                    onCheckUpdate { result ->
                        updateStatus = result
                        checking = false
                    }
                }
            )
            SettingRow(
                icon = Icons.Filled.Info,
                iconTint = PrimaryBrown,
                label = "当前版本",
                subtitle = com.aesthetic.music.BuildConfig.VERSION_NAME,
                onClick = {}
            )
        }
    }

    // ===== 音质选择弹窗 =====
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = Surface,
            shape = RoundedCornerShape(14.dp),
            title = {
                Text(
                    text = "选择播放音质",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    QualityLevel.entries.forEach { level ->
                        val isSelected = quality == level
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Stone100 else Color.Transparent)
                                .clickable {
                                    onQualityChange(level)
                                    showQualityDialog = false
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBrown)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = level.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "音质等级取决于音源脚本支持的最高码率，如果音源不支持无损将自动平滑降级。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("取消", color = TextMuted, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    // ===== 导入音源脚本弹窗 =====
    if (showScriptImportDialog) {
        ScriptImportDialog(
            onDismiss = { showScriptImportDialog = false },
            onImportText = { text, name ->
                onImportScriptText(text, name)
                showScriptImportDialog = false
            },
            onImportUrl = { url, cb ->
                onImportScriptUrl(url, cb)
            }
        )
    }

    // ===== 私有服务器配置弹窗 =====
    if (showSyncConfigDialog) {
        SyncConfigDialog(
            currentUrl = syncServerUrl,
            currentToken = syncToken,
            onDismiss = { showSyncConfigDialog = false },
            onSave = { url, token ->
                onSaveSyncConfig(url, token)
                showSyncConfigDialog = false
            },
            onTest = { cb ->
                onTestSyncConnection(cb)
            }
        )
    }

    // ===== 卸载脚本确认弹窗 =====
    if (showUnloadConfirm) {
        AlertDialog(
            onDismissRequest = { showUnloadConfirm = false },
            title = { Text("卸载音源脚本") },
            text = { Text("确定要卸载当前音源脚本吗？卸载后在线歌曲将无法直接解析播放。") },
            confirmButton = {
                TextButton(onClick = {
                    showUnloadConfirm = false
                    onUnloadScript()
                }) {
                    Text("卸载", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnloadConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ScriptImportDialog(
    onDismiss: () -> Unit,
    onImportText: (String, String?) -> Unit,
    onImportUrl: (String, (Boolean, String) -> Unit) -> Unit,
) {
    var mode by remember { mutableIntStateOf(0) } // 0 = 粘贴代码, 1 = 网络链接
    var scriptText by remember { mutableStateOf("") }
    var scriptUrl by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入音源脚本", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Tab 切换
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Stone100)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (mode == 0) Surface else Color.Transparent)
                            .clickable { mode = 0 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("粘贴 JS 代码", fontSize = 12.sp, color = if (mode == 0) PrimaryBrown else TextSoft)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (mode == 1) Surface else Color.Transparent)
                            .clickable { mode = 1 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("网络订阅 URL", fontSize = 12.sp, color = if (mode == 1) PrimaryBrown else TextSoft)
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (mode == 0) {
                    OutlinedTextField(
                        value = scriptText,
                        onValueChange = { scriptText = it },
                        label = { Text("JS 代码") },
                        placeholder = { Text("粘贴完整的音源脚本内容...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        maxLines = 15,
                    )
                } else {
                    OutlinedTextField(
                        value = scriptUrl,
                        onValueChange = { scriptUrl = it },
                        label = { Text("订阅/下载直链") },
                        placeholder = { Text("https://raw.githubusercontent.com/.../latest.js") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("自定义名称（可选）") },
                    placeholder = { Text("留空将从脚本注释 @name 自动识别") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (errorMsg != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    errorMsg = null
                    if (mode == 0) {
                        if (scriptText.isBlank()) {
                            errorMsg = "请先粘贴脚本代码"
                            return@Button
                        }
                        onImportText(scriptText, customName.ifBlank { null })
                    } else {
                        if (scriptUrl.isBlank()) {
                            errorMsg = "请输入下载链接"
                            return@Button
                        }
                        downloading = true
                        onImportUrl(scriptUrl) { ok, msg ->
                            downloading = false
                            if (!ok) {
                                errorMsg = msg
                            } else {
                                onDismiss()
                            }
                        }
                    }
                },
                enabled = !downloading,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown)
            ) {
                Text(if (downloading) "正在抓取…" else "确认导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun SyncConfigDialog(
    currentUrl: String,
    currentToken: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onTest: ((Boolean, String) -> Unit) -> Unit,
) {
    var url by remember { mutableStateOf(currentUrl) }
    var token by remember { mutableStateOf(currentToken) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置私有服务器", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "填入您的 Melos 私有后端地址与同步密钥，即可一键在手机和电脑之间同步已激活的音源脚本及歌单：",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://192.168.1.100:3000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("同步密钥 (Sync Token)") },
                    placeholder = { Text("melos_sync_secret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (testResult != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(text = testResult!!, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(url, token) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown)
            ) {
                Text("保存配置")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        if (testing) return@TextButton
                        testing = true
                        testResult = "测试中…"
                        onSave(url, token) // 先临时保存
                        onTest { ok, msg ->
                            testing = false
                            testResult = if (ok) "✅ $msg" else "❌ $msg"
                        }
                    }
                ) {
                    Text("测试连接")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Stone200, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
                maxLines = 1
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "从未"
    return try {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(ms))
    } catch (_: Exception) {
        "刚刚"
    }
}