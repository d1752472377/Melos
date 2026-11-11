package com.aesthetic.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.appIsDark

/** 首页与随心听共用的顶部导航：左边两个页面切换，右边搜索与听歌识曲入口 */
enum class DiscoverTab { DISCOVER, FM }

@Composable
fun DiscoverTopBar(
    active: DiscoverTab,
    onOpenDiscover: () -> Unit,
    onOpenFm: () -> Unit,
    onSearch: () -> Unit,
    onRecognition: () -> Unit,
    modifier: Modifier = Modifier,
    statusBarPadding: Boolean = true,
) {
    Row(
        modifier = modifier
            .then(if (statusBarPadding) Modifier.statusBarsPadding() else Modifier)
            .fillMaxWidth()
            .padding(start = AppSpacing.xxl, end = AppSpacing.xxl, top = AppSpacing.lg, bottom = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiscoverTabLabel(
            text = "发现",
            active = active == DiscoverTab.DISCOVER,
            onClick = onOpenDiscover,
        )
        Spacer(Modifier.width(AppSpacing.section))
        DiscoverTabLabel(
            text = "随心听",
            active = active == DiscoverTab.FM,
            onClick = onOpenFm,
        )

        Spacer(Modifier.weight(1f))

        TopIconButton(
            icon = Icons.Rounded.Search,
            contentDescription = "搜索",
            onClick = onSearch,
        )
        Spacer(Modifier.width(AppSpacing.sm))
        TopIconButton(
            icon = Icons.Rounded.Mic,
            contentDescription = "听歌识曲",
            onClick = onRecognition,
        )
    }
}

@Composable
private fun DiscoverTabLabel(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (active) textHigh() else textMedium(),
        fontSize = if (active) 22.sp else 16.sp,
        fontWeight = if (active) FontWeight(800) else FontWeight.Medium,
        letterSpacing = if (active) (-0.6).sp else 0.sp,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
    )
}

@Composable
private fun TopIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(textHigh().copy(alpha = if (appIsDark()) 0.10f else 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = textHigh(),
            modifier = Modifier.size(18.dp),
        )
    }
}
