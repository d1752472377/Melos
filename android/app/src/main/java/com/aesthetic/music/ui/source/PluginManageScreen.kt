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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
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
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.settings.SettingsDivider
import com.aesthetic.music.ui.settings.SettingsGap
import com.aesthetic.music.ui.settings.SettingsPageScaffold
import com.aesthetic.music.ui.settings.SettingsSection
import com.aesthetic.music.ui.settings.SettingsSwitchRow
import com.aesthetic.music.ui.theme.AccentAmber
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.AccentRose
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class SourceActions(
    val importFromContent: (content: String, targetPlatform: String) -> Unit,
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
            onActivate = actions.activate,
            onReload = actions.reload,
            onToggleEnable = actions.setEnable,
            onPickPlatform = { platformSheetPlugin = it },
            onDetectTarget = actions.detectOne,
            onDelete = { pendingDelete = it },
        )
    }

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
    val enabledPlugins = plugins.filter { it.enable }
    val activePlugin = enabledPlugins.firstOrNull { it.id == activeId } ?: enabledPlugins.firstOrNull()
    val running = enabledPlugins.firstOrNull { it.id == loadedPluginId }

    val title = when {
        userPrefs.sourceFastest && enabledPlugins.isNotEmpty() -> "自动最快"
        activePlugin != null -> activePlugin.name
        else -> "内置直连"
    }
    val explain = when {
        enabledPlugins.isEmpty() -> "未启用插件，播放使用各平台内置直连"
        userPrefs.sourceFastest -> "已开启自动最快，将按实测速度在 ${enabledPlugins.size} 个启用插件中挑选"
        userPrefs.sourceSmartSwitch -> "已开启智能切换，失败或偏慢的源会被跳过"
        activePlugin != null -> "已手动指定该插件"
        else -> "未指定插件，按最近使用顺序尝试"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
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
                    engineReady && running != null -> "运行中"
                    enabledPlugins.isEmpty() -> "内置直连"
                    else -> "未加载"
                },
                color = when {
                    engineReady && running != null -> AccentMint
                    enabledPlugins.isEmpty() -> AccentCyan
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
                accent = MaterialTheme.colorScheme.primary,
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
                    color = if (isSelected) MaterialTheme.colorScheme.primary else TextSoft,
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
                    color = if (isSelected) MaterialTheme.colorScheme.primary else TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    StatusBadge(text = "当前", color = MaterialTheme.colorScheme.primary)
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
    icon: ImageVector,
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
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

// ==================== 音源卡片列表 ====================

@Composable
private fun SourceListSection(
    plugins: List<SourcePlugin>,
    probes: Map<String, SourceProbe>,
    activeId: String?,
    loadedPluginId: String?,
    detecting: Boolean,
    onActivate: (String) -> Unit,
    onReload: () -> Unit,
    onToggleEnable: (String, Boolean) -> Unit,
    onPickPlatform: (SourcePlugin) -> Unit,
    onDetectTarget: (String) -> Unit,
    onDelete: (SourcePlugin) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "已安装音源插件（${plugins.size}）",
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = TextSoft,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )

        if (plugins.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surface)
                    .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂未安装任何音源插件",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextMuted,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                plugins.forEach { plugin ->
                    PluginCard(
                        plugin = plugin,
                        probes = probes,
                        isActive = plugin.id == activeId,
                        isRunning = plugin.id == loadedPluginId,
                        detecting = detecting,
                        onActivate = { onActivate(plugin.id) },
                        onReload = onReload,
                        onToggleEnable = { onToggleEnable(plugin.id, it) },
                        onPickPlatform = { onPickPlatform(plugin) },
                        onDetectAllPlatforms = {
                            val platforms = plugin.declaredPlatforms.ifEmpty { SourcePlatform.builtins }
                            platforms.forEach { platform ->
                                val targetId = SourceProbe.pluginTarget(plugin.id, platform)
                                onDetectTarget(targetId)
                            }
                        },
                        onDelete = { onDelete(plugin) },
                    )
                }
            }
        }
    }
}

