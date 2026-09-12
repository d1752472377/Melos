package com.aesthetic.music.ui.source

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.settings.UserPrefs
import com.aesthetic.music.data.source.DetectTarget
import com.aesthetic.music.data.source.ProbeStatus
import com.aesthetic.music.data.source.SourcePlatform
import com.aesthetic.music.data.source.SourcePlugin
import com.aesthetic.music.data.source.SourceProbe
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.settings.SettingsDivider
import com.aesthetic.music.ui.settings.SettingsGap
import com.aesthetic.music.ui.settings.SettingsNote
import com.aesthetic.music.ui.settings.SettingsPageScaffold
import com.aesthetic.music.ui.settings.SettingsSection
import com.aesthetic.music.ui.settings.SettingsSwitchRow
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.AccentRose
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.SurfaceSoft
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 插件管理页的动作集合。
 *
 * 用数据类而不是十几个回调参数：页面有 11 个动作，直接铺在函数签名里会让"哪个回调是这个按钮的"
 * 只能靠位置记忆，改动时极易错位。
 */
data class SourceActions(
    /** 从文件导入（内容已由页面读出） */
    val importFromContent: (content: String, targetPlatform: String) -> Unit,
    /** 从链接导入 */
    val importFromUrl: (url: String, targetPlatform: String, onFinished: (Boolean, String) -> Unit) -> Unit,
    val setEnable: (id: String, enable: Boolean) -> Unit,
    val setPlatform: (id: String, platformCode: String) -> Unit,
    val activate: (id: String) -> Unit,
    val reload: () -> Unit,
    val unload: () -> Unit,
    val remove: (id: String) -> Unit,
    val detectAll: () -> Unit,
    val detectOne: (targetId: String) -> Unit,
    val setSourceFailover: (Boolean) -> Unit,
    val setSourceSmartSwitch: (Boolean) -> Unit,
    val setSourceFastest: (Boolean) -> Unit,
)

/**
 * 插件管理（音源管理）页。
 *
 * 页面结构：当前音源总览 → 导入脚本（文件 / 链接，可指定平台）→ 切换策略三开关 →
 * 音源列表（内置直连 + 已安装插件）→ 检测结果（真实请求的正确性与耗时）→ 说明。
 *
 * 设计原则：**所有状态与结论都来自真实数据**。检测不存在的平台就写"无法自动检测"，
 * 脚本没加载成功就写失败原因，而不是给一个好看的绿勾。
 */
