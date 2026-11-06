package com.aesthetic.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Wallpaper
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.BackgroundBlurMode
import com.aesthetic.music.data.settings.LyricBlurMode
import com.aesthetic.music.data.settings.LyricFontSize
import com.aesthetic.music.data.settings.PlayerTheme
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.player.PlayMode
import com.aesthetic.music.ui.theme.AmberLight
import com.aesthetic.music.ui.theme.Rose
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import java.util.Locale
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

/**
 * 全功能播放器中控抽屉（Liquid Glass 玻璃液化质感）：
 * 整合：
 * 1. 播放倍速 (Playback Speed)
 * 2. 睡眠定时 (Sleep Timer)
 * 3. 封面/歌词视图切换 (Cover/Lyrics Toggle)
 * 4. 离线下载 (Download Song)
 * 5. 收藏至歌单 (Add to Playlist)
 * 6. 播放页主题热切 (Player Theme)
 * 7. 播放模式切换 (Play Mode)
 * 8. 歌词字体大小与模糊控制 (Lyrics Font & Blur)
 * 9. 背景画质模糊控制 (Background Blur)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControlCenterSheet(
    onDismissRequest: () -> Unit,
    song: Song?,
    showLyric: Boolean,
    onToggleLyric: () -> Unit,
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    sleepRemainingMs: Long,
    onSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    currentTheme: PlayerTheme,
    onThemeSelect: (PlayerTheme) -> Unit,
    playMode: PlayMode,
    onTogglePlayMode: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    /** 当前音质等级（与设置页同一份设置，改这里等于改全局音质） */
    currentQuality: QualityLevel,
    onQualityChange: (QualityLevel) -> Unit,
    currentLyricFontSize: LyricFontSize,
    onLyricFontSizeChange: (LyricFontSize) -> Unit,
    currentLyricBlurMode: LyricBlurMode,
    onLyricBlurModeChange: (LyricBlurMode) -> Unit,
    currentBgBlurMode: BackgroundBlurMode,
    onBgBlurModeChange: (BackgroundBlurMode) -> Unit,
    hazeState: HazeState? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = null,
    ) {
        val hazeModifier = if (hazeState != null) {
            Modifier.hazeEffect(
                state = hazeState,
                style = HazeDefaults.style(
                    backgroundColor = AppColors.controlBg,
                    blurRadius = 32.dp,
                    noiseFactor = 0.02f,
                )
            )
        } else {
            Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        AppColors.controlBgTop,
                        AppColors.controlBgBottom,
                    )
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .then(hazeModifier)
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.page)
                .padding(top = AppSpacing.lg, bottom = AppSpacing.section)
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶栏拖动把手 (Liquid Capsule)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
            )

            Spacer(Modifier.height(AppSpacing.xl))

            // 标题与歌曲信息栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!song?.artwork.isNullOrBlank()) {
                    AsyncImage(
                        model = song?.artwork,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(AppSpacing.lg))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "播放中控台",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color.White,
                        )
                        Spacer(Modifier.width(AppSpacing.xs))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "CONTROL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                    Text(
                        text = song?.let { "${it.title} · ${it.artist}" } ?: "未在播放",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 关闭按钮
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onDismissRequest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭中控",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.xxxl))

            // 第一板块：4 个快捷功能液化磁贴
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                // 1. 切换封面/歌词
                LiquidTile(
                    modifier = Modifier.weight(1f),
                    icon = if (showLyric) Icons.Filled.Album else Icons.Filled.Lyrics,
                    title = if (showLyric) "切至封面" else "切至歌词",
                    subtitle = if (showLyric) "当前歌词" else "当前封面",
                    highlight = true,
                    onClick = onToggleLyric,
                )

                // 2. 播放顺序
                val (modeIcon, modeText) = when (playMode) {
                    PlayMode.SEQUENCE -> Icons.Filled.Repeat to "顺序播放"
                    PlayMode.REPEAT -> Icons.Filled.RepeatOne to "单曲循环"
                    PlayMode.SHUFFLE -> Icons.Filled.Shuffle to "随机播放"
                }
                LiquidTile(
                    modifier = Modifier.weight(1f),
                    icon = modeIcon,
                    title = modeText,
                    subtitle = "播放顺序",
                    highlight = playMode != PlayMode.SEQUENCE,
                    onClick = onTogglePlayMode,
                )
            }

            Spacer(Modifier.height(AppSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                // 3. 下载当前单曲
                LiquidTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Download,
                    title = "离线下载",
                    subtitle = "保存至本地",
                    highlight = false,
                    onClick = onDownload,
                )

                // 4. 添加到歌单
                LiquidTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    title = "加入歌单",
                    subtitle = "自建收藏夹",
                    highlight = false,
                    onClick = onAddToPlaylist,
                )
            }

            Spacer(Modifier.height(AppSpacing.page))

            // 第二板块：音质（GZ 播放页也有音质可选）
            // 为什么放在中控台而不是设置页：音质是"这首/此刻"的诉求（耳机/外放/流量），
            // 藏在设置深处会让人每次都翻两层菜单。切换后会从当前位置重播（见 PlayerManager）。
            SectionHeader(
                icon = Icons.Filled.HighQuality,
                title = "播放音质",
                trailingText = currentQuality.label,
            )
            Spacer(Modifier.height(AppSpacing.md))
            LiquidOptionSegmentedBar(
                options = QualityLevel.entries.map { it to it.label },
                selectedOption = currentQuality,
                onSelect = onQualityChange,
            )

            Spacer(Modifier.height(AppSpacing.page))

            // 第三板块：播放倍速分段控制器
            SectionHeader(
                icon = Icons.Filled.Speed,
                title = "播放倍速",
                trailingText = if (currentSpeed % 1f == 0f) "${currentSpeed.toInt()}x" else "${currentSpeed}x",
            )
            Spacer(Modifier.height(AppSpacing.md))
            LiquidSpeedSegmentedBar(
                currentSpeed = currentSpeed,
                onSpeedSelect = onSpeedChange,
            )

            Spacer(Modifier.height(AppSpacing.page))

            // 第三板块：睡眠定时
            SectionHeader(
                icon = Icons.Filled.Bedtime,
                title = "睡眠定时",
                trailingText = if (sleepRemainingMs > 0) "倒计时 ${formatTimer(sleepRemainingMs)}" else "未开启",
                trailingColor = if (sleepRemainingMs > 0) AmberLight else Color.White.copy(alpha = 0.5f),
                onTrailingClick = if (sleepRemainingMs > 0) onCancelSleepTimer else null,
                trailingActionLabel = if (sleepRemainingMs > 0) "关闭" else null,
            )
            Spacer(Modifier.height(AppSpacing.md))
            LiquidSleepTimerBar(
                sleepRemainingMs = sleepRemainingMs,
                onSelectMinutes = onSleepTimer,
                onCancel = onCancelSleepTimer,
            )

            Spacer(Modifier.height(AppSpacing.page))

            // 第四板块：播放页主题切换
            SectionHeader(
                icon = Icons.Filled.Palette,
                title = "播放页主题",
                trailingText = currentTheme.label,
            )
            Spacer(Modifier.height(AppSpacing.md))
            LiquidThemeCards(
                currentTheme = currentTheme,
                onThemeSelect = onThemeSelect,
            )

            Spacer(Modifier.height(AppSpacing.page))

            // 第五板块：歌词与视觉个性化
            SectionHeader(
                icon = Icons.Filled.FormatSize,
                title = "歌词字体大小",
                trailingText = currentLyricFontSize.label,
            )
            Spacer(Modifier.height(AppSpacing.md))
            LiquidOptionSegmentedBar(
                options = listOf(
                    LyricFontSize.STANDARD to LyricFontSize.STANDARD.label,
                    LyricFontSize.LARGE to LyricFontSize.LARGE.label,
                    LyricFontSize.EXTRA_LARGE to LyricFontSize.EXTRA_LARGE.label,
                ),
                selectedOption = currentLyricFontSize,
                onSelect = onLyricFontSizeChange,
            )

            Spacer(Modifier.height(AppSpacing.xxl))

            SectionHeader(
                icon = Icons.Filled.BlurOn,
                title = "未聚焦歌词虚化",
                trailingText = currentLyricBlurMode.label,
            )
            Spacer(Modifier.height(AppSpacing.md))
            LiquidOptionSegmentedBar(
                options = listOf(
                    LyricBlurMode.OFF to LyricBlurMode.OFF.label,
                    LyricBlurMode.SUBTLE to LyricBlurMode.SUBTLE.label,
                    LyricBlurMode.DEEP to LyricBlurMode.DEEP.label,
                ),
                selectedOption = currentLyricBlurMode,
                onSelect = onLyricBlurModeChange,
            )

            Spacer(Modifier.height(AppSpacing.xxl))

            SectionHeader(
                icon = Icons.Filled.Wallpaper,
                title = "背景画质模糊",
                trailingText = currentBgBlurMode.label,
            )
            Spacer(Modifier.height(AppSpacing.md))
            LiquidOptionSegmentedBar(
                options = listOf(
                    BackgroundBlurMode.LIGHT to BackgroundBlurMode.LIGHT.label,
                    BackgroundBlurMode.MEDIUM to BackgroundBlurMode.MEDIUM.label,
                    BackgroundBlurMode.HEAVY to BackgroundBlurMode.HEAVY.label,
                ),
                selectedOption = currentBgBlurMode,
                onSelect = onBgBlurModeChange,
            )
        }
    }
}

