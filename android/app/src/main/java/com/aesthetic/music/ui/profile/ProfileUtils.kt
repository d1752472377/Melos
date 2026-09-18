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
     * 将本地图片压缩并编码为 Base64 Data URI（用于上传至服务端）。
     * 保证最长边 <= [maxDimension]，质量为 [quality]，避免大图塞爆数据库与网络流量。
     */
    fun compressImageToBase64(filePath: String, maxDimension: Int = 512, quality: Int = 75): String? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) return null

            val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(filePath, boundsOptions)
            val w = boundsOptions.outWidth
            val h = boundsOptions.outHeight
            if (w <= 0 || h <= 0) return null

            var sampleSize = 1
            val maxSide = maxOf(w, h)
            while (maxSide / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = android.graphics.BitmapFactory.decodeFile(filePath, decodeOptions) ?: return null

            val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            val finalBitmap = if (scale < 1.0f) {
                val sw = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val sh = (bitmap.height * scale).toInt().coerceAtLeast(1)
                android.graphics.Bitmap.createScaledBitmap(bitmap, sw, sh, true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            val baos = java.io.ByteArrayOutputStream()
            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, baos)
            finalBitmap.recycle()

            val bytes = baos.toByteArray()
            "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将云端下发的 Base64 Data URI 或 URL 解析并缓存到本地私有目录。
     * 若本身即为 HTTP/HTTPS 网络直链则直接返回。
     */
    fun saveBase64ImageToInternal(context: Context, dataUriOrUrl: String, prefix: String): String? {
        return try {
            if (dataUriOrUrl.isBlank()) return null
            if (dataUriOrUrl.startsWith("http://", ignoreCase = true) || dataUriOrUrl.startsWith("https://", ignoreCase = true)) {
                return dataUriOrUrl
            }
            val base64Str = if (dataUriOrUrl.contains(",")) {
                dataUriOrUrl.substringAfter(",")
            } else {
                dataUriOrUrl
            }
            val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            val dir = File(context.filesDir, "profile").apply { mkdirs() }
            // 清理旧缓存文件
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("${prefix}_cloud_")) {
                    file.delete()
                }
            }
            val file = File(dir, "${prefix}_cloud_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
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
