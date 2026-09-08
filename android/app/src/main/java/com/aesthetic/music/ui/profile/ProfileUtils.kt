package com.aesthetic.music.ui.profile

import android.content.Context
import android.net.Uri
import java.io.File

object ProfileUtils {

    /**
     * 将通过系统选择器获取的图片 URI 拷贝到应用私有目录，确保跨进程和应用重启后永久具有读取权限。
     */
    fun saveImageToInternal(context: Context, uri: Uri, prefix: String): String? {
        return try {
            val dir = File(context.filesDir, "profile").apply { mkdirs() }
            // 清理同前缀旧文件，防止存储无限增长
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith(prefix)) {
                    file.delete()
                }
            }
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 格式化毫秒为友好时间（例如：2小时15分）
     */
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0分"
        val s = ms / 1000
        val m = s / 60
        val h = m / 60
        return if (h > 0) "${h}小时${m % 60}分" else "${m}分"
    }
}
