package com.aesthetic.music.ui.source

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.R
import com.aesthetic.music.data.settings.UserPrefs
import com.aesthetic.music.data.source.PlatformQualityGroup
import com.aesthetic.music.data.source.PluginDetectState
import com.aesthetic.music.data.source.ProbeStatus
import com.aesthetic.music.data.source.QualityProbe
import com.aesthetic.music.data.source.SourcePlatform
import com.aesthetic.music.data.source.SourcePlugin
import com.aesthetic.music.data.source.SourceProbe
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.AppDropdownDivider
import com.aesthetic.music.ui.components.AppDropdownItem
import com.aesthetic.music.ui.components.AppDropdownMenu
import com.aesthetic.music.ui.components.AppSolidButton
import com.aesthetic.music.ui.home.iosGlassCard
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
import java.util.Locale
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

data class SourceActions(
    val importFromContent: (content: String, targetPlatform: String) -> Unit,
    val importFromUri: (Context, android.net.Uri, targetPlatform: String) -> Unit,
    val importFromUrl: (url: String, targetPlatform: String, onFinished: (Boolean, String) -> Unit) -> Unit,
    val setEnable: (id: String, enable: Boolean) -> Unit,
    val setPlatform: (id: String, platformCode: String) -> Unit,
    val activate: (id: String) -> Unit,
    val reload: () -> Unit,
    val unload: () -> Unit,
    val remove: (id: String) -> Unit,
    val refresh: () -> Unit,
    val detectAll: () -> Unit,
    val detectOne: (targetId: String) -> Unit,
    val startPluginDetect: (pluginId: String, quick: Boolean) -> Unit,
    val clearPluginDetect: () -> Unit,
    val setSourceFailover: (Boolean) -> Unit,
    val setSourceSmartSwitch: (Boolean) -> Unit,
    val setSourceFastest: (Boolean) -> Unit,
)

private enum class SortMode(val label: String) {
    Recent("最近使用"),
    Name("名称"),
    Speed("速度"),
}

