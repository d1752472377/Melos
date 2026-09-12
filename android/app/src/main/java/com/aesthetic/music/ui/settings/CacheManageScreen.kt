package com.aesthetic.music.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.data.cache.CacheCenter
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.components.AppConfirmDialog
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.Stone200
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/**
 * 缓存管理页：展示缓存总占用，点击打开抽屉看三类明细并可分别/一键清理。
 *
 * 数据与清理动作都由 destination 提供（需要 Context / 协程），这里只负责呈现。
 */
@Composable
fun CacheManageScreen(
    breakdown: CacheCenter.Breakdown?,
    isScanning: Boolean,
    cachedSongCount: Int,
    showDetail: Boolean,
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    onCloseDetail: () -> Unit,
    onRefresh: () -> Unit,
    onClearMusic: () -> Unit,
    onClearImages: () -> Unit,
    onClearTemp: () -> Unit,
    onClearAll: () -> Unit,
    onOpenCachedSongs: () -> Unit,
) {
    SettingsPageScaffold(title = "缓存管理", subtitle = "查看占用并清理本地缓存", onBack = onBack) {
        // ===== 总占用卡片 =====
        SettingsSection {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDetail)
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryBrown.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = PrimaryBrown,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "缓存占用",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isScanning) "正在统计…" else "点击查看明细与清理",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSoft,
                        )
                    }
                    Text(
                        text = breakdown?.let { CacheCenter.formatSize(it.total) } ?: "--",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = PrimaryBrown,
                    )
                }

                if (breakdown != null) {
                    Spacer(Modifier.height(14.dp))
                    CacheRatioBar(breakdown)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RatioLegend("下载音乐", breakdown.music, PrimaryBrown)
                        RatioLegend("图片缓存", breakdown.images, Color(0xFF3E7CB1))
                        RatioLegend("临时文件", breakdown.temp, Color(0xFF8A8F98))
                    }
                }
            }
        }

        SettingsGap()

        SettingsSection(title = "快捷操作") {
            SettingsNavRow(
                icon = Icons.Filled.MusicNote,
                title = "已缓存歌曲",
                subtitle = if (cachedSongCount > 0) "$cachedSongCount 首，可单曲删除或播放" else "还没有缓存歌曲",
                onClick = onOpenCachedSongs,
            )
            SettingsDivider()
            SettingsNavRow(
                icon = Icons.Filled.CleaningServices,
                title = "清理全部缓存",
                subtitle = "清空下载音乐、图片缓存与临时文件",
                iconTint = Color(0xFFC0392B),
                onClick = onClearAll,
            )
            SettingsDivider()
            SettingsNavRow(
                icon = Icons.Filled.DeleteSweep,
                title = "重新统计",
                subtitle = "缓存大小会随下载/播放实时变化",
                onClick = onRefresh,
            )
        }

        SettingsGap(10)

        SettingsNote("· 「下载音乐」是边听边下产生的音频文件；清理后已缓存歌曲需要重新联网获取。")
        SettingsNote("· 「图片缓存」主要是专辑封面与头像；清理后会在浏览时重新下载，不影响本地文件。")
        SettingsNote("· 「临时文件」包含下载分片与其它临时数据，可安全清理。")
    }

    if (showDetail) {
        CacheDetailSheet(
            breakdown = breakdown,
            isScanning = isScanning,
            onDismiss = onCloseDetail,
            onClearMusic = onClearMusic,
            onClearImages = onClearImages,
            onClearTemp = onClearTemp,
        )
    }
}

/** 三色占比条：一眼看出谁占地方 */
@Composable
private fun CacheRatioBar(breakdown: CacheCenter.Breakdown) {
    val total = breakdown.total.coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Stone200),
    ) {
        listOf(
            breakdown.music to PrimaryBrown,
            breakdown.images to Color(0xFF3E7CB1),
            breakdown.temp to Color(0xFF8A8F98),
        ).forEach { (size, color) ->
            val fraction = size.toFloat() / total.toFloat()
            if (fraction > 0f) {
                Box(
                    Modifier
                        .weight(fraction.coerceAtLeast(0.001f))
                        .height(8.dp)
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun RatioLegend(label: String, size: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(3.5f.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$label ${CacheCenter.formatSize(size)}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = TextMuted,
        )
    }
}

/** 缓存明细抽屉：三类各自的大小 + 单独清理 */
@Composable
private fun CacheDetailSheet(
    breakdown: CacheCenter.Breakdown?,
    isScanning: Boolean,
    onDismiss: () -> Unit,
    onClearMusic: () -> Unit,
    onClearImages: () -> Unit,
    onClearTemp: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "缓存明细",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "合计 ${breakdown?.let { CacheCenter.formatSize(it.total) } ?: "--"}" +
                    if (isScanning) "（统计中…）" else "",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextMuted,
            )
            Spacer(Modifier.height(16.dp))

            CacheDetailRow(
                icon = Icons.Filled.MusicNote,
                title = "下载音乐",
                subtitle = "边听边下的音频文件",
                size = breakdown?.music ?: 0L,
                onClear = onClearMusic,
            )
            Spacer(Modifier.height(10.dp))
            CacheDetailRow(
                icon = Icons.Filled.Image,
                title = "图片缓存",
                subtitle = "封面 / 头像等图片",
                size = breakdown?.images ?: 0L,
                onClear = onClearImages,
            )
            Spacer(Modifier.height(10.dp))
            CacheDetailRow(
                icon = Icons.Filled.Warehouse,
                title = "临时文件离线缓存",
                subtitle = "下载分片与临时数据",
                size = breakdown?.temp ?: 0L,
                onClear = onClearTemp,
            )
        }
    }
}

@Composable
private fun CacheDetailRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    size: Long,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Stone100)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryBrown, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Text(
                text = "$subtitle · ${CacheCenter.formatSize(size)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSoft,
            )
        }
        TextButton(onClick = onClear, enabled = size > 0L) {
            Text(
                text = "清理",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (size > 0L) PrimaryBrown else TextMuted,
            )
        }
    }
}

/** 清理确认弹窗（供 destination 复用，避免"点了就没了"的误操作） */
@Composable
fun CacheClearConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppConfirmDialog(
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        confirmText = "清理",
        onConfirm = onConfirm,
    )
}
