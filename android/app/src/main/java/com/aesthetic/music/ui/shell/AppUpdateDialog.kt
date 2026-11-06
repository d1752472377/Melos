package com.aesthetic.music.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.service.UpdateInfo
import com.aesthetic.music.service.UpdateService
import com.aesthetic.music.ui.components.AppDialog
import com.aesthetic.music.ui.theme.TextSoft
import kotlinx.coroutines.launch
import com.aesthetic.music.ui.theme.AppSpacing

@Composable
fun AppUpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateProgressText by remember(info) { mutableStateOf<String?>(null) }
    val isReady = remember(info, updateProgressText) { UpdateService.isApkReady(context, info) }

    AppDialog(
        onDismissRequest = {
            if (updateProgressText == null && !info.forceUpdate) {
                onDismiss()
            }
        },
        title = if (info.forceUpdate) "发现重要更新 ${info.versionName}" else "发现新版本 ${info.versionName}",
        confirmText = if (updateProgressText == null) {
            if (isReady) "立即安装" else "立即更新"
        } else null,
        onConfirm = {
            scope.launch {
                val alreadyDownloaded = UpdateService.isApkReady(context, info)
                updateProgressText = if (alreadyDownloaded) "正在打开安装程序…" else "准备下载…"
                try {
                    val path = UpdateService.downloadApk(context, info) { received, total ->
                        if (total > 0) {
                            updateProgressText = "下载中 ${received * 100 / total}%"
                        }
                    }
                    updateProgressText = "正在打开安装程序…"
                    if (UpdateService.installApk(context, path)) {
                        updateProgressText = null
                        if (!info.forceUpdate) onDismiss()
                    } else {
                        onError("请允许「安装未知应用」后重新安装")
                        updateProgressText = null
                        if (!info.forceUpdate) onDismiss()
                    }
                } catch (e: Exception) {
                    onError("更新失败: ${e.message ?: "未知错误"}")
                    updateProgressText = null
                    if (!info.forceUpdate) onDismiss()
                }
            }
        },
        cancelText = if (updateProgressText == null && !info.forceUpdate) "稍后再说" else null,
        onCancel = onDismiss,
    ) {
        Column {
            if (updateProgressText != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(AppSpacing.lg))
                    Text(updateProgressText ?: "正在处理…", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    text = if (info.updateLog.isNotBlank()) info.updateLog else "修复已知问题，提升使用体验。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(AppSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "大小: ${AudioCacheService.formatSize(info.apkSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                    )
                    if (isReady) {
                        Text(
                            text = "已就绪 (免流量)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