/** 格式化睡眠定时剩余时间 */
private fun formatTimer(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

/** 分区标题栏 */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    trailingText: String,
    trailingColor: Color = Color.White.copy(alpha = 0.7f),
    onTrailingClick: (() -> Unit)? = null,
    trailingActionLabel: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(AppSpacing.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.9f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = trailingColor,
            )
            if (trailingActionLabel != null && onTrailingClick != null) {
                Spacer(Modifier.width(AppSpacing.sm))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Rose.copy(alpha = 0.2f))
                        .clickable(onClick = onTrailingClick)
                        .padding(horizontal = AppSpacing.xs, vertical = 2.dp)
                ) {
                    Text(
                        text = trailingActionLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Rose,
                    )
                }
            }
        }
    }
}

/** 液化玻璃磁贴 */
@Composable
private fun LiquidTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgBrush = if (highlight) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.08f),
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.04f),
            )
        )
    }

    val borderBrush = if (highlight) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.15f),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.05f),
            )
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderBrush, RoundedCornerShape(16.dp))
            .background(bgBrush)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (highlight) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = if (highlight) Color.White else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(AppSpacing.md))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = Color.White,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
}

/** 液化分段倍速控制条 */
@Composable
private fun LiquidSpeedSegmentedBar(
    currentSpeed: Float,
    onSpeedSelect: (Float) -> Unit,
) {
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(AppSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
    ) {
        speeds.forEach { speed ->
            val isSelected = currentSpeed == speed
            val label = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.28f),
                                            Color.White.copy(alpha = 0.16f),
                                        )
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable(onClick = { onSpeedSelect(speed) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                    ),
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                )
            }
        }
    }
}

