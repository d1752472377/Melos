package com.aesthetic.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .border(1.dp, Stone200.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
            .shadow(4.dp, ambientColor = Color.Black.copy(alpha = 0.03f))
            .background(Surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        DockIcon(Icons.Filled.Home, selected = currentTab == AppTab.HOME, onClick = onHome)
        DockIcon(Icons.Filled.Explore, selected = currentTab == AppTab.DISCOVER, onClick = onDiscover)

        // 中央旋转唱片
        Box(
            Modifier
                .size(46.dp)
                .shadow(3.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.1f))
                .clip(CircleShape)
                .clickable(onClick = onOpenPlayer),
            contentAlignment = Alignment.Center,
        ) {
            RotatingDisc(currentArtwork, Modifier.size(46.dp))
        }

        DockIcon(Icons.AutoMirrored.Filled.QueueMusic, selected = currentTab == AppTab.PLAYLIST, onClick = onPlaylist)
        DockIcon(Icons.Filled.Person, selected = currentTab == AppTab.PROFILE, onClick = onProfile)
    }
}

@Composable
private fun DockIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PrimaryBrown.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) PrimaryBrown else TextSoft,
            modifier = Modifier.size(22.dp),
        )
    }
}
