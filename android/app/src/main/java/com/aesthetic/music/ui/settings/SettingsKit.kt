package com.aesthetic.music.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aesthetic.music.ui.home.iosGlassCard
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.AppRadius
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.Background
import com.aesthetic.music.ui.theme.CardBorder
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import com.aesthetic.music.ui.components.BottomContentSpacer

data class SettingOption(val label: String)

@Composable
fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.page),
    ) {
        Spacer(Modifier.height(statusBarTop + 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onBack)
            )
            Spacer(Modifier.width(AppSpacing.lg))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(AppSpacing.xxxl))
        content()
        Spacer(Modifier.height(BottomContentSpacer))
    }
}

@Composable
fun SettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TextSoft,
                modifier = Modifier.padding(start = AppSpacing.xs, bottom = AppSpacing.sm),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .iosGlassCard(shape = RoundedCornerShape(AppRadius.card), elevation = 4.dp),
            content = content,
        )
    }
}

@Composable
fun SettingsGap(height: Int = 14) {
    Spacer(Modifier.height(height.dp))
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(AppRadius.xs))
            .background(tint.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = PrimaryBrown,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, iconTint)
        Spacer(Modifier.width(AppSpacing.lg))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSoft,
                )
            }
        }
        Spacer(Modifier.width(AppSpacing.sm))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = PrimaryBrown,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, iconTint)
        Spacer(Modifier.width(AppSpacing.lg))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
        }
        Spacer(Modifier.width(AppSpacing.md))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBrown,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Stone200,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun SettingsSegmentRow(
    title: String,
    options: List<SettingOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
        Spacer(Modifier.height(AppSpacing.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadius.sm))
                .background(Stone100)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val bg by animateColorAsState(
                    targetValue = if (selected) Surface else Color.Transparent,
                    animationSpec = tween(150),
                    label = "segment_bg",
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) PrimaryBrown else TextSoft,
                    animationSpec = tween(150),
                    label = "segment_text",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(AppRadius.xs))
                        .background(bg)
                        .clickable { onSelect(index) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsOptionList(
    title: String,
    options: List<SettingOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.xs),
        )
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg, vertical = 2.dp)
                    .clip(RoundedCornerShape(AppRadius.sm))
                    .background(if (selected) Stone100 else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (selected) PrimaryBrown else TextPrimary,
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = PrimaryBrown,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 60.dp)
            .height(1.dp)
            .background(Stone200.copy(alpha = 0.6f)),
    )
}