/** 液化睡眠定时快捷选项条 */
@Composable
private fun LiquidSleepTimerBar(
    sleepRemainingMs: Long,
    onSelectMinutes: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val options = listOf(15, 30, 45, 60)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        options.forEach { minutes ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .clickable(onClick = { onSelectMinutes(minutes) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${minutes}分",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/** 液化播放页主题卡片 */
@Composable
private fun LiquidThemeCards(
    currentTheme: PlayerTheme,
    onThemeSelect: (PlayerTheme) -> Unit,
) {
    val themes = listOf(
        Triple(
            PlayerTheme.CLASSIC,
            "经典深邃",
            Brush.verticalGradient(listOf(AppColors.controlGradient1, AppColors.controlGradient2))
        ),
        Triple(
            PlayerTheme.COVER,
            "黑胶唱机",
            Brush.verticalGradient(listOf(AppColors.controlCoverTop, AppColors.controlCoverBottom))
        ),
        Triple(
            PlayerTheme.APPLE,
            "Apple 流光",
            Brush.linearGradient(listOf(AppColors.controlNeon1, AppColors.controlNeon2, AppColors.controlNeon3))
        ),
        Triple(
            PlayerTheme.RECORD_SHELF,
            "唱片封套",
            Brush.linearGradient(listOf(AppColors.controlGray1, AppColors.controlGray2, AppColors.slate900))
        ),
        Triple(
            PlayerTheme.LUMINOUS_CD,
            "流光 CD",
            Brush.linearGradient(listOf(AppColors.slate500, AppColors.slate300, AppColors.slate600))
        ),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        themes.forEach { (theme, name, brush) ->
            val isSelected = theme == currentTheme

            Column(
                modifier = Modifier.width(92.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            brush = if (isSelected) {
                                Brush.verticalGradient(
                                    listOf(
                                        AmberLight,
                                        AmberLight.copy(alpha = 0.5f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.22f),
                                        Color.White.copy(alpha = 0.06f)
                                    )
                                )
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .background(brush)
                        .clickable(onClick = { onThemeSelect(theme) })
                        .padding(AppSpacing.sm),
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(AmberLight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }

                    // 迷你卡片装饰
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.4f))
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                    }
                }

                Spacer(Modifier.height(AppSpacing.xs))

                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp,
                    ),
                    color = if (isSelected) AmberLight else Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** 液化通用选项分段控制条 */
@Composable
private fun <T> LiquidOptionSegmentedBar(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(AppSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
    ) {
        options.forEach { (option, label) ->
            val isSelected = option == selectedOption

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.28f),
                                            Color.White.copy(alpha = 0.16f),
                                        )
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable(onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                    ),
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                )
            }
        }
    }
}

