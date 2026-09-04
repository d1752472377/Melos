package com.aesthetic.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aesthetic.music.ui.components.RotatingDisc
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 底部导航 Dock：主页 / 发现 / 中央旋转唱片 / 歌单 / 个人
 */
@Composable
fun NavigationDock(
    currentTab: AppTab,
    currentArtwork: String?,
    onHome: () -> Unit,
    onDiscover: () -> Unit,
    onPlaylist: () -> Unit,
    onProfile: () -> Unit,
    onOpenPlayer: () -> Unit,
    isSpinning: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(6.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), ambientColor = Color.Black.copy(alpha = 0.05f))
            .border(1.dp, Stone200.copy(alpha = 0.6f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        DockIcon(Icons.Filled.Home, label = "首页", selected = currentTab == AppTab.HOME, onClick = onHome)
        DockIcon(Icons.Filled.Explore, label = "发现", selected = currentTab == AppTab.DISCOVER, onClick = onDiscover)

        // 中央旋转黑胶唱片
        Box(
            Modifier
                .size(48.dp)
                .shadow(4.dp, CircleShape, ambientColor = PrimaryBrown.copy(alpha = 0.2f))
                .border(
                    width = 2.dp,
                    color = if (isSpinning) PrimaryBrown.copy(alpha = 0.5f) else Stone200.copy(alpha = 0.8f),
                    shape = CircleShape
                )
                .clip(CircleShape)
                .semantics { contentDescription = "打开播放器" }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenPlayer
                ),
            contentAlignment = Alignment.Center,
        ) {
            RotatingDisc(currentArtwork, Modifier.size(44.dp), isPlaying = isSpinning, durationMs = 5000)
        }

        DockIcon(Icons.AutoMirrored.Filled.QueueMusic, label = "歌单", selected = currentTab == AppTab.PLAYLIST, onClick = onPlaylist)
        DockIcon(Icons.Filled.Person, label = "我的", selected = currentTab == AppTab.PROFILE, onClick = onProfile)
    }
}

@Composable
private fun DockIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) PrimaryBrown.copy(alpha = 0.14f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) PrimaryBrown else TextSoft,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}