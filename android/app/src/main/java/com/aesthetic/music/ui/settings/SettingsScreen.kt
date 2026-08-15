package com.aesthetic.music.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.settings.ThemeMode
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

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    quality: QualityLevel,
    floatingCapsuleEnabled: Boolean,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onQualityChange: (QualityLevel) -> Unit,
    onFloatingCapsuleChange: (Boolean) -> Unit,
    onCheckUpdate: (onResult: (String) -> Unit) -> Unit,
) {
    var showQualityDialog by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("点击检查是否有新版本") }
    var checking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 150.dp),
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(38.dp))
        }

        Spacer(Modifier.height(8.dp))

        // ===== 外观 =====
        SectionCard(title = "外观") {
            // 三档主题胶囊选择器
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Stone100)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    ThemeMode.SYSTEM to "跟随系统",
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.DARK to "深色"
                ).forEach { (mode, label) ->
                    val isSelected = themeMode == mode
                    val bgAnimated by animateColorAsState(
                        targetValue = if (isSelected) PrimaryBrown else Color.Transparent,
                        animationSpec = tween(150),
                        label = "SegmentBg"
                    )
                    val textAnimated by animateColorAsState(
                        targetValue = if (isSelected) Color.White else TextMuted,
                        animationSpec = tween(150),
                        label = "SegmentText"
                    )

                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgAnimated)
                            .clickable { onThemeModeChange(mode) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textAnimated,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 悬浮胶囊开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TagBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Widgets,
                        contentDescription = null,
                        tint = TagText,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "系统悬浮胶囊",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (floatingCapsuleEnabled) "播放时跨 App 悬浮显示（需悬浮窗权限）" else "播放时跨 App 悬浮显示",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                    )
                }
                Switch(
                    checked = floatingCapsuleEnabled,
                    onCheckedChange = onFloatingCapsuleChange,
                    colors = SwitchDefaults.colors(
                        // 开启状态
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryBrown,
                        checkedBorderColor = Color.Transparent,
                        // 关闭状态（加深轨道颜色，提升对比度）
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Stone200, // 或 Color(0xFFE2E2E2)
                        uncheckedBorderColor = Color.Transparent
                    ),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ===== 播放与音质 =====
        SectionCard(title = "播放与音质") {
            SettingRow(
                icon = Icons.Filled.MusicNote,
                label = "音质等级",
                subtitle = quality.label,
                onClick = { showQualityDialog = true }
            )
        }

        Spacer(Modifier.height(14.dp))

        // ===== 关于 =====
        SectionCard(title = "关于") {
            SettingRow(
                icon = Icons.Filled.SystemUpdate,
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
                label = "版本号",
                subtitle = com.aesthetic.music.BuildConfig.VERSION_NAME,
                onClick = {}
            )
        }
    }

    // ===== 音质选择弹窗 (精美配色重构) =====
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "选择音质",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    QualityLevel.entries.forEach { level ->
                        val isSelected = quality == level
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
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
                                level.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "高音质（无损）加载更快占用空间相对较大，部分歌曲可能受版权限制自动降级播放。",
                        style = MaterialTheme.typography.bodySmall,
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
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)) // 16dp 圆角卡片
            .background(Surface)
            .padding(vertical = 12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = TextSoft,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TagBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TagText, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 2
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}