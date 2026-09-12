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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.settings.ScriptCatalogEntry
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.data.settings.UserPrefs
import com.aesthetic.music.data.userapi.ScriptMetadata
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.AppDialog
import com.aesthetic.music.ui.components.LocalBottomBarPadding
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
        ThemeMode.SYSTEM -> "自动（跟随系统）"
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
    }

/** 设置主页可跳转的子页面 */
enum class SettingsPage {
    APPEARANCE, QUALITY, PLAYBACK, LYRICS, MEDIA_CARD, DOWNLOAD, CACHE
}

/**
 * 设置主页。
 *
 * 结构：常用设置（7 个入口，各自独立成页）→ 音源脚本 / 云同步 → 系统与交互 → 关于。
 * 设置项本身不再堆在本页，避免"一页滚到底找开关"；本页只显示每项的当前状态摘要。
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    quality: QualityLevel,
    userPrefs: UserPrefs = UserPrefs(),
    floatingCapsuleEnabled: Boolean,
    activeScript: ScriptMetadata? = null,
    scriptCatalog: List<ScriptCatalogEntry> = emptyList(),
    isEngineReady: Boolean = false,
    engineError: String? = null,
    onRetryLoadScript: () -> Unit = {},
    onSwitchScript: (String) -> Unit = {},
    isSyncConfigured: Boolean = false,
    syncServerUrl: String = "",
    syncToken: String = "",
    lastSyncedAt: Long = 0L,
    lastSyncStatus: String? = null,
    isSyncing: Boolean = false,
    onBack: () -> Unit,
    onOpenPage: (SettingsPage) -> Unit = {},
    onFloatingCapsuleChange: (Boolean) -> Unit,
    onCheckUpdate: (onResult: (String) -> Unit) -> Unit,
    onSaveSyncConfig: (String, String) -> Unit = { _, _ -> },
    onTestSyncConnection: ((Boolean, String) -> Unit) -> Unit = {},
    onPerformSync: () -> Unit = {},
    onImportScriptText: (String, String?) -> Unit = { _, _ -> },
    onImportScriptUrl: (String, (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onUnloadScript: () -> Unit = {},
) {
    var showScriptImportDialog by remember { mutableStateOf(false) }
    var showSyncConfigDialog by remember { mutableStateOf(false) }
    var showUnloadConfirm by remember { mutableStateOf(false) }
    var showCatalogDialog by remember { mutableStateOf(false) }

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
            .padding(bottom = LocalBottomBarPadding.current),
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
                text = "设置",
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isEngineReady) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AccentMint.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "🟢 就绪",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = AccentMint
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "🔴 异常",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PrimaryBrown.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v${activeScript.version}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = PrimaryBrown
                                )
                            }
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
                    if (activeScript.author.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "作者: ${activeScript.author}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextMuted
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "支持平台: ${activeScript.supportedSources.joinToString(", ")} · QuickJS 沙箱解析",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextMuted
                    )
                    if (!isEngineReady && engineError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "沙箱报错: $engineError",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (!isEngineReady) {
                    SettingRow(
                        icon = Icons.Filled.Refresh,
                        iconTint = PrimaryBrown,
                        label = "🔄 重新在沙箱中加载脚本",
                        subtitle = "点击重新初始化并执行已保存的音源脚本",
                        onClick = onRetryLoadScript
                    )
                }

                SettingRow(
                    icon = Icons.Filled.Code,
                    iconTint = PrimaryBrown,
                    label = "导入 / 替换音源脚本",
                    subtitle = "可粘贴 JS 完整代码或输入订阅直链",
                    onClick = { showScriptImportDialog = true }
                )
                SettingRow(
                    icon = Icons.Filled.Sync,
                    iconTint = PrimaryBrown,
                    label = "☁️ 从私有云同步音源脚本",
                    subtitle = if (isSyncConfigured) "拉取服务端启用的最新音源并双向同步" else "未连接私有服务（点击配置服务器地址与密钥）",
                    onClick = {
                        if (isSyncConfigured) onPerformSync() else showSyncConfigDialog = true
                    }
                )
                if (scriptCatalog.isNotEmpty()) {
                    SettingRow(
                        icon = Icons.Filled.Widgets,
                        iconTint = PrimaryBrown,
                        label = "🗂 音源脚本目录（${scriptCatalog.size} 个）",
                        subtitle = "本地一键切换已同步的音源，无需回服务端修改",
                        onClick = { showCatalogDialog = true }
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
                SettingRow(
                    icon = Icons.Filled.Sync,
                    iconTint = PrimaryBrown,
                    label = "☁️ 从私有云同步音源脚本与歌单",
                    subtitle = if (isSyncConfigured) "一键获取私有服务器托管的健康音源" else "未连接私有服务（点击配置服务器地址与密钥）",
                    onClick = {
                        if (isSyncConfigured) onPerformSync() else showSyncConfigDialog = true
                    }
                )
                if (scriptCatalog.isNotEmpty()) {
                    SettingRow(
                        icon = Icons.Filled.Widgets,
                        iconTint = PrimaryBrown,
                        label = "🗂 音源脚本目录（${scriptCatalog.size} 个）",
                        subtitle = "本地一键切换已同步的音源，无需回服务端修改",
                        onClick = { showCatalogDialog = true }
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

        // ===== 外观与主题：已独立成「主题与外观」子页面，这里只放入口 =====
        SectionCard(title = "常用设置") {
            SettingRow(
                icon = Icons.Filled.Palette,
                iconTint = PrimaryBrown,
                label = "主题与外观",
                subtitle = "${themeMode.label} · 状态栏${userPrefs.statusBarMode.label}",
                onClick = { onOpenPage(SettingsPage.APPEARANCE) }
            )
            SettingRow(
                icon = Icons.Filled.MusicNote,
                iconTint = PrimaryBrown,
                label = "音质",
                subtitle = quality.label,
                onClick = { onOpenPage(SettingsPage.QUALITY) }
            )
            SettingRow(
                icon = Icons.Filled.PlayCircleOutline,
                iconTint = PrimaryBrown,
                label = "播放设置",
                subtitle = buildString {
                    append(if (userPrefs.preloadNext) "预加载下一首" else "不预加载")
                    if (userPrefs.crossFade) append(" · 淡入淡出")
                    if (userPrefs.mixWithOthers) append(" · 混合播放")
                },
                onClick = { onOpenPage(SettingsPage.PLAYBACK) }
            )
            SettingRow(
                icon = Icons.Filled.Subtitles,
                iconTint = PrimaryBrown,
                label = "歌词显示",
                subtitle = buildString {
                    append(if (userPrefs.lyricTranslation) "翻译歌词" else "不显示翻译")
                    append(if (userPrefs.lyricOnline) " · 在线" else " · 离线")
                    if (userPrefs.lyricLocal) append(" · 本地优先")
                },
                onClick = { onOpenPage(SettingsPage.LYRICS) }
            )
            SettingRow(
                icon = Icons.Filled.Notifications,
                iconTint = PrimaryBrown,
                label = "媒体卡片",
                subtitle = buildString {
                    append(if (userPrefs.mediaCardFavorite) "含收藏按钮" else "仅播放控制")
                    if (userPrefs.mediaCardShowLyric) append(" · 显示歌词")
                },
                onClick = { onOpenPage(SettingsPage.MEDIA_CARD) }
            )
            SettingRow(
                icon = Icons.Filled.CloudDownload,
                iconTint = PrimaryBrown,
                label = "下载设置",
                subtitle = buildString {
                    append(if (userPrefs.downloadWhilePlaying) "边听边下" else "不自动缓存")
                    if (userPrefs.downloadWifiOnly) append(" · 仅 WiFi")
                },
                onClick = { onOpenPage(SettingsPage.DOWNLOAD) }
            )
            SettingRow(
                icon = Icons.Filled.Storage,
                iconTint = PrimaryBrown,
                label = "缓存管理",
                subtitle = "查看缓存占用、按类型清理",
                onClick = { onOpenPage(SettingsPage.CACHE) }
            )
        }

        Spacer(Modifier.height(14.dp))

        // ===== 音源脚本 / 云同步 / 系统与交互 / 关于：保持原有实现 =====
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

        // ===== 播放与音质：音质已独立成子页面，这里只保留桌面胶囊等系统项 =====
        SectionCard(title = "播放与音效") {
            SettingRow(
                icon = Icons.Filled.MusicNote,
                iconTint = PrimaryBrown,
                label = "音质等级",
                subtitle = "${quality.label}（在「音质」页可改）",
                onClick = { onOpenPage(SettingsPage.QUALITY) }
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

    // ===== 音质选择弹窗已移入「音质」子页面（ui/settings/QualitySettingsScreen.kt） =====

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
        AppConfirmDialog(
            onDismissRequest = { showUnloadConfirm = false },
            title = "卸载音源脚本",
            message = "确定要卸载当前音源脚本吗？卸载后在线歌曲将无法直接解析播放。",
            confirmText = "卸载",
            onConfirm = {
                showUnloadConfirm = false
                onUnloadScript()
            },
            destructive = true,
        )
    }

    // ===== 音源脚本目录弹窗（本地切换） =====
    if (showCatalogDialog) {
        ScriptCatalogDialog(
            entries = scriptCatalog,
            activeId = activeScript?.id.orEmpty(),
            onDismiss = { showCatalogDialog = false },
            onSwitch = { id ->
                showCatalogDialog = false
                onSwitchScript(id)
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

    AppDialog(
        onDismissRequest = onDismiss,
        title = "导入音源脚本",
        confirmText = if (downloading) "正在抓取…" else "确认导入",
        confirmEnabled = !downloading,
        content = {
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
        onConfirm = confirm@{
            errorMsg = null
            if (mode == 0) {
                if (scriptText.isBlank()) {
                    errorMsg = "请先粘贴脚本代码"
                    return@confirm
                }
                onImportText(scriptText, customName.ifBlank { null })
            } else {
                if (scriptUrl.isBlank()) {
                    errorMsg = "请输入下载链接"
                    return@confirm
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

    AppDialog(
        onDismissRequest = onDismiss,
        title = "配置私有服务器",
        content = {
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
        actions = {
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
            Button(
                onClick = { onSave(url, token) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown)
            ) {
                Text("保存配置")
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

@Composable
private fun ScriptCatalogDialog(
    entries: List<ScriptCatalogEntry>,
    activeId: String,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = "音源脚本目录",
        confirmText = "关闭",
        onConfirm = onDismiss,
        cancelText = null,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (entries.isEmpty()) {
                    Text(
                        text = "暂无已同步的音源脚本，请先执行一次私有云同步。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSoft
                    )
                } else {
                    entries.forEach { entry ->
                        val isActive = entry.id.isNotBlank() && entry.id == activeId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) Stone100 else Color.Transparent)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = entry.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("v${entry.version}", fontSize = 11.sp, color = TextMuted)
                                }
                                if (entry.description.isNotBlank()) {
                                    Text(
                                        text = entry.description,
                                        fontSize = 11.sp,
                                        color = TextSoft,
                                        maxLines = 1
                                    )
                                }
                                val statusText = when (entry.status.lowercase()) {
                                    "success", "healthy" -> "✅ 测活正常"
                                    "failed", "error" -> "❌ 解析异常"
                                    else -> "未测活"
                                }
                                Text(statusText, fontSize = 11.sp, color = TextMuted)
                            }
                            Spacer(Modifier.width(8.dp))
                            if (isActive) {
                                Text(
                                    text = "正在使用",
                                    fontSize = 11.sp,
                                    color = PrimaryBrown,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                TextButton(onClick = { onSwitch(entry.id) }) {
                                    Text("切换使用", fontSize = 12.sp, color = PrimaryBrown)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}