/**
 * 插件卡片与图 3 下拉菜单
 */
@Composable
private fun PluginCard(
    plugin: SourcePlugin,
    probes: Map<String, SourceProbe>,
    isActive: Boolean,
    isRunning: Boolean,
    detecting: Boolean,
    onActivate: () -> Unit,
    onReload: () -> Unit,
    onToggleEnable: (Boolean) -> Unit,
    onPickPlatform: () -> Unit,
    onDetectAllPlatforms: () -> Unit,
    onDelete: () -> Unit,
) {
    val platforms = plugin.declaredPlatforms.ifEmpty { SourcePlatform.builtins }
    var menuExpanded by remember { mutableStateOf(false) }

    // 1. 卡片外层：直接使用你们全局统一的 iosGlassCard 玻璃拟态修饰符！
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 4.dp)
            .padding(16.dp),
    ) {
        // 顶部行：左侧插件图标 + 中间标题与状态 + 右侧统一开关与菜单
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryBrown.copy(alpha = 0.1f)), // 对齐设置页统一透明度
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Extension,
                    contentDescription = null,
                    tint = PrimaryBrown,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val (statusColor, statusText) = when {
                        isRunning -> AccentMint to "运行中"
                        isActive -> PrimaryBrown to "当前指定"
                        plugin.enable -> AccentCyan to "已启用"
                        else -> TextMuted to "已停用"
                    }

                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = statusColor,
                    )

                    Text(text = "·", color = TextMuted, fontSize = 11.sp)
                    Text(
                        text = "v${plugin.version}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                    )

                    if (plugin.author.isNotBlank()) {
                        Text(text = "·", color = TextMuted, fontSize = 11.sp)
                        Text(
                            text = plugin.author,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // 2. 开关颜色：直接取自你发出来的 SettingsSwitchRow 源码配置！
            Switch(
                checked = plugin.enable,
                onCheckedChange = onToggleEnable,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryBrown,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Stone200,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )

            // 右侧更多操作按钮
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "更多选项",
                        tint = TextSoft,
                        modifier = Modifier.size(20.dp),
                    )
                }

                MaterialTheme(
                    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
                ) {
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        offset = DpOffset(x = (-8).dp, y = 4.dp),
                        modifier = Modifier
                            .background(Surface)
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                    ) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            },
                            text = { Text("检测更新", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                if (isRunning) onReload() else onActivate()
                            },
                        )

                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.FilterAlt, // 或 Icons.Filled.FilterList
                                    contentDescription = null,
                                    tint = TextSoft,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            text = { Text("指定平台...", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                onPickPlatform()
                            },
                        )

                        // 分割线（对齐你的 SettingsDivider 风格）
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.8.dp,
                            color = Stone200.copy(alpha = 0.6f),
                        )

                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = AccentRose, modifier = Modifier.size(18.dp))
                            },
                            text = { Text("移除插件...", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = AccentRose) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }

        // 平台状态水平展示
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            platforms.take(5).forEach { platform ->
                val targetId = SourceProbe.pluginTarget(plugin.id, platform)
                val probe = probes[targetId]
                val status = probe?.status ?: ProbeStatus.UNKNOWN
                val dotColor = statusColor(status)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Text(
                        text = platform.label.replace("音乐", "").trim(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                    )
                }
            }
        }

        // 底部居中的【检测】按钮
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Stone100)
                .clickable(enabled = !detecting, onClick = onDetectAllPlatforms)
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (detecting) "检测中…" else "检测",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = AccentCyan,
                )
            }
        }
    }
}

// ==================== 通用辅助组件 ====================

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
private fun OutlineAction(
    text: String,
    icon: ImageVector,
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
            .background(if (enabled) MaterialTheme.colorScheme.primary else Stone200)
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

private fun readTextFromUri(context: Context, uri: android.net.Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readBytes()
        if (bytes.size > 4 * 1024 * 1024) null else bytes.toString(Charsets.UTF_8)
    }
} catch (e: Exception) {
    null
}

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