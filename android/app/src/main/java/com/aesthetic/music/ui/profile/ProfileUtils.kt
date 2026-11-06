package com.aesthetic.music.ui.profile

import android.content.Context
import android.net.Uri
import com.aesthetic.music.data.api.UserInfo
import java.io.File
import java.util.Calendar

object ProfileFormat {

    fun registerDate(createdAtMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
        if (createdAtMs <= 0L) return null
        val ms = if (createdAtMs > nowMs) nowMs else createdAtMs
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "${c.get(Calendar.YEAR)}年${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
    }

    fun registerDays(createdAtMs: Long, nowMs: Long = System.currentTimeMillis()): Long? {
        if (createdAtMs <= 0L) return null
        val ms = if (createdAtMs > nowMs) nowMs else createdAtMs
        return ((nowMs - ms) / 86_400_000L) + 1
    }

    fun isValidEmail(raw: String): Boolean {
        val s = raw.trim()
        if (s.length !in 3..120) return false
        if (s.any { it.isWhitespace() }) return false
        val at = s.indexOf('@')
        if (at <= 0 || at != s.lastIndexOf('@')) return false
        val local = s.substring(0, at)
        val domain = s.substring(at + 1)
        if (local.isEmpty() || domain.isEmpty()) return false
        if (!domain.contains('.')) return false
        if (domain.startsWith('.') || domain.endsWith('.') || domain.contains("..")) return false
        val tld = domain.substringAfterLast('.')
        return tld.length >= 2 && tld.all { it.isLetter() }
    }

    fun emailDisplay(email: String?): String =
        email?.trim()?.takeIf { it.isNotEmpty() } ?: "未绑定"

    fun isEmailBound(email: String?): Boolean = !email.isNullOrBlank()

    fun maskEmail(email: String?): String {
        val s = email?.trim().orEmpty()
        if (s.isEmpty() || !s.contains('@')) return "未绑定"
        val local = s.substringBefore('@')
        val domain = s.substringAfter('@')
        val head = local.take(2)
        return "$head***@$domain"
    }

    fun displayName(nickname: String?, username: String?, fallback: String = "Melos 音乐人"): String =
        nickname?.trim()?.takeIf { it.isNotEmpty() }
            ?: username?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallback

    fun usernameDisplay(username: String?): String =
        username?.trim()?.takeIf { it.isNotEmpty() } ?: "未登录"

    fun userIdDisplay(user: UserInfo?, fallback: String = "10001"): String =
        user?.id?.takeIf { it > 0 }?.toString() ?: fallback

    fun bioDisplay(bio: String?, placeholder: String = "还没有填写个性签名"): String =
        bio?.trim()?.takeIf { it.isNotEmpty() } ?: placeholder

    fun joinedSummary(createdAtMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
        val days = registerDays(createdAtMs, nowMs) ?: return null
        return "已加入 $days 天"
    }
}

object ProfileUtils {

    fun saveImageToInternal(context: Context, uri: Uri, prefix: String): String? {
        return try {
            val dir = File(context.filesDir, "profile").apply { mkdirs() }
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

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0分"
        val s = ms / 1000
        val m = s / 60
        val h = m / 60
        return if (h > 0) "${h}小时${m % 60}分" else "${m}分"
    }
}
