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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aesthetic.music.BuildConfig
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.components.AppDropdownDivider
import com.aesthetic.music.ui.components.AppDropdownItem
import com.aesthetic.music.ui.components.AppDropdownMenu
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.glassBorder
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.theme.AccentCoral
import com.aesthetic.music.ui.theme.AccentCyan
import com.aesthetic.music.ui.theme.AccentMint
import com.aesthetic.music.ui.theme.AccentRose
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
    bio: String = "",
    sourceSummary: String,
    onOpenEditProfile: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenMyPlaylists: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenListeningStats: () -> Unit,
    onOpenCloudManage: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
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
                top = 0.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = BottomContentSpacer,
            ),
        ) {
            item(key = "top_profile_card") {
                ProfileHeader(
                    nickname = nickname,
                    userId = userId,
                    username = username,
                    bio = bio,
                    avatarPath = avatarPath,
                    backgroundPath = backgroundPath,
                    isBackgroundBlur = isBackgroundBlur,
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
                Column(Modifier.padding(horizontal = AppSpacing.page)) {
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
                }
            }

            item(key = "service_cards") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.page),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                ) {
                    ProfileHalfCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.GraphicEq,
                        iconTint = AccentCyan,
                        title = "统计",
                        subtitle = "使用数据一览",
                        onClick = onOpenListeningStats,
                    )
                    ProfileHalfCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.CloudSync,
                        iconTint = AppColors.purple,
                        title = "云端",
                        subtitle = "同步与备份",
                        onClick = onOpenCloudManage,
                    )
                }
            }

            item(key = "feature_menu_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.page)
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
                            icon = Icons.Filled.Feedback,
                            iconTint = AppColors.sky,
                            title = "提交反馈",
                            subtitle = "问题与建议",
                            onClick = onOpenFeedback,
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

            item(key = "logout_button") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.page)
                        .clip(RoundedCornerShape(9999.dp))
                        .border(1.dp, AppColors.crimson.copy(alpha = 0.45f), RoundedCornerShape(9999.dp))
                        .clickable { showLogoutConfirm = true }
                        .padding(vertical = AppSpacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "退出登录",
                        color = AppColors.crimson,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
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
private fun ProfileHeader(
    nickname: String,
    userId: String,
    username: String,
    bio: String,
    avatarPath: String?,
    backgroundPath: String?,
    isBackgroundBlur: Boolean,
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
        isLightBackground = if (backgroundPath.isNullOrBlank()) {
            true
        } else {
            calculateImageIsLight(context, backgroundPath)
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val imageHeight = statusBarTop + 176.dp
    val avatarSize = 88.dp
    val menuTint = if (isLightBackground) AppColors.profileOnLight else Color.White

    Box(Modifier.fillMaxWidth()) {
        // 背景直接铺满顶部（含状态栏），矩形、不做圆角，底部用渐变过渡到页面底色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight),
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
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    AppColors.profileAvatar1,
                                    AppColors.profileAvatar2,
                                    AppColors.profileAvatar3,
                                ),
                                start = Offset(0f, Float.POSITIVE_INFINITY),
                                end = Offset(Float.POSITIVE_INFINITY, 0f),
                            )
                        ),
                )
            }

            // 顶部压一层暗色，保证右上角菜单在任何背景上都看得清
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent)
                        )
                    ),
            )

            // 底部向页面底色过渡：矩形 + 柔和渐变，不做圆角
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Background,
                        )
                    ),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = AppSpacing.sm, end = AppSpacing.md),
            ) {
                IconButton(
                    onClick = { onMenuToggle(!showMenu) },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多设置",
                        tint = menuTint,
                        modifier = Modifier.size(20.dp),
                    )
                }

                val isCustomBackground = !backgroundPath.isNullOrBlank() || isBackgroundBlur

                AppDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onMenuToggle(false) },
                ) {
                    AppDropdownItem(
                        text = "更换背景",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = onChangeBackgroundClick,
                    )

                    AppDropdownItem(
                        text = if (isBackgroundBlur) "取消模糊" else "背景模糊",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.BlurOn,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = onToggleBgBlur,
                    )

                    if (isCustomBackground) {
                        AppDropdownItem(
                            text = "恢复默认",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.RestartAlt,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = onRestoreDefaults,
                        )
                    }

                    AppDropdownDivider()
                    AppDropdownItem(
                        text = "退出登录",
                        danger = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = AccentRose,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = onLogoutClick,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = imageHeight - avatarSize / 2),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.page),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .border(3.dp, Color.White, CircleShape)
                        .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!avatarPath.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(avatarPath).crossfade(true).build(),
                            contentDescription = "用户头像",
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(AppColors.profileAvatar2),
                        )
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .clickable(onClick = onEditProfileClick)
                        .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = AppColors.ink,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(
                        text = "编辑个人资料",
                        color = AppColors.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.xl))

            Column(
                modifier = Modifier.padding(horizontal = AppSpacing.page),
            ) {
                Text(
                    text = nickname,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    text = ProfileFormat.bioDisplay(bio),
                    color = TextMuted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = "@${ProfileFormat.usernameDisplay(username)}",
                    color = TextSoft,
                    fontSize = 11.sp,
                )
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
/** 个人页「统计 / 云端」这种半宽功能卡 */
@Composable
private fun ProfileHalfCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 2.dp)
            .border(1.dp, glassBorder(), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(AppSpacing.xl),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = TextSoft,
            fontSize = 11.sp,
        )
    }
}