@Composable
fun PluginManageScreen(
    plugins: List<SourcePlugin>,
    probes: Map<String, SourceProbe>,
    targets: List<DetectTarget>,
    detecting: Boolean,
    detectProgress: Pair<Int, Int>,
    lastDetectAt: Long,
    activeId: String?,
    loadedPluginId: String?,
    engineReady: Boolean,
    engineError: String?,
    userPrefs: UserPrefs,
    actions: SourceActions,
    onToast: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var importPlatform by remember { mutableStateOf("") }
    var showUrlSheet by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var importingUrl by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SourcePlugin?>(null) }
    var showUnloadConfirm by remember { mutableStateOf(false) }
    var platformSheetPlugin by remember { mutableStateOf<SourcePlugin?>(null) }

    // 系统文件选择器：读文本交给页面（只有它拿得到 ContentResolver），登记与加载仍在控制器
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = readTextFromUri(context, uri)
        if (text.isNullOrBlank()) {
            onToast("读取文件失败，请换一个文件或用「从连接导入」")
        } else {
            actions.importFromContent(text, importPlatform)
        }
    }

    SettingsPageScaffold(
        title = "插件管理",
        subtitle = "音源导入 · 检测 · 切换策略",
        onBack = onBack,
    ) {
        CurrentSourceCard(
            plugins = plugins,
            activeId = activeId,
            loadedPluginId = loadedPluginId,
            engineReady = engineReady,
            engineError = engineError,
            userPrefs = userPrefs,
            lastDetectAt = lastDetectAt,
            targetCount = targets.size,
            detecting = detecting,
            onReload = actions.reload,
            onDetectAll = actions.detectAll,
            onUnload = { showUnloadConfirm = true },
        )

        SettingsGap()

        ImportSection(
            importPlatform = importPlatform,
            onPlatformChange = { importPlatform = it },
            onPickFile = { filePicker.launch(arrayOf("*/*")) },
            onImportUrl = { showUrlSheet = true },
        )

        SettingsGap()

        PolicySection(
            userPrefs = userPrefs,
            onFailoverChange = actions.setSourceFailover,
            onSmartSwitchChange = actions.setSourceSmartSwitch,
            onFastestChange = actions.setSourceFastest,
        )

        SettingsGap()

        SourceListSection(
            plugins = plugins,
            probes = probes,
            activeId = activeId,
            loadedPluginId = loadedPluginId,
            detecting = detecting,
            onDetectBuiltin = actions.detectOne,
            onActivate = actions.activate,
            onToggleEnable = actions.setEnable,
            onPickPlatform = { platformSheetPlugin = it },
            onDetectPlugin = actions.detectOne,
            onDelete = { pendingDelete = it },
        )

        SettingsGap()

        DetectSection(
            targets = targets,
            probes = probes,
            detecting = detecting,
            detectProgress = detectProgress,
            lastDetectAt = lastDetectAt,
            onDetectAll = actions.detectAll,
            onDetectOne = actions.detectOne,
        )

        SettingsGap()

        SettingsSection(title = "关于音源") {
            Column(Modifier.padding(16.dp)) {
                SettingsNote(
                    "· 音源插件是 LX Music 规范的 JS 脚本，在本机 QuickJS 沙箱里执行，" +
                        "由脚本自己去请求各平台的公开接口，不上传任何账号信息。\n" +
                        "· 导入后可以「指定平台」：指定后只在播放该平台歌曲时使用它，适合一个平台配一个源。\n" +
                        "· 检测会对每个源发起真实请求（搜索 + 解析 + 地址可访问性），" +
                        "耗时是那一次的真实往返时间，网络不同结果会不同。\n" +
                        "· 咪咕等平台没有内置搜索入口，脚本声明支持也无法自动取证，" +
                        "这类源会显示「无法自动检测」，以实际播放结果为准。"
                )
            }
        }
    }

    // ==================== 抽屉与弹窗 ====================

    if (showUrlSheet) {
        AppBottomSheet(onDismissRequest = { if (!importingUrl) showUrlSheet = false }) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "从连接导入",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "支持脚本直链（raw 链接、订阅地址）。导入后立即生效。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("脚本链接") },
                    placeholder = { Text("https://example.com/source.js") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                PlatformPickerRow(
                    selected = importPlatform,
                    onSelect = { importPlatform = it },
                )
                Spacer(Modifier.height(14.dp))
                SheetPrimaryButton(
                    text = if (importingUrl) "导入中…" else "导入",
                    enabled = !importingUrl && urlInput.isNotBlank(),
                    onClick = {
                        importingUrl = true
                        actions.importFromUrl(urlInput, importPlatform) { ok, message ->
                            importingUrl = false
                            if (ok) {
                                showUrlSheet = false
                                urlInput = ""
                            } else {
                                onToast(message)
                            }
                        }
                    },
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    platformSheetPlugin?.let { plugin ->
        AppBottomSheet(onDismissRequest = { platformSheetPlugin = null }) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "指定平台 · ${plugin.name}",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "指定后，这个插件只在播放该平台歌曲时参与解析；选「全平台通用」则任何平台都可使用。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                )
                Spacer(Modifier.height(10.dp))
                PlatformOptionsList(
                    selected = plugin.targetPlatform,
                    onSelect = { code ->
                        actions.setPlatform(plugin.id, code)
                        platformSheetPlugin = null
                    },
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    pendingDelete?.let { plugin ->
        AppConfirmDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除插件",
            message = "将删除「${plugin.name}」的脚本与配置，删除后需要重新导入。确定继续吗？",
            confirmText = "删除",
            onConfirm = {
                actions.remove(plugin.id)
                pendingDelete = null
            },
        )
    }

    if (showUnloadConfirm) {
        AppConfirmDialog(
            onDismissRequest = { showUnloadConfirm = false },
            title = "停止使用音源脚本",
            message = "脚本会从沙箱中卸载，播放将回落到内置直连（音质可能下降）。插件本身不会被删除。",
            confirmText = "停止使用",
            destructive = false,
            onConfirm = {
                actions.unload()
                showUnloadConfirm = false
            },
        )
    }
}

// ==================== 当前音源总览 ====================

@Composable
private fun CurrentSourceCard(
    plugins: List<SourcePlugin>,
    activeId: String?,
    loadedPluginId: String?,
    engineReady: Boolean,
    engineError: String?,
    userPrefs: UserPrefs,
    lastDetectAt: Long,
    targetCount: Int,
    detecting: Boolean,
    onReload: () -> Unit,
    onDetectAll: () -> Unit,
    onUnload: () -> Unit,
) {
    val activePlugin = plugins.firstOrNull { it.id == activeId }
    val running = plugins.firstOrNull { it.id == loadedPluginId }

    val title = when {
        userPrefs.sourceFastest && plugins.isNotEmpty() -> "自动最快"
        activePlugin != null -> activePlugin.name
        plugins.isNotEmpty() -> plugins.first().name
        else -> "内置直连"
    }
    val explain = when {
        plugins.isEmpty() -> "未安装插件，播放使用各平台内置直连"
        userPrefs.sourceFastest -> "已开启自动最快，将按实测速度在 ${plugins.count { it.enable }} 个启用插件中挑选"
        userPrefs.sourceSmartSwitch -> "已开启智能切换，失败或偏慢的源会被跳过"
        activePlugin != null -> "已手动指定该插件"
        else -> "未指定插件，按最近使用顺序尝试"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, Stone200, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "当前音源",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = explain,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = TextSoft,
                )
            }
            Spacer(Modifier.width(10.dp))
            StatusBadge(
                text = when {
                    engineReady -> "运行中"
                    plugins.isEmpty() -> "内置直连"
                    else -> "未加载"
                },
                color = when {
                    engineReady -> AccentMint
                    plugins.isEmpty() -> AccentCyan
                    else -> AccentAmber
                },
            )
        }

        if (!engineError.isNullOrBlank() && plugins.isNotEmpty() && !engineReady) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = AccentRose,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = engineError,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = AccentRose,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = buildString {
                append("已安装 ${plugins.size} 个插件 · 启用 ${plugins.count { it.enable }} 个")
                append(" · ")
                append(if (lastDetectAt > 0) "上次检测 ${relativeTime(lastDetectAt)}" else "尚未检测")
                if (running != null && activePlugin != null && running.id != activePlugin.id) {
                    append(" · 沙箱中：${running.name}")
                }
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextMuted,
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineAction(
                text = "重新加载",
                icon = Icons.Filled.Refresh,
                enabled = plugins.isNotEmpty(),
                onClick = onReload,
                modifier = Modifier.weight(1f),
            )
            OutlineAction(
                text = if (detecting) "检测中…" else "全部检测",
                icon = Icons.Filled.Bolt,
                enabled = !detecting,
                onClick = onDetectAll,
                modifier = Modifier.weight(1f),
                accent = PrimaryBrown,
            )
        }
        if (plugins.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "停止使用音源脚本",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = TextMuted,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onUnload)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

// ==================== 导入 ====================

@Composable
private fun ImportSection(
    importPlatform: String,
    onPlatformChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onImportUrl: () -> Unit,
) {
    SettingsSection(title = "导入音源插件") {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "可先指定这个插件的适用平台，再选择导入方式。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
            )
            Spacer(Modifier.height(10.dp))
            PlatformPickerRow(selected = importPlatform, onSelect = onPlatformChange)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ImportButton(
                    title = "从文件导入",
                    subtitle = "选择 .js 文件",
                    icon = Icons.Filled.Description,
                    modifier = Modifier.weight(1f),
                    onClick = onPickFile,
                )
                ImportButton(
                    title = "从连接导入",
                    subtitle = "填写脚本直链",
                    icon = Icons.Filled.Link,
                    modifier = Modifier.weight(1f),
                    onClick = onImportUrl,
                )
            }
        }
    }
}

