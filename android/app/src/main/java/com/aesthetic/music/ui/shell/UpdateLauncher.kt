package com.aesthetic.music.ui.shell

import android.content.Context
import com.aesthetic.music.service.UpdateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 检查更新 → 下载 → 打开安装程序（从 AppShell 内联的 Settings 逻辑抽出）。
 * 进度文案经 [onResult] 回传；下载异常额外经 [onError] 走一次 Snackbar。
 */
fun launchUpdateCheck(
    context: Context,
    scope: CoroutineScope,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
) {
    scope.launch {
        when (val result = UpdateService.checkUpdate()) {
            is UpdateService.CheckResult.Available -> {
                val info = result.info
                val isReady = UpdateService.isApkReady(context, info)
                if (isReady) {
                    onResult("安装包已就绪，正在打开安装程序…")
                } else {
                    onResult("发现新版本 ${info.versionName}，开始下载…")
                }
                try {
                    val path = UpdateService.downloadApk(context, info) { received, total ->
                        if (total > 0) onResult("下载中 ${received * 100 / total}%")
                    }
                    onResult("正在打开安装程序…")
                    if (UpdateService.installApk(context, path)) {
                        onResult("已打开安装程序")
                    } else {
                        onResult("请允许「安装未知应用」后重新下载安装")
                    }
                } catch (e: Exception) {
                    onResult("更新失败：${e.message ?: "未知错误"}")
                    onError("更新失败: ${e.message}")
                }
            }

            is UpdateService.CheckResult.UpToDate -> onResult("当前已是最新版本")

            is UpdateService.CheckResult.Failed ->
                onResult("检查更新失败: ${result.reason ?: "网络错误"}")
        }
    }
}