@Composable
fun PluginManageScreen(
    plugins: List<SourcePlugin>,
    probes: Map<String, SourceProbe>,
    detecting: Boolean,
    activeId: String?,
    loadedPluginId: String?,
    engineReady: Boolean,
    engineError: String?,
    pluginDetect: PluginDetectState?,
    userPrefs: UserPrefs,
    actions: SourceActions,
    onToast: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var importPlatform by remember { mutableStateOf("") }
    var showAddSheet by remember { mutableStateOf(false) }
    var showUrlSheet by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var importingUrl by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SourcePlugin?>(null) }
    var showUnloadConfirm by remember { mutableStateOf(false) }
    var platformSheetPlugin by remember { mutableStateOf<SourcePlugin?>(null) }
    var showStatusSheet by remember { mutableStateOf(false) }
    var showPlaybackSheet by remember { mutableStateOf(false) }
    var detectChoicePlugin by remember { mutableStateOf<SourcePlugin?>(null) }
    var sortMode by remember { mutableStateOf(SortMode.Speed) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var topMenuExpanded by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        actions.importFromUri(context, uri, importPlatform)
        showAddSheet = false
    }

    val sortedPlugins = remember(plugins, probes, sortMode) {
        when (sortMode) {
            SortMode.Recent -> plugins.sortedByDescending { it.lastUsedAt }
            SortMode.Name -> plugins.sortedBy { it.name.lowercase(Locale.getDefault()) }
            SortMode.Speed -> plugins.sortedBy { pluginSpeed(it, probes) }
        }
    }
    val enabledCount = plugins.count { it.enable }
    val running = engineReady && loadedPluginId != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.md),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(24.dp)
                    .clickable(onClick = onBack),
            )
            Text(
                text = "插件管理",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(Modifier.align(Alignment.CenterEnd)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { topMenuExpanded = true }
                        .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("音源", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = TextSoft)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = TextSoft, modifier = Modifier.size(16.dp))
                }
                AppDropdownMenu(
                    expanded = topMenuExpanded,
                    onDismissRequest = { topMenuExpanded = false },
                ) {
                    AppDropdownItem(
                        text = "刷新列表",
                        onClick = {
                            topMenuExpanded = false
                            actions.refresh()
                        },
                    )
                    AppDropdownItem(
                        text = "全部检测",
                        onClick = {
                            topMenuExpanded = false
                            actions.detectAll()
                        },
                    )
                    AppDropdownDivider()
                    AppDropdownItem(
                        text = "停止使用脚本",
                        danger = true,
                        onClick = {
                            topMenuExpanded = false
                            showUnloadConfirm = true
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.page, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "音源插件",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${plugins.size} 个已安装 · $enabledCount 个已启用",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSoft,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryBrown)
                    .clickable { showAddSheet = true }
                    .padding(horizontal = AppSpacing.xl, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(AppSpacing.xxs))
                Text(
                    text = "添加插件",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.page, vertical = AppSpacing.xs)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showStatusSheet = true }
                    .padding(horizontal = AppSpacing.xl, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !engineError.isNullOrBlank() -> AccentRose
                                running -> AccentMint
                                else -> AccentAmber
                            },
                        ),
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    text = when {
                        !engineError.isNullOrBlank() -> "运行异常"
                        running -> "运行正常"
                        else -> "未加载"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
            Box(Modifier.width(1.dp).height(18.dp).background(Stone200))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showPlaybackSheet = true }
                    .padding(horizontal = AppSpacing.xl, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = TextSoft, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "播放设置",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.page, end = AppSpacing.lg, top = AppSpacing.xxl, bottom = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已安装 ${plugins.size}",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序", tint = TextSoft, modifier = Modifier.size(18.dp))
                }
                AppDropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    SortMode.entries.forEach { mode ->
                        AppDropdownItem(
                            text = mode.label,
                            selected = mode == sortMode,
                            onClick = {
                                sortMode = mode
                                sortMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        if (plugins.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(horizontal = AppSpacing.page)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface)
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("还没有音源插件", fontSize = 13.sp, color = TextMuted)
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = AppSpacing.page),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
            ) {
                sortedPlugins.forEach { plugin ->
                    PluginCard(
                        plugin = plugin,
                        probes = probes,
                        isActive = plugin.id == activeId,
                        isRunning = plugin.id == loadedPluginId && engineReady,
                        detecting = detecting || pluginDetect?.running == true,
                        onActivate = { actions.activate(plugin.id) },
                        onToggleEnable = { actions.setEnable(plugin.id, it) },
                        onPickPlatform = { platformSheetPlugin = plugin },
                        onDetect = { detectChoicePlugin = plugin },
                        onDelete = { pendingDelete = plugin },
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showAddSheet) {
        AppBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(Modifier.padding(horizontal = AppSpacing.page)) {
                SheetHeader(title = "添加插件", onClose = { showAddSheet = false })
                Spacer(Modifier.height(AppSpacing.xl))
                Text("适用平台", fontSize = 12.sp, color = TextSoft)
                Spacer(Modifier.height(AppSpacing.xs))
                PlatformPickerRow(selected = importPlatform, onSelect = { importPlatform = it })
                Spacer(Modifier.height(AppSpacing.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    ImportTile(
                        title = "从文件导入",
                        subtitle = "选择 .js 文件",
                        icon = Icons.Filled.Description,
                        modifier = Modifier.weight(1f),
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                    )
                    ImportTile(
                        title = "从连接导入",
                        subtitle = "填写脚本直链",
                        icon = Icons.Filled.Link,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAddSheet = false
                            showUrlSheet = true
                        },
                    )
                }
                Spacer(Modifier.height(AppSpacing.page))
            }
        }
    }

    if (showUrlSheet) {
        AppBottomSheet(onDismissRequest = { if (!importingUrl) showUrlSheet = false }) {
            Column(Modifier.padding(horizontal = AppSpacing.page)) {
                SheetHeader(title = "从连接导入", onClose = { if (!importingUrl) showUrlSheet = false })
                Spacer(Modifier.height(AppSpacing.lg))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("https://example.com/source.js") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Surface,
                        unfocusedContainerColor = Surface,
                        focusedBorderColor = PrimaryBrown,
                        unfocusedBorderColor = CardBorder,
                    ),
                )
                Spacer(Modifier.height(AppSpacing.md))
                PlatformPickerRow(selected = importPlatform, onSelect = { importPlatform = it })
                Spacer(Modifier.height(AppSpacing.xl))
                AppSolidButton(
                    text = if (importingUrl) "导入中…" else "导入",
                    enabled = !importingUrl && urlInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
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
                Spacer(Modifier.height(AppSpacing.page))
            }
        }
    }

    if (showStatusSheet) {
        AppBottomSheet(onDismissRequest = { showStatusSheet = false }) {
            Column(Modifier.padding(horizontal = AppSpacing.page)) {
                SheetHeader(
                    title = "插件运行状态",
                    subtitle = when {
                        !engineError.isNullOrBlank() -> "运行异常"
                        running -> "运行正常"
                        else -> "未加载"
                    },
                    onClose = { showStatusSheet = false },
                )
                Spacer(Modifier.height(AppSpacing.xl))
                Text("运行概览", fontSize = 12.sp, color = TextSoft)
                Spacer(Modifier.height(AppSpacing.xs))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(AppSpacing.xl),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (engineError.isNullOrBlank()) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = if (engineError.isNullOrBlank()) AccentMint else AccentRose,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text(
                            text = engineError ?: if (running) "插件已加载，暂未发现运行异常。" else "插件尚未加载。",
                            fontSize = 12.sp,
                            color = if (engineError.isNullOrBlank()) TextPrimary else AccentRose,
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.xxl))
                    Row(Modifier.fillMaxWidth()) {
                        StatusStat("已启用", enabledCount, Modifier.weight(1f))
                        StatusStat("已加载", if (running) 1 else 0, Modifier.weight(1f))
                        StatusStat("暂时停用", plugins.count { !it.enable }, Modifier.weight(1f))
                        StatusStat("启动失败", if (engineError.isNullOrBlank()) 0 else 1, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    text = "当前播放不受影响；正在取链或检测的任务会中断。",
                    fontSize = 10.5.sp,
                    color = TextMuted,
                )
                Spacer(Modifier.height(AppSpacing.xl))
                AppSolidButton(
                    text = "重新加载",
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    onClick = {
                        actions.reload()
                        showStatusSheet = false
                    },
                )
                Spacer(Modifier.height(AppSpacing.page))
            }
        }
    }

    if (showPlaybackSheet) {
        var policyExpanded by remember { mutableStateOf(false) }
        val mode = when {
            userPrefs.sourceSmartSwitch -> "智能切换"
            userPrefs.sourceFailover -> "失败换源"
            else -> "仅当前源"
        }
        val activePlugin = plugins.firstOrNull { it.id == activeId }

        AppBottomSheet(onDismissRequest = { showPlaybackSheet = false }) {
            Column(Modifier.padding(horizontal = AppSpacing.page)) {
                SheetHeader(title = "播放设置", subtitle = "修改后立即生效", onClose = { showPlaybackSheet = false })
                Spacer(Modifier.height(AppSpacing.xl))
                Text("音源选择", fontSize = 12.sp, color = TextSoft)
                Spacer(Modifier.height(AppSpacing.xs))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                ) {
                    SettingRow(
                        icon = Icons.Filled.SwapHoriz,
                        title = "取链策略",
                        value = mode,
                        onClick = { policyExpanded = !policyExpanded },
                    )
                    if (policyExpanded) {
                        Row(
                            modifier = Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        ) {
                            listOf("仅当前源", "失败换源", "智能切换").forEach { option ->
                                ModeChip(
                                    text = option,
                                    selected = option == mode,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        actions.setSourceFailover(option != "仅当前源")
                                        actions.setSourceSmartSwitch(option == "智能切换")
                                        policyExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = Stone200.copy(alpha = 0.6f))
                    SettingRow(
                        icon = Icons.Filled.FilterAlt,
                        title = "指定平台插件",
                        value = activePlugin?.targetLabel ?: "自动选择",
                        onClick = {
                            val plugin = activePlugin
                            if (plugin == null) {
                                onToast("先在列表里选择一个插件")
                            } else {
                                showPlaybackSheet = false
                                platformSheetPlugin = plugin
                            }
                        },
                    )
                    HorizontalDivider(color = Stone200.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { actions.setSourceFastest(!userPrefs.sourceFastest) }
                            .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = AccentMint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(AppSpacing.lg))
                        Column(Modifier.weight(1f)) {
                            Text("多插件智能切换", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("优先使用取链更快的插件", fontSize = 11.sp, color = TextSoft)
                        }
                        Switch(
                            checked = userPrefs.sourceFastest,
                            onCheckedChange = { actions.setSourceFastest(it) },
                            colors = sourceSwitchColors(),
                        )
                    }
                }
                Spacer(Modifier.height(AppSpacing.page))
            }
        }
    }

    detectChoicePlugin?.let { plugin ->
        AppBottomSheet(onDismissRequest = { detectChoicePlugin = null }) {
            Column(Modifier.padding(horizontal = AppSpacing.page)) {
                SheetHeader(
                    title = "音源检测",
                    subtitle = plugin.name,
                    onClose = { detectChoicePlugin = null },
                )
                Spacer(Modifier.height(AppSpacing.lg))
                DetectChoiceRow(
                    title = "快速检测",
                    subtitle = "检测声明的最高两档音质",
                    icon = Icons.Filled.Bolt,
                    accent = AccentCyan,
                ) {
                    detectChoicePlugin = null
                    actions.startPluginDetect(plugin.id, true)
                }
                Spacer(Modifier.height(AppSpacing.sm))
                DetectChoiceRow(
                    title = "全档检测",
                    subtitle = "检测声明的全部音质",
                    icon = Icons.Filled.Tune,
                    accent = PrimaryBrown,
                ) {
                    detectChoicePlugin = null
                    actions.startPluginDetect(plugin.id, false)
                }
                Spacer(Modifier.height(AppSpacing.sm))
                Text("逐档检测样本，不下载整首歌曲。", fontSize = 10.5.sp, color = TextMuted)
                Spacer(Modifier.height(AppSpacing.xxxl))
            }
        }
    }

    pluginDetect?.let { state ->
        AppBottomSheet(onDismissRequest = { actions.clearPluginDetect() }) {
            Column(Modifier.padding(horizontal = AppSpacing.page)) {
                SheetHeader(
                    title = state.title,
                    subtitle = state.plugin.name,
                    onClose = { actions.clearPluginDetect() },
                )
                Spacer(Modifier.height(AppSpacing.xl))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(AppSpacing.xl),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                state.running -> "正在切换检测项"
                                state.error != null -> "检测中断"
                                else -> "检测完成"
                            },
                            fontSize = 12.sp,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (state.total > 0) "已检测 ${state.done} / ${state.total} 档" else "准备中",
                            fontSize = 10.5.sp,
                            color = TextSoft,
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.md))
                    val progress = if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PrimaryBrown,
                        trackColor = Stone100,
                    )
                }
                Spacer(Modifier.height(AppSpacing.sm))
                Text("样本结果不代表全部歌曲", fontSize = 10.5.sp, color = TextMuted)
                Spacer(Modifier.height(AppSpacing.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "本次样本",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${state.groups.count { it.qualities.isNotEmpty() }} 个平台",
                        fontSize = 10.5.sp,
                        color = TextSoft,
                    )
                }
                Spacer(Modifier.height(AppSpacing.sm))
                val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.groups.filter { it.qualities.isNotEmpty() }.forEach { group ->
                        QualityGroupCard(group)
                        Spacer(Modifier.height(AppSpacing.sm))
                    }
                }
                Spacer(Modifier.height(AppSpacing.xs))
                AppSolidButton(
                    text = if (state.running) "取消检测" else "完成",
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    onClick = { actions.clearPluginDetect() },
                )
                Spacer(Modifier.height(AppSpacing.page))
            }
        }
    }

    platformSheetPlugin?.let { plugin ->
        AppBottomSheet(onDismissRequest = { platformSheetPlugin = null }) {
            Column(Modifier.padding(horizontal = AppSpacing.page)) {
                SheetHeader(
                    title = "适用平台 · ${plugin.name}",
                    onClose = { platformSheetPlugin = null },
                )
                Spacer(Modifier.height(AppSpacing.sm))
                PlatformOptionsList(
                    selected = plugin.targetPlatform,
                    onSelect = { code ->
                        actions.setPlatform(plugin.id, code)
                        platformSheetPlugin = null
                    },
                )
                Spacer(Modifier.height(AppSpacing.page))
            }
        }
    }

    pendingDelete?.let { plugin ->
        AppConfirmDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除插件",
            message = "将删除「${plugin.name}」的脚本与配置。",
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
            message = "播放将回落到内置直连，插件本身不会删除。",
            confirmText = "停止使用",
            destructive = false,
            onConfirm = {
                actions.unload()
                showUnloadConfirm = false
            },
        )
    }
}

