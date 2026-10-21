package com.aesthetic.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * 播放队列抽屉 —— 按用户提供的瓜子截图重做。
 *
 * ## 为什么重做
 * 旧版（`PlayerScreen.kt` 里的 `showQueueSheet` 分支）每行右侧**只有一个 ✕**：
 * 想收藏当前队列里的歌、想把它加进自建歌单，都得先退出队列、去别的页面找那首歌。
 * 队列是用户"挑歌"的地方，却只给了"删掉"这一个动作，信息密度与操作路径都不合理。
 *
 * ## 版式（对齐截图）
 * ```
 * ┌──────────────────────────────────────────────┐
 * │ ‹   播放列表            [30]   清空     ✕    │  ← 标题 20sp Bold / 计数胶囊 / 红色清空
 * │ 长按拖动调整顺序                              │  ← 12sp 辅助说明
 * │ 01 ▢ Don't Start Now        ♡  ≡+  🗑        │  ← 当前项：序号与歌名变红
 * │    ▢ Dua Lipa                                │
 * │ 02 ▢ As It Was              ♡  ≡+  🗑        │
 * └──────────────────────────────────────────────┘
 * ```
 * 每行三颗按钮：**收藏 / 加入歌单 / 从队列移除**；长按整行可拖动排序。
 *
 * ## 两个实现细节
 * 1. **颜色随主题**：截图是浅色主题（白底深字），深色主题下用深底浅字，
 *    否则在深色播放页上弹出一张纯白卡片会非常刺眼。
 * 2. **拖动排序用「交换」而不是「位移」**：拖动过程中每次跨过一行就立即回调
 *    [onMove]，队列数据由 PlayerManager 持有，UI 不自己维护一份副本——
 *    否则「界面顺序」和「实际播放顺序」会出现两份真相。
 *    手势的 `pointerInput(Unit)` 必须不随 index 重启（用 [rememberUpdatedState] 读当前下标），
 *    否则每次交换都会取消正在进行的拖动手势（这是 LazyColumn 拖拽最常见的坑）。
 */
@Composable
fun PlayerQueueSheet(
    queue: List<Song>,
    currentIndex: Int,
    isFavorite: (Song) -> Boolean,
    onDismiss: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onClear: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    val dark = Palette.isDark
    val container = if (dark) Color(0xFF16171D) else Color(0xFFF7F7FA)
    val titleColor = if (dark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val subColor = if (dark) Color(0xFF8E8E93) else Color(0xFF9A9AA0)
    val iconColor = if (dark) Color.White.copy(alpha = 0.62f) else Color(0xFF6E6E78)
    val chipBg = if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFFE9E9EF)
    val accent = Color(0xFFFF3B30) // 截图里的「清空」与当前项红
    val rowActiveBg = if (dark) Color.White.copy(alpha = 0.06f) else Color(0x0D000000)

    val listState = rememberLazyListState()

    AppBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = container,
        showDragHandle = false,
    ) {
        // ===== 顶栏 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "返回",
                tint = titleColor,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(2.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "播放列表",
                color = titleColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            // 计数胶囊
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(chipBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${queue.size}",
                    color = subColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(10.dp))
            // 清空
            Text(
                text = "清空",
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = queue.isNotEmpty(), onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = subColor,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(2.dp),
            )
        }

        // ===== 辅助说明 =====
        Text(
            text = "长按拖动调整顺序",
            color = subColor,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
        )

        // ===== 队列列表 =====
        // key 必须「跟着歌走」而不是「跟着下标走」：如果 key 里带 index，
        // 拖动交换一次之后两行的 key 都变了 → 两个 item 被销毁重建 → 正在进行的拖动手势当场被取消，
        // 表现是「长按拖一下就断」。重复歌曲用出现序号区分。
        val stableKeys = remember(queue) {
            val seen = HashMap<String, Int>()
            queue.map { s ->
                val base = "${s.platform}:${s.id}"
                val n = (seen[base] ?: 0) + 1
                seen[base] = n
                "$base#$n"
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        ) {
            itemsIndexed(
                items = queue,
                key = { index, _ -> stableKeys.getOrElse(index) { "queue:$index" } },
            ) { index, song ->
                QueueRow(
                    song = song,
                    index = index,
                    count = queue.size,
                    isCurrent = index == currentIndex,
                    isFavorite = isFavorite(song),
                    dark = dark,
                    titleColor = titleColor,
                    subColor = subColor,
                    iconColor = iconColor,
                    accent = accent,
                    activeBg = rowActiveBg,
                    onPlay = { onPlayIndex(index) },
                    onRemove = { onRemoveIndex(index) },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onMove = onMove,
                )
            }
            item(key = "queue_bottom_spacer") {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** 行高固定 72dp：拖动排序按「跨过几行」换算位移，行高必须是常量 */
private val QueueRowHeight = 72.dp

@Composable
private fun QueueRow(
    song: Song,
    index: Int,
    count: Int,
    isCurrent: Boolean,
    isFavorite: Boolean,
    dark: Boolean,
    titleColor: Color,
    subColor: Color,
    iconColor: Color,
    accent: Color,
    activeBg: Color,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val rowHeightPx = with(density) { QueueRowHeight.toPx() }

    // 拖动状态：起始下标 + 累计位移。用 remember 而非 remember(index)，
    // 手势不能因为「交换后 index 变了」而重启（见文件头注释第 2 点）。
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val dragging = dragFrom >= 0
    val currentIndexState by rememberUpdatedState(index)
    val countState by rememberUpdatedState(count)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(QueueRowHeight)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) activeBg else Color.Transparent)
            .graphicsLayer {
                if (dragging) {
                    translationY = dragOffsetY
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = 12f
                }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragFrom = currentIndexState
                        dragOffsetY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val from = dragFrom
                        if (from < 0) return@detectDragGesturesAfterLongPress
                        dragOffsetY += amount.y
                        val steps = (dragOffsetY / rowHeightPx).roundToInt()
                        if (steps != 0) {
                            val target = (from + steps).coerceIn(0, countState - 1)
                            if (target != from) {
                                onMove(from, target)
                                dragFrom = target
                                dragOffsetY -= steps * rowHeightPx
                            }
                        }
                    },
                    onDragEnd = {
                        dragFrom = -1
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        dragFrom = -1
                        dragOffsetY = 0f
                    },
                )
            }
            .clickable(onClick = onPlay)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 序号（两位数，当前项红色）
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            color = if (isCurrent) accent else subColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(26.dp),
        )

        // 封面
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            if (!song.artwork.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(song.artwork).crossfade(180).build(),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                SongCover(url = null, modifier = Modifier.size(44.dp), corner = 8.dp)
            }
        }

        Spacer(Modifier.width(12.dp))

        // 歌名 + 歌手
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title.ifBlank { "未知歌曲" },
                color = if (isCurrent) accent else titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = song.artist.ifBlank { "未知歌手" },
                color = subColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 三颗操作：收藏 / 加入歌单 / 移除
        QueueAction(
            icon = {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) accent else iconColor,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onToggleFavorite,
        )
        QueueAction(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = "加入歌单",
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onAddToPlaylist,
        )
        QueueAction(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "从队列移除",
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onRemove,
        )
    }
}

/** 队列行右侧的单颗动作钮：36dp 命中区，无涟漪（与播放页其它圆形钮一致） */
@Composable
private fun QueueAction(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}