@Composable
private fun PlatformPickerRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Stone100)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SourceController.platformOptions.forEach { (code, label) ->
            val isSelected = code == selected
            val text = if (code.isEmpty()) "全平台" else label.replace("音乐", "").trim()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Surface else Color.Transparent)
                    .clickable { onSelect(code) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isSelected) PrimaryBrown else TextSoft,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PlatformOptionsList(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SourceController.platformOptions.forEach { (code, label) ->
            val isSelected = code == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Stone100 else Color.Transparent)
                    .clickable { onSelect(code) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isSelected) PrimaryBrown else TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    StatusBadge(text = "当前", color = PrimaryBrown)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "咪咕等无内置搜索的平台不在列表中：应用无法主动搜索该平台歌曲，" +
                "脚本若声明支持它，播放该平台歌曲时依然会被使用。",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = TextMuted,
        )
    }
}

@Composable
private fun ImportButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Stone100)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(PrimaryBrown.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryBrown, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextSoft,
            )
        }
    }
}

// ==================== 切换策略 ====================

@Composable
private fun PolicySection(
    userPrefs: UserPrefs,
    onFailoverChange: (Boolean) -> Unit,
    onSmartSwitchChange: (Boolean) -> Unit,
    onFastestChange: (Boolean) -> Unit,
) {
    SettingsSection(title = "切换策略") {
        SettingsSwitchRow(
            icon = Icons.Filled.SwapHoriz,
            title = "失败换源",
            subtitle = "当前源解析失败时，自动按顺序尝试下一个音源；关闭后只用当前源",
            checked = userPrefs.sourceFailover,
            onCheckedChange = onFailoverChange,
        )
        SettingsDivider()
        SettingsSwitchRow(
            icon = Icons.Filled.Tune,
            title = "智能切换",
            subtitle = "不等失败：最近检测或播放实测中被判定为失败/偏慢的源会被直接跳过",
            checked = userPrefs.sourceSmartSwitch,
            onCheckedChange = onSmartSwitchChange,
            iconTint = AccentCyan,
        )
        SettingsDivider()
        SettingsSwitchRow(
            icon = Icons.Filled.Speed,
            title = "自动按照最快的进行使用",
            subtitle = "按实测响应速度给音源排序，永远优先用当前最快的那个（检测与播放都会更新速度数据）",
            checked = userPrefs.sourceFastest,
            onCheckedChange = onFastestChange,
            iconTint = AccentMint,
        )
    }
}