@Composable
private fun PluginCard(
    plugin: SourcePlugin,
    probes: Map<String, SourceProbe>,
    isActive: Boolean,
    isRunning: Boolean,
    detecting: Boolean,
    onActivate: () -> Unit,
    onToggleEnable: (Boolean) -> Unit,
    onPickPlatform: () -> Unit,
    onDetect: () -> Unit,
    onDelete: () -> Unit,
) {
    val platforms = plugin.declaredPlatforms.ifEmpty { SourcePlatform.builtins }
    var menuExpanded by remember { mutableStateOf(false) }
    val tested = platforms.mapNotNull { probes[SourceProbe.pluginTarget(plugin.id, it)] }
        .filter { it.checkedAt > 0 }
    val okCount = tested.count { it.ok }
    val avgMs = tested.map { it.speedScoreMs }.takeIf { it.isNotEmpty() }?.average()
    val summary = buildString {
        if (tested.isEmpty()) {
            append("未检测")
        } else {
            append("$okCount/${platforms.size} 平台可用")
            if (avgMs != null && avgMs > 0) {
                append(" · 平均 ")
                append(String.format(Locale.US, "%.1fs", avgMs / 1000.0))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 4.dp)
            .padding(AppSpacing.xxl),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryBrown.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Extension, contentDescription = null, tint = PrimaryBrown, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(AppSpacing.lg))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isRunning) {
                        Spacer(Modifier.width(AppSpacing.xs))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentMint),
                        )
                    } else if (isActive) {
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text("当前", fontSize = 10.sp, color = PrimaryBrown, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("v${plugin.version}")
                        if (plugin.author.isNotBlank()) {
                            append(" · ")
                            append(plugin.author)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = TextSoft, modifier = Modifier.size(19.dp))
                }
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    AppDropdownItem(
                        text = "设为当前",
                        onClick = {
                            menuExpanded = false
                            onActivate()
                        },
                    )
                    AppDropdownItem(
                        text = "指定平台",
                        onClick = {
                            menuExpanded = false
                            onPickPlatform()
                        },
                    )
                    AppDropdownDivider()
                    AppDropdownItem(
                        text = "移除插件",
                        danger = true,
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(AppSpacing.lg))
        Text("支持平台", fontSize = 11.sp, color = TextSoft)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            platforms.take(5).forEach { platform ->
                val probe = probes[SourceProbe.pluginTarget(plugin.id, platform)]
                PlatformBadge(platform = platform, status = probe?.status ?: ProbeStatus.UNKNOWN)
            }
        }

        Spacer(Modifier.height(AppSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (tested.isEmpty()) TextMuted else if (okCount > 0) AccentMint else AccentRose),
            )
            Spacer(Modifier.width(5.dp))
            Text(summary, fontSize = 11.sp, color = TextSoft)
        }

        Spacer(Modifier.height(AppSpacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("已启用", fontSize = 12.sp, color = TextSoft)
            Spacer(Modifier.width(7.dp))
            Switch(
                checked = plugin.enable,
                onCheckedChange = onToggleEnable,
                colors = sourceSwitchColors(),
                modifier = Modifier.height(28.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentCyan.copy(alpha = 0.10f))
                    .clickable(enabled = !detecting, onClick = onDetect)
                    .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "检测",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentCyan,
                )
            }
        }
    }
}

