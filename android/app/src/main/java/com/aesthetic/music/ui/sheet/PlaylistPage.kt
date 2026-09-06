package com.aesthetic.music.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.ui.components.EmptyBox
import com.aesthetic.music.ui.components.ErrorBox
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.LocalBottomBarPadding
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.components.formatPlayCountText
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Surface
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import kotlinx.coroutines.launch

/**
 * 歌单广场（替代原"发现"页的主 Tab）：
 * - 以方块形式展示歌单；
 * - 顶部可切换 最新 / 最热；
 * - 右上角文字按钮（默认显示当前标签）点开左侧标签抽屉。
 */
@Composable
fun PlaylistPage(
    sheetTags: List<String>,
    sheetCat: String,
    sheetOrder: String,
    playlists: List<PlaylistItem>,
    isLoading: Boolean,
    error: String?,
    onSelectCat: (String) -> Unit,
    onSelectOrder: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "歌单分类",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                )
                sheetTags.forEach { tag ->
                    val selected = tag == sheetCat
                    NavigationDrawerItem(
                        label = { Text(tag, color = if (selected) PrimaryBrown else TextPrimary) },
                        selected = selected,
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = PrimaryBrown.copy(alpha = 0.12f),
                        ),
                        onClick = {
                            onSelectCat(tag)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // 标题 + 标签抽屉按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onBack)
                            .size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = "歌单广场",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { scope.launch { drawerState.open() } }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sheetCat,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBrown,
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = "打开歌单标签抽屉",
                        tint = PrimaryBrown,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // 最新 / 最热 切换
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OrderChip(label = "最热", selected = sheetOrder == "hot") {
                    onSelectOrder("hot")
                }
                OrderChip(label = "最新", selected = sheetOrder == "new") {
                    onSelectOrder("new")
                }
            }

            // 方块歌单列表
            when {
                isLoading && playlists.isEmpty() -> {
                    LoadingBox(message = "正在加载歌单…", modifier = Modifier.weight(1f).fillMaxWidth())
                }
                error != null && playlists.isEmpty() -> {
                    ErrorBox(message = error, onRetry = onRetry, modifier = Modifier.weight(1f).fillMaxWidth())
                }
                playlists.isEmpty() -> {
                    EmptyBox(message = "该分类下暂无歌单", modifier = Modifier.weight(1f).fillMaxWidth())
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = LocalBottomBarPadding.current,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(playlists, key = { "${it.platform}:${it.id}" }) { playlist ->
                            SheetCard(playlist = playlist, onClick = { onOpenPlaylist(playlist) })
                        }
                    }
                }
            }
        }
    }
}

/** 最新/最热描边胶囊 */
@Composable
private fun OrderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) PrimaryBrown else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) androidx.compose.ui.graphics.Color.White else TextSoft,
        )
    }
}

/** 单张方块歌单卡片 */
@Composable
private fun SheetCard(playlist: PlaylistItem, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        SongCover(
            url = playlist.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            corner = 14.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        val played = formatPlayCountText(playlist.playCount)
        val tracks = playlist.trackCount?.takeIf { it > 0 }?.let { "$it 首" }
        val meta = listOf(played, tracks).filter { !it.isNullOrBlank() }.joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}