// ==================== 音源列表 ====================

@Composable
private fun SourceListSection(
    plugins: List<SourcePlugin>,
    probes: Map<String, SourceProbe>,
    activeId: String?,
    loadedPluginId: String?,
    detecting: Boolean,
    onDetectBuiltin: (String) -> Unit,
    onActivate: (String) -> Unit,
    onToggleEnable: (String, Boolean) -> Unit,
    onPickPlatform: (SourcePlugin) -> Unit,
    onDetectPlugin: (String) -> Unit,
    onDelete: (SourcePlugin) -> Unit,
) {
    SettingsSection(title = "音源列表 · 内置直连") {
        SourcePlatform.builtins.forEachIndexed { index, platform ->
            if (index > 0) SettingsDivider()
            val probe = probes[SourceProbe.builtinTarget(platform)]
            BuiltinSourceRow(
                platform = platform,
                probe = probe,
                enabled = !detecting,
                onDetect = { onDetectBuiltin(SourceProbe.builtinTarget(platform)) },
            )
        }
    }

    SettingsGap(10)

    SettingsSection(title = "音源列表 · 已安装插件（${plugins.size}）") {
        if (plugins.isEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "还没有安装插件",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "在上方「导入音源插件」里选择文件或填写链接即可导入。内置直连在没有插件时也能播放，但音质与可用性通常不如插件源。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = TextSoft,
                )
            }
        } else {
            plugins.forEachIndexed { index, plugin ->
                if (index > 0) SettingsDivider()
                PluginRow(
                    plugin = plugin,
                    probe = probeFor(plugin, probes),
                    isActive = plugin.id == activeId,
                    isRunning = plugin.id == loadedPluginId,
                    detecting = detecting,
                    onActivate = { onActivate(plugin.id) },
                    onToggleEnable = { onToggleEnable(plugin.id, it) },
                    onPickPlatform = { onPickPlatform(plugin) },
                    onDetect = { onDetectPlugin(pluginProbeTarget(plugin, probes)) },
                    onDelete = { onDelete(plugin) },
                )
            }
        }
    }
}