@Composable
private fun PlatformBadge(platform: SourcePlatform, status: ProbeStatus) {
    val warning = status == ProbeStatus.FAIL || status == ProbeStatus.UNTESTABLE
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
        Box(contentAlignment = Alignment.TopEnd) {
            val icon = platformIconRes(platform)
            if (icon != null) {
                Image(
                    painter = painterResource(id = icon),
                    contentDescription = platform.label,
                    modifier = Modifier.size(26.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(platformColor(platform)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("咪", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            if (warning) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(11.dp))
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = platformShortLabel(platform),
            fontSize = 9.sp,
            color = TextSoft,
            maxLines = 1,
        )
    }
}

private fun platformIconRes(platform: SourcePlatform): Int? = when (platform) {
    SourcePlatform.NETEASE -> R.drawable.ic_netease_music
    SourcePlatform.QQ -> R.drawable.ic_qq_music
    SourcePlatform.KUGOU -> R.drawable.ic_kugou_music
    SourcePlatform.KUWO -> R.drawable.ic_kuwo_music
    SourcePlatform.MIGU -> null
}

private fun platformColor(platform: SourcePlatform): Color = when (platform) {
    SourcePlatform.NETEASE -> AppColors.brandNetease
    SourcePlatform.QQ -> AppColors.brandQQ
    SourcePlatform.KUGOU -> AppColors.brandKugou
    SourcePlatform.KUWO -> AppColors.brandKuwo
    SourcePlatform.MIGU -> AppColors.brandMigu
}

private fun platformShortLabel(platform: SourcePlatform): String = when (platform) {
    SourcePlatform.NETEASE -> "网易云"
    SourcePlatform.QQ -> "QQ"
    SourcePlatform.KUGOU -> "酷狗"
    SourcePlatform.KUWO -> "酷我"
    SourcePlatform.MIGU -> "咪咕"
}

@Composable
private fun QualityGroupCard(group: PlatformQualityGroup) {
    val passed = group.qualities.count { it.status == ProbeStatus.OK || it.status == ProbeStatus.SLOW }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(AppSpacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = platformIconRes(group.platform)
            if (icon != null) {
                Image(
                    painter = painterResource(id = icon),
                    contentDescription = group.platform.label,
                    modifier = Modifier.size(18.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(platformColor(group.platform)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("咪", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = group.platform.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text("已通过 $passed 档", fontSize = 10.5.sp, color = TextSoft)
        }
        Spacer(Modifier.height(AppSpacing.sm))
        group.qualities.forEach { quality ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(quality.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(quality.latencyLabel, fontSize = 10.sp, color = TextSoft)
                        if (quality.sizeLabel.isNotBlank()) {
                            Text(" · ", fontSize = 10.sp, color = TextMuted)
                            Text(quality.sizeLabel, fontSize = 10.sp, color = TextSoft)
                        }
                    }
                    if (quality.status != ProbeStatus.OK && quality.detail.isNotBlank()) {
                        Text(
                            text = quality.detail.take(36),
                            fontSize = 9.5.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                QualityStatusPill(quality.status)
            }
        }
    }
}

@Composable
private fun QualityStatusPill(status: ProbeStatus) {
    val (label, color) = when (status) {
        ProbeStatus.OK -> "通过" to AccentMint
        ProbeStatus.SLOW -> "偏慢" to AccentAmber
        ProbeStatus.FAIL -> "未确认" to AccentAmber
        ProbeStatus.UNTESTABLE -> "无法检测" to AccentAmber
        ProbeStatus.UNKNOWN -> "未检测" to TextMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == ProbeStatus.OK) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        } else {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(AppSpacing.xxs))
        Text(label, fontSize = 10.5.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String? = null, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = TextSoft)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = TextSoft, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ImportTile(
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
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
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
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 9.5.sp, color = TextSoft)
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(AppSpacing.lg))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = TextSoft)
        Spacer(Modifier.width(AppSpacing.xxs))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) PrimaryBrown.copy(alpha = 0.12f) else Stone100)
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) PrimaryBrown else TextSoft,
            maxLines = 1,
        )
    }
}

@Composable
private fun DetectChoiceRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Stone100)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xl, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 10.5.sp, color = TextSoft)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StatusStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = TextSoft)
    }
}

@Composable
private fun sourceSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = PrimaryBrown,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Stone200,
    uncheckedBorderColor = Color.Transparent,
)

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
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) PrimaryBrown else TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Text("当前", fontSize = 10.sp, color = PrimaryBrown, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun pluginSpeed(plugin: SourcePlugin, probes: Map<String, SourceProbe>): Long {
    val values = plugin.declaredPlatforms.ifEmpty { SourcePlatform.builtins }
        .mapNotNull { probes[SourceProbe.pluginTarget(plugin.id, it)] }
        .map { it.speedScoreMs }
    return if (values.isEmpty()) SourceProbe.UNKNOWN_LATENCY_MS else values.average().toLong()
}
