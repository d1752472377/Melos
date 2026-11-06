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
import com.aesthetic.music.ui.components.AppConfirmDialog
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
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors
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
    localStats: LocalStats,
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
    onPickAvatar: (Uri) -> String?,
    onPickBackground: (Uri) -> String?,
    onToggleBgBlur: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onLogout: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    isCheckingUpdate: Boolean = false,
    onShowToast: (String) -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = onPickAvatar(uri)
            if (saved != null) {
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
            val saved = onPickBackground(uri)
            if (saved != null) {
                onShowToast("背景图更新成功")
            } else {
                onShowToast("背景图保存失败")
            }
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        HomeAmbientGlowBackground()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxl),
            contentPadding = PaddingValues(
                top = statusBarTop + 16.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = BottomContentSpacer,
            ),
        ) {
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

            item(key = "music_entry_section") {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                    MusicStatsCard(
                        favoritesCount = favoritesCount,
                        playlistsCount = playlistsCount,
                        historyCount = historyCount,
                        cacheCount = cacheCount,
                        onOpenFavorites = onOpenFavorites,
                        onOpenMyPlaylists = onOpenMyPlaylists,
                        onOpenHistory = onOpenHistory,
                        onOpenDownloads = onOpenDownloads,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 2.dp)
                            .border(1.dp, glassBorder(), RoundedCornerShape(20.dp))
                            .padding(vertical = 2.dp),
                    ) {
                        Column {
                            ProfileFeatureRow(
                                icon = Icons.Filled.GraphicEq,
                                iconTint = AccentCyan,
                                title = "听歌统计",
                                subtitle = "时间脉络 · 数据回顾 · 喜好洞察",
                                onClick = onOpenListeningStats,
                            )
                            ProfileRowDivider()
                            ProfileFeatureRow(
                                icon = Icons.Filled.Tune,
                                iconTint = AppColors.sky,
                                title = "专业调音台",
                                subtitle = "大师级频响预设 · 动态平滑曲线",
                                badge = "10段EQ",
                                badgeTint = AppColors.skyDeep,
                                onClick = onOpenEqualizer,
                            )
                            ProfileRowDivider()
                            ProfileFeatureRow(
                                icon = Icons.Filled.CloudSync,
                                iconTint = AppColors.purple,
                                title = "账号与云端同步",
                                subtitle = "登录设备 · 云歌单 · 手动同步",
                                onClick = onOpenCloudManage,
                            )
                        }
                    }
                }
            }

            item(key = "feature_menu_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 2.dp)
                        .border(1.dp, glassBorder(), RoundedCornerShape(20.dp))
                        .padding(vertical = AppSpacing.xxs),
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
                            subtitle = if (isCheckingUpdate) "检查中…" else "v${BuildConfig.VERSION_NAME}",
                            isLoading = isCheckingUpdate,
                            onClick = onCheckUpdate,
                        )
                    }
                }
            }
        }
    }

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

}

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

    var isLightBackground by remember { mutableStateOf(true) }

    LaunchedEffect(backgroundPath) {
        if (!backgroundPath.isNullOrBlank()) {
            isLightBackground = calculateImageIsLight(context, backgroundPath)
        } else {
            isLightBackground = true
        }
    }

    val primaryTextColor = if (isLightBackground) AppColors.profileOnLight else Color.White
    val secondaryTextColor = if (isLightBackground) AppColors.profileSecondaryOnLight else Color.White.copy(alpha = 0.72f)
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
                        AppColors.profileAvatar1,
                        AppColors.profileAvatar2,
                        AppColors.profileAvatar3,
                    ),
                    start = Offset(0f, Float.POSITIVE_INFINITY),
                    end = Offset(Float.POSITIVE_INFINITY, 0f)
                )
            ),
    ) {
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
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.15f)
                                )
                            )
                        } else {
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
                .padding(AppSpacing.page)
        ) {
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
                        DropdownMenuItem(
                            text = { Text("更换背景", color = AppColors.profileOnLight, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = AppColors.profileOnLight,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = onChangeBackgroundClick,
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isBackgroundBlur) "取消模糊" else "背景模糊",
                                    color = AppColors.profileOnLight,
                                    fontSize = 13.sp,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.BlurOn,
                                    contentDescription = null,
                                    tint = AppColors.profileOnLight,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = onToggleBgBlur,
                        )

                        if (isCustomBackground) {
                            DropdownMenuItem(
                                text = { Text("恢复默认", color = AppColors.profileOnLight, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.RestartAlt,
                                        contentDescription = null,
                                        tint = AppColors.profileOnLight,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = onRestoreDefaults,
                            )
                        }

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

            Spacer(Modifier.height(AppSpacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            Brush.sweepGradient(
                                listOf(
                                    AppColors.amber,
                                    AppColors.sky,
                                    AppColors.pink,
                                    AppColors.amber
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

                Spacer(Modifier.width(AppSpacing.xxl))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
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
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "@${ProfileFormat.usernameDisplay(username)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = secondaryTextColor,
                    )

                    val totalMinutes = (localStats.totalPlayMs / 60000L).coerceAtLeast(0)
                    val totalHours = totalMinutes / 60
                    val remainMin = totalMinutes % 60
                    val durationStr = if (totalHours > 0) "${totalHours}h${remainMin}m" else "${remainMin}m"

                    Spacer(Modifier.height(AppSpacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(primaryTextColor.copy(alpha = 0.08f))
                                .padding(horizontal = AppSpacing.xs, vertical = 2.dp)
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
                                .padding(horizontal = AppSpacing.xs, vertical = 2.dp)
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

            Spacer(Modifier.height(AppSpacing.xxxl))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(buttonBgColor)
                    .border(1.dp, buttonBorderColor, RoundedCornerShape(21.dp))
                    .clickable(onClick = onEditProfileClick)
                    .padding(horizontal = AppSpacing.xxl),
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
                    Spacer(Modifier.width(AppSpacing.sm))
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
                    Spacer(Modifier.width(AppSpacing.xxs))
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
private fun MusicStatsCard(
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
        modifier = Modifier
            .fillMaxWidth()
            .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 2.dp)
            .border(1.dp, glassBorder(), RoundedCornerShape(20.dp))
            .padding(vertical = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactStatItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Favorite,
            iconTint = AppColors.rose,
            label = "收藏",
            count = favoritesCount,
            onClick = onOpenFavorites,
        )
        StatDivider()
        CompactStatItem(
            modifier = Modifier.weight(1f),
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            iconTint = AppColors.indigo,
            label = "歌单",
            count = playlistsCount,
            onClick = onOpenMyPlaylists,
        )
        StatDivider()
        CompactStatItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.History,
            iconTint = AccentCoral,
            label = "历史",
            count = historyCount,
            onClick = onOpenHistory,
        )
        StatDivider()
        CompactStatItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.DownloadDone,
            iconTint = AppColors.emerald,
            label = "下载",
            count = cacheCount,
            onClick = onOpenDownloads,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(Stone200.copy(alpha = 0.6f)),
    )
}

@Composable
private fun CompactStatItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    count: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = TextSoft,
        )
    }
}

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
            .padding(horizontal = AppSpacing.xxl, vertical = 13.dp),
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
        Spacer(Modifier.width(AppSpacing.xl))
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
        Spacer(Modifier.width(AppSpacing.xs))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun ProfileFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: String? = null,
    badgeTint: Color = iconTint,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AppSpacing.xl))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                )
                if (badge != null) {
                    Spacer(Modifier.width(AppSpacing.xs))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeTint.copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = badgeTint,
                        )
                    }
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
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

@Composable
private fun ProfileRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl)
            .height(1.dp)
            .background(Stone200.copy(alpha = 0.5f)),
    )
}