/** 插件的检测目标 id：优先取已存在的声明平台，取不到则退回全平台通用（该插件未加载时） */
private fun pluginProbeTarget(plugin: SourcePlugin, probes: Map<String, SourceProbe>): String {
    val declared = plugin.declaredPlatforms
    val platforms = declared.ifEmpty { SourcePlatform.builtins }
    // 优先挑一个"已经有检测结果"的平台，重测时指向用户刚看到的那一行
    val existing = platforms.firstOrNull { probes.containsKey(SourceProbe.pluginTarget(plugin.id, it)) }
    return SourceProbe.pluginTarget(plugin.id, existing ?: platforms.first())
}

/** 插件在界面上的速度展示：优先展示"已检测过的那个平台"的结论 */
private fun probeFor(plugin: SourcePlugin, probes: Map<String, SourceProbe>): SourceProbe? {
    val platforms = plugin.declaredPlatforms.ifEmpty { SourcePlatform.builtins }
    return platforms.mapNotNull { probes[SourceProbe.pluginTarget(plugin.id, it)] }
        .maxByOrNull { it.checkedAt }
}

@Composable
private fun BuiltinSourceRow(
    platform: SourcePlatform,
    probe: SourceProbe?,
    enabled: Boolean,
    onDetect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AccentCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = platform.label.take(2),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = AccentCyan,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = platform.label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = "内置直连：搜索与兜底播放，无需插件即可使用",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
            )
        }
        Spacer(Modifier.width(8.dp))
        ProbeBadge(probe)
        Spacer(Modifier.width(8.dp))
        MiniAction(text = "检测", enabled = enabled, onClick = onDetect)
    }
}

