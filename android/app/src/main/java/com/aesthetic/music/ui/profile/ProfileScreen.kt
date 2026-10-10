package com.aesthetic.music.ui.profile

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aesthetic.music.BuildConfig
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.service.UpdateInfo
import com.aesthetic.music.service.UpdateService
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.AppDialog
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.glassBorder
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.AddPhotoAlternate
/**
 * 异步分析图片平均亮度：
 * 返回 true 表示浅色图片（文本使用黑色/深色），false 表示深色图片（文本使用白色/浅色）
 */
private suspend fun calculateImageIsLight(context: Context, imagePath: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imagePath)
                .allowHardware(false)
                .size(64, 64)
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    var totalLuminance = 0.0
                    val width = bitmap.width
                    val height = bitmap.height
                    val step = 4
                    var count = 0

                    for (x in 0 until width step step) {
                        for (y in 0 until height step step) {
                            val pixel = bitmap.getPixel(x, y)
                            val r = android.graphics.Color.red(pixel) / 255.0
                            val g = android.graphics.Color.green(pixel) / 255.0
                            val b = android.graphics.Color.blue(pixel) / 255.0
                            // ITU-R BT.709 亮度权重公式
                            totalLuminance += 0.2126 * r + 0.7152 * g + 0.0722 * b
                            count++
                        }
                    }
                    if (count > 0) {
                        return@withContext (totalLuminance / count) > 0.52
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * 个人界面：
 * 1. 顶部个性化卡片：头像（点击上传更换）、昵称、登录用户名、注册日期、编辑资料按钮、右上角三点下拉菜单；
 * 2. 快捷入口：收藏、歌单、播放历史、下载；
 * 3. 详细功能列表：设置、插件管理、检查更新。
 */
@Composable
fun ProfileScreen(
    nickname: String,
    userId: String,
    username: String,
    createdAt: Long,
    avatarPath: String?,
    backgroundPath: String?,
    isBackgroundBlur: Boolean,
    historyCount: Int,
    favoritesCount: Int,
    playlistsCount: Int,
    cacheCount: Int,
    cacheSize: Long,
    localStats: LocalStats,
    isSyncConfigured: Boolean = true,
    sourceSummary: String,
    onOpenEditProfile: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenMyPlaylists: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenListeningStats: () -> Unit,
    onOpenCloudManage: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenPluginManage: () -> Unit,
    onUpdateAvatar: (String?) -> Unit,
    onUpdateBackground: (String?) -> Unit,
    onToggleBgBlur: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onLogout: () -> Unit,
    onShowToast: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateAvailableInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateProgressText by remember { mutableStateOf<String?>(null) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = ProfileUtils.saveImageToInternal(context, uri, "avatar")
            if (saved != null) {
                onUpdateAvatar(saved)
                onShowToast("头像更新成功")
            } else {
                onShowToast("头像保存失败")
            }
        }
    }

    val bgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = ProfileUtils.saveImageToInternal(context, uri, "background")
            if (saved != null) {
                onUpdateBackground(saved)
                onShowToast("背景图更新成功")
            } else {
                onShowToast("背景图保存失败")
            }
        }
    }

    fun triggerCheckUpdate() {
        if (checkingUpdate) return
        checkingUpdate = true
        scope.launch {
            when (val result = UpdateService.checkUpdate()) {
                is UpdateService.CheckResult.Available -> {
                    checkingUpdate = false
                    updateAvailableInfo = result.info
                }
                is UpdateService.CheckResult.UpToDate -> {
                    checkingUpdate = false
                    onShowToast("当前已是最新版本 (v${BuildConfig.VERSION_NAME})")
                }
                is UpdateService.CheckResult.Failed -> {
                    checkingUpdate = false
                    onShowToast("检查更新失败: ${result.reason ?: "网络连接异常"}")
                }
            }
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // 全局动态呼吸极光光斑背景
        HomeAmbientGlowBackground()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = statusBarTop + 16.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = BottomContentSpacer,
            ),
        ) {
            // ===== 1. 顶部个性化卡片 =====
            item(key = "top_profile_card") {
                TopProfileCard(
                    nickname = nickname,
                    userId = userId,
                    username = username,
                    createdAt = createdAt,
                    avatarPath = avatarPath,
                    backgroundPath = backgroundPath,
                    isBackgroundBlur = isBackgroundBlur,
                    localStats = localStats,
                    showMenu = showMenu,
                    onMenuToggle = { showMenu = it },
                    onAvatarClick = { avatarPickerLauncher.launch("image/*") },
                    onEditProfileClick = onOpenEditProfile,
                    onChangeBackgroundClick = {
                        showMenu = false
                        bgPickerLauncher.launch("image/*")
                    },
                    onToggleBgBlur = {
                        showMenu = false
                        onToggleBgBlur()
                    },
                    onRestoreDefaults = {
                        showMenu = false
                        showRestoreConfirm = true
                    },
                    onLogoutClick = {
                        showMenu = false
                        showLogoutConfirm = true
                    },
                )
            }

            // ===== 2. 功能入口：上排4个独立毛玻璃卡片 + 下排快速功能入口 =====
            item(key = "music_entry_section") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MusicStatsRow(
                        favoritesCount = favoritesCount,
                        playlistsCount = playlistsCount,
                        historyCount = historyCount,
                        cacheCount = cacheCount,
                        onOpenFavorites = onOpenFavorites,
                        onOpenMyPlaylists = onOpenMyPlaylists,
                        onOpenHistory = onOpenHistory,
                        onOpenDownloads = onOpenDownloads,
                    )

                    // 听歌统计大卡片
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 2.dp)
                            // [迁移] 原 GlassTokens.glowBorderBrush/dockBorderBrush 为编造渐变，改用 GZ 真值 token
                            .border(1.dp, glassBorder(), RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenListeningStats)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "听歌统计",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = TextPrimary,
                            )
                            Text(
                                text = "时间脉络 · 数据回顾 · 喜好洞察",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextSoft,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp),
                        )
                    }

                    // 专业调音台 / 均衡器 (NEW! 深度对齐 GZ)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 2.dp)
                            // [迁移] 原 GlassTokens.glowBorderBrush/dockBorderBrush 为编造渐变，改用 GZ 真值 token
                            .border(1.dp, glassBorder(), RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenEqualizer)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "专业调音台",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = TextPrimary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF38BDF8).copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "10段EQ",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFF0284C7)
                                    )
                                }
                            }
                            Text(
                                text = "大师级频响预设 · 动态平滑曲线",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextSoft,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp),
                        )
                    }

                    // 云端管理
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 2.dp)
                            // [迁移] 原 GlassTokens.glowBorderBrush/dockBorderBrush 为编造渐变，改用 GZ 真值 token
                            .border(1.dp, glassBorder(), RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenCloudManage)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFA855F7).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudSync,
                                contentDescription = null,
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "云端备份与同步",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = TextPrimary,
                            )
                            Text(
                                text = "WebDAV / 多端云歌单同步",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextSoft,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            // ===== 3. 分组标题：系统与设置 =====
            item(key = "section_system_settings") {
                Column(modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)) {
                    Text(
                        text = "系统与设置",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextPrimary,
                    )
                }
            }

            // 剩余系统功能列表 (毛玻璃卡片组)
            item(key = "feature_menu_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 2.dp)
                        // [迁移] 原 GlassTokens.glowBorderBrush/dockBorderBrush 为编造渐变，改用 GZ 真值 token
                        .border(1.dp, glassBorder(), RoundedCornerShape(20.dp))
                        .padding(vertical = 4.dp),
                ) {
                    Column {
                        ProfileMenuItem(
                            icon = Icons.Filled.Settings,
                            iconTint = TextPrimary,
                            title = "设置",
                            subtitle = "偏好与外观",
                            onClick = onOpenSettings,
                        )
                        ProfileMenuItem(
                            icon = Icons.Filled.Extension,
                            iconTint = AccentMint,
                            title = "插件管理",
                            subtitle = sourceSummary,
                            onClick = onOpenPluginManage,
                        )
                        ProfileMenuItem(
                            icon = Icons.Filled.SystemUpdate,
                            iconTint = AccentCoral,
                            title = "检查更新",
                            subtitle = if (checkingUpdate) "检查中…" else "v${BuildConfig.VERSION_NAME}",
                            isLoading = checkingUpdate,
                            onClick = { triggerCheckUpdate() },
                        )
                    }
                }
            }
        }
    }

    // ===== 恢复默认确认弹窗 =====
    if (showRestoreConfirm) {
        AppConfirmDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = "恢复默认",
            message = "将清除自定义背景图、背景模糊、个性签名与昵称显示偏好（头像会保留）。确认恢复吗？",
            confirmText = "恢复默认",
            onConfirm = {
                showRestoreConfirm = false
                onRestoreDefaults()
            },
        )
    }

    // ===== 退出登录确认弹窗 =====
    if (showLogoutConfirm) {
        AppConfirmDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = "退出登录",
            message = "退出当前账号后，需重新验证账号才能继续使用 Melos。",
            confirmText = "退出",
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
        )
    }

    // ===== 发现新版本更新弹窗 =====
    val updateInfo = updateAvailableInfo
    if (updateInfo != null) {
        val isReady = remember(updateInfo, updateProgressText) { UpdateService.isApkReady(context, updateInfo) }
        AppDialog(
            onDismissRequest = {
                if (updateProgressText == null && !updateInfo.forceUpdate) updateAvailableInfo = null
            },
            title = if (updateInfo.forceUpdate) "发现重要更新 ${updateInfo.versionName}" else "发现新版本 ${updateInfo.versionName}",
            confirmText = if (updateProgressText == null) {
                if (isReady) "立即安装" else "立即更新"
            } else null,
            onConfirm = {
                scope.launch {
                    val alreadyDownloaded = UpdateService.isApkReady(context, updateInfo)
                    updateProgressText = if (alreadyDownloaded) "正在打开安装程序…" else "准备下载…"
                    try {
                        val path = UpdateService.downloadApk(context, updateInfo) { received, total ->
                            if (total > 0) {
                                updateProgressText = "下载中 ${received * 100 / total}%"
                            }
                        }
                        updateProgressText = "正在打开安装程序…"
                        if (UpdateService.installApk(context, path)) {
                            if (!updateInfo.forceUpdate) updateAvailableInfo = null
                            updateProgressText = null
                        } else {
                            onShowToast("请允许「安装未知应用」后重新安装")
                            updateProgressText = null
                        }
                    } catch (e: Exception) {
                        onShowToast("更新失败: ${e.message ?: "未知错误"}")
                        updateProgressText = null
                        if (!updateInfo.forceUpdate) updateAvailableInfo = null
                    }
                }
            },
            cancelText = if (updateProgressText == null && !updateInfo.forceUpdate) "稍后再说" else null,
            onCancel = { updateAvailableInfo = null },
        ) {
            Column {
                if (updateProgressText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(updateProgressText ?: "正在处理…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(
                        text = if (updateInfo.updateLog.isNotBlank()) updateInfo.updateLog else "修复已知问题，提升使用体验。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "大小: ${AudioCacheService.formatSize(updateInfo.apkSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                        if (isReady) {
                            Text(
                                text = "已就绪 (免流量)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶部个性化卡片（支持图片亮度检测与自适应字体）
 */
@Composable
private fun TopProfileCard(
    nickname: String,
    userId: String,
    username: String,
    createdAt: Long,
    avatarPath: String?,
    backgroundPath: String?,
    isBackgroundBlur: Boolean,
    localStats: LocalStats,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onAvatarClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onChangeBackgroundClick: () -> Unit,
    onToggleBgBlur: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    val context = LocalContext.current

    // 状态：背景是否为浅色（默认浅色弥散背景，属于浅色）
    var isLightBackground by remember { mutableStateOf(true) }

    // 监测 backgroundPath 变动，动态分析亮度
    LaunchedEffect(backgroundPath) {
        if (!backgroundPath.isNullOrBlank()) {
            isLightBackground = calculateImageIsLight(context, backgroundPath)
        } else {
            isLightBackground = true // 默认粉蓝白色渐变卡片属于浅色
        }
    }

    // 根据明暗动态派生色彩系统
    val primaryTextColor = if (isLightBackground) Color(0xFF1E2329) else Color.White
    val secondaryTextColor = if (isLightBackground) Color(0xFF5E6B7A) else Color.White.copy(alpha = 0.72f)
    val buttonBgColor = if (isLightBackground) Color.White.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.15f)
    val buttonBorderColor = if (isLightBackground) Color.White else Color.White.copy(alpha = 0.22f)
    val avatarBorderColor = if (isLightBackground) Color.White else Color.White.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFCCE4FF),
                        Color(0xFFF7FAFD),
                        Color(0xFFFFB3C6),
                    ),
                    start = Offset(0f, Float.POSITIVE_INFINITY),
                    end = Offset(Float.POSITIVE_INFINITY, 0f)
                )
            ),
    ) {
        // 自定义背景图层与智能补光遮罩
        if (!backgroundPath.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(backgroundPath).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .then(if (isBackgroundBlur) Modifier.blur(20.dp) else Modifier),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (isLightBackground) {
                            // 浅色图：使用纯白透明渐变（Brush 类型）
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.15f)
                                )
                            )
                        } else {
                            // 深色图：使用黑白暗色渐变（Brush 类型）
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.20f),
                                    Color.Black.copy(alpha = 0.65f)
                                )
                            )
                        }
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 顶栏：微型标题与三点菜单
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MELOS MUSIC PROFILE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    ),
                    color = secondaryTextColor,
                )
                Box {
                    IconButton(
                        onClick = { onMenuToggle(!showMenu) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多设置",
                            tint = primaryTextColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    val isCustomBackground = !backgroundPath.isNullOrBlank() || isBackgroundBlur

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { onMenuToggle(false) },
                        modifier = Modifier
                            .background(Color.White)
                            .clip(RoundedCornerShape(16.dp)),
                    ) {
                        // 1. 更换背景（带加号图标）
                        DropdownMenuItem(
                            text = { Text("更换背景", color = Color(0xFF1E2329), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Color(0xFF1E2329),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = onChangeBackgroundClick,
                        )

                        // 2. 背景模糊
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isBackgroundBlur) "取消模糊" else "背景模糊",
                                    color = Color(0xFF1E2329),
                                    fontSize = 13.sp,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.BlurOn,
                                    contentDescription = null,
                                    tint = Color(0xFF1E2329),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = onToggleBgBlur,
                        )

                        // 3. 恢复默认（仅在用户改动了背景图或开启了模糊时显示，默认状态下自动隐藏）
                        if (isCustomBackground) {
                            DropdownMenuItem(
                                text = { Text("恢复默认", color = Color(0xFF1E2329), fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.RestartAlt,
                                        contentDescription = null,
                                        tint = Color(0xFF1E2329),
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = onRestoreDefaults,
                            )
                        }

                        // 4. 退出登录（移除了上方横线，紧凑排版）
                        DropdownMenuItem(
                            text = { Text("退出登录", color = MaterialTheme.colorScheme.error, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = onLogoutClick,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 用户头像与基本信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 奢华双环发光头像
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFF59E0B),
                                    Color(0xFF38BDF8),
                                    Color(0xFFEC4899),
                                    Color(0xFFF59E0B)
                                )
                            ),
                            CircleShape
                        )
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .border(1.dp, avatarBorderColor, CircleShape)
                        .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!avatarPath.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(avatarPath).crossfade(true).build(),
                            contentDescription = "用户头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = primaryTextColor,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = nickname,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = primaryTextColor,
                            maxLines = 1,
                        )

                        // 100% 永久全解锁尊享金标 (对齐 GZ Pro 勋章)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                    )
                                )
                                .border(0.8.dp, Color(0xFFFEF3C7).copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "永久尊享",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "@${ProfileFormat.usernameDisplay(username)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = secondaryTextColor,
                    )

                    // 听歌时长与统计概览微胶囊
                    val totalMinutes = (localStats.totalPlayMs / 60000L).coerceAtLeast(0)
                    val totalHours = totalMinutes / 60
                    val remainMin = totalMinutes % 60
                    val durationStr = if (totalHours > 0) "${totalHours}h${remainMin}m" else "${remainMin}m"

                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(primaryTextColor.copy(alpha = 0.08f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "听歌 ${localStats.totalPlays} 首",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                color = secondaryTextColor
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(primaryTextColor.copy(alpha = 0.08f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "时长 $durationStr",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                color = secondaryTextColor
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // 宽幅自适应编辑资料按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(buttonBgColor)
                    .border(1.dp, buttonBorderColor, RoundedCornerShape(21.dp))
                    .clickable(onClick = onEditProfileClick)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = primaryTextColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "编辑音乐资料",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = primaryTextColor,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "个人档案",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = secondaryTextColor,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicStatsRow(
    favoritesCount: Int,
    playlistsCount: Int,
    historyCount: Int,
    cacheCount: Int,
    onOpenFavorites: () -> Unit,
    onOpenMyPlaylists: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ModernStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Favorite,
            iconTint = Color(0xFFFF4D6D),
            bgGradient = listOf(Color(0x33FF4D6D), Color(0x10FF4D6D)),
            label = "收藏",
            count = "$favoritesCount",
            onClick = onOpenFavorites,
        )
        ModernStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            iconTint = Color(0xFF6366F1),
            bgGradient = listOf(Color(0x336366F1), Color(0x106366F1)),
            label = "歌单",
            count = "$playlistsCount",
            onClick = onOpenMyPlaylists,
        )
        ModernStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.History,
            iconTint = AccentCoral,
            bgGradient = listOf(Color(0x33FF6B6B), Color(0x10FF6B6B)),
            label = "历史",
            count = "$historyCount",
            onClick = onOpenHistory,
        )
        ModernStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.DownloadDone,
            iconTint = Color(0xFF10B981),
            bgGradient = listOf(Color(0x3310B981), Color(0x1010B981)),
            label = "下载",
            count = "$cacheCount",
            onClick = onOpenDownloads,
        )
    }
}

@Composable
private fun ModernStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    bgGradient: List<Color>,
    label: String,
    count: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .iosGlassCard(shape = RoundedCornerShape(18.dp), elevation = 2.dp)
            // [迁移] 原 GlassTokens.glowBorderBrush/dockBorderBrush 为编造渐变，改用 GZ 真值 token
            .border(1.dp, glassBorder(), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(bgGradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = TextSoft,
            )
        }
    }
}

/**
 * 菜单行项
 */
@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = iconTint,
            )
        } else if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(12.dp),
        )
    }
}