@Composable
private fun PluginRow(
    plugin: SourcePlugin,
    probe: SourceProbe?,
    isActive: Boolean,
    isRunning: Boolean,
    detecting: Boolean,
    onActivate: () -> Unit,
    onToggleEnable: (Boolean) -> Unit,
    onPickPlatform: () -> Unit,
    onDetect: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "v${plugin.version}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextSoft,
                    )
                    if (isRunning) {
                        Spacer(Modifier.width(6.dp))
                        StatusBadge(text = "运行中", color = AccentMint)
                    } else if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        StatusBadge(text = "当前指定", color = PrimaryBrown)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(plugin.targetLabel)
                        val declared = plugin.declaredPlatforms
                        if (declared.isNotEmpty()) {
                            append(" · 支持 ")
                            append(declared.joinToString("/") { it.label.replace("音乐", "") })
                        } else {
                            append(" · 支持平台待加载确认")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                )
                if (plugin.author.isNotBlank() || plugin.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = listOfNotNull(
                            plugin.author.takeIf { it.isNotBlank() }?.let { "@$it" },
                            plugin.description.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextMuted,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Switch(
                checked = plugin.enable,
                onCheckedChange = onToggleEnable,
                modifier = Modifier.size(width = 44.dp, height = 26.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        // 检测徽章与操作按钮分两行：四个按钮加徽章挤在一行时，窄屏上会被压成一排省略号
        if (probe != null && probe.status != ProbeStatus.UNKNOWN) {
            ProbeBadge(probe)
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiniAction(text = "检测", enabled = !detecting, onClick = onDetect)
            MiniAction(text = "指定平台", enabled = true, onClick = onPickPlatform)
            // 正在运行的插件这一项变成"重新加载"：脚本改过或沙箱异常时，行内就能重载
            MiniAction(text = if (isRunning) "重新加载" else "设为当前", enabled = true, onClick = onActivate)
            MiniAction(text = "删除", enabled = true, onClick = onDelete, danger = true, icon = Icons.Filled.Delete)
        }
    }
}

// ==================== 检测 ====================

@Composable
private fun DetectSection(
    targets: List<DetectTarget>,
    probes: Map<String, SourceProbe>,
    detecting: Boolean,
    detectProgress: Pair<Int, Int>,
    lastDetectAt: Long,
    onDetectAll: () -> Unit,
    onDetectOne: (String) -> Unit,
) {
    val done = detectProgress.first
    val total = detectProgress.second
    val okCount = targets.count { probes[it.id]?.status == ProbeStatus.OK }
    val failCount = targets.count { probes[it.id]?.status == ProbeStatus.FAIL }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "检测结果",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = TextSoft,
                modifier = Modifier.weight(1f),
            )
            MiniAction(
                text = if (detecting) "检测中 $done/$total" else "全部检测",
                enabled = !detecting,
                onClick = onDetectAll,
                accent = PrimaryBrown,
            )
        }
        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Stone200, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Surface),
        ) {
            // 进度条：检测要跑好几秒，明确告知进度比转圈更让人放心
            if (detecting) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Stone100),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (total > 0) done.toFloat() / total else 0f)
                            .height(3.dp)
                            .background(PrimaryBrown),
                    )
                }
            }

            if (targets.isEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "暂无可检测的音源",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "导入插件后会为每个插件声明的平台各生成一项检测。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                    )
                }
            } else {
                targets.forEachIndexed { index, target ->
                    if (index > 0) SettingsDivider()
                    DetectRow(
                        target = target,
                        probe = probes[target.id],
                        enabled = !detecting,
                        onDetect = { onDetectOne(target.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = buildString {
                append("检测方式：对每个源发真实请求（平台搜索 → 脚本解析直链 → 校验地址可访问性），")
                append("耗时即该次真实往返时间。")
                if (lastDetectAt > 0) append("上次检测：${relativeTime(lastDetectAt)}。")
                if (okCount + failCount > 0) {
                    append("当前已测 ${okCount + failCount} 项，其中正常 $okCount、失败 $failCount。")
                }
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun DetectRow(
    target: DetectTarget,
    probe: SourceProbe?,
    enabled: Boolean,
    onDetect: () -> Unit,
) {
    val status = probe?.status ?: ProbeStatus.UNKNOWN
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = target.label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
            )
            if (!probe?.detail.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = probe.detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = when (status) {
                        ProbeStatus.FAIL -> AccentRose
                        ProbeStatus.SLOW -> AccentAmber
                        ProbeStatus.UNTESTABLE -> TextSoft
                        else -> TextMuted
                    },
                    maxLines = 3,
                )
            } else {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "尚未检测",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            StatusBadge(text = status.label, color = statusColor(status))
            if (probe != null && probe.latencyMs >= 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = probe.latencyLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextSoft,
                )
            }
            if (probe != null && probe.observedCount > 0 && probe.observedMs >= 0) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = "播放实测 ${probe.observedMs}ms",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextMuted,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        MiniAction(text = "重测", enabled = enabled, onClick = onDetect)
    }
}

// ==================== 小组件 ====================

@Composable
private fun ProbeBadge(probe: SourceProbe?) {
    val status = probe?.status ?: ProbeStatus.UNKNOWN
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusBadge(text = status.label, color = statusColor(status))
        if (probe != null && probe.latencyMs >= 0) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = probe.latencyLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextSoft,
            )
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun MiniAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Color = TextSoft,
    danger: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val color = when {
        !enabled -> TextMuted.copy(alpha = 0.5f)
        danger -> AccentRose
        else -> accent
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Stone100)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun OutlineAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = TextMuted,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) accent.copy(alpha = 0.10f) else Stone100)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) accent else TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = if (enabled) accent else TextMuted.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SheetPrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) PrimaryBrown else Stone200)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

@Composable
private fun statusColor(status: ProbeStatus): Color = when (status) {
    ProbeStatus.OK -> AccentMint
    ProbeStatus.SLOW -> AccentAmber
    ProbeStatus.FAIL -> AccentRose
    ProbeStatus.UNTESTABLE -> AccentCyan
    ProbeStatus.UNKNOWN -> TextSoft
}

// ==================== 工具 ====================

private fun readTextFromUri(context: Context, uri: android.net.Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readBytes()
        if (bytes.size > 4 * 1024 * 1024) null else bytes.toString(Charsets.UTF_8)
    }
} catch (e: Exception) {
    null
}

/** "3 分钟前"这种相对时间：检测时间点用绝对时间没意义，用户关心的是"多久以前测的" */
private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            fmt.format(timestamp)
        }
        else -> {
            val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
            val now = Calendar.getInstance()
            val days = ((now.timeInMillis - timestamp) / 86_400_000L).toInt()
            if (days == 1) "昨天" else "${calendar.get(Calendar.MONTH) + 1}月${calendar.get(Calendar.DAY_OF_MONTH)}日"
        }
    }
}
