package com.aesthetic.music.ui.profile

import android.content.Context
import android.net.Uri
import com.aesthetic.music.data.api.UserInfo
import java.io.File
import java.util.Calendar



/**
 * 个人资料的纯格式化/校验逻辑（无 Android 依赖，可单测）。
 *
 * 抽出来的原因：这些都是"显示规则"，此前散落在 Compose 里（年月日拼接、邮箱判断、
 * 昵称兜底），只能靠肉眼看界面验证。放到这里之后每条规则都有单测兜住，
 * 界面只负责把返回值画出来。
 */
object ProfileFormat {

    /**
     * 注册日期文案：`2026年9月13日`；时间戳缺失/非法时返回 null（界面显示"未知"，而不是 1970 年）。
     *
     * [nowMs] 只为可测性注入，默认取当前时间；未来时间戳（服务端时钟偏差）按"刚刚"处理，
     * 避免出现"注册于 2027 年"这种明显荒谬的文案。
     */
    fun registerDate(createdAtMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
        if (createdAtMs <= 0L) return null
        val ms = if (createdAtMs > nowMs) nowMs else createdAtMs
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "${c.get(Calendar.YEAR)}年${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
    }

    /** 注册天数（用于"加入 X 天"）：不足一天算 1 天；时间戳非法返回 null */
    fun registerDays(createdAtMs: Long, nowMs: Long = System.currentTimeMillis()): Long? {
        if (createdAtMs <= 0L) return null
        val ms = if (createdAtMs > nowMs) nowMs else createdAtMs
        return ((nowMs - ms) / 86_400_000L) + 1
    }

    /**
     * 邮箱是否可提交。
     *
     * 刻意不做 RFC 级别的严格校验：邮箱的真实性只有"能不能收到信"才能证明，
     * 客户端过严的正则只会把合法但少见的地址（+ 号、多级域名、超长 TLD）挡在门外。
     * 这里只拦「明显不是邮箱」的输入，把拒绝的理由留给服务端。
     */
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

    /** 邮箱显示：未绑定统一文案，避免界面各处出现"null"/"/"这种脏字符 */
    fun emailDisplay(email: String?): String =
        email?.trim()?.takeIf { it.isNotEmpty() } ?: "未绑定"

    fun isEmailBound(email: String?): Boolean = !email.isNullOrBlank()

    /**
     * 邮箱脱敏展示（如 `ab***@qq.com`）：卡片这类一眼可见的位置不直接暴露完整地址。
     * 值不像邮箱时（老数据/脏数据）统一返回"未绑定"，而不是把原始字符串原样显示出来。
     */
    fun maskEmail(email: String?): String {
        val s = email?.trim().orEmpty()
        if (s.isEmpty() || !s.contains('@')) return "未绑定"
        val local = s.substringBefore('@')
        val domain = s.substringAfter('@')
        val head = local.take(2)
        return "$head***@$domain"
    }

    /**
     * 昵称显示：昵称 → 登录用户名 → 兜底文案。
     * 与 `UserStore.getNickname()` 的回落顺序一致，保证卡片与资料页显示同一个名字。
     */
    fun displayName(nickname: String?, username: String?, fallback: String = "Melos 音乐人"): String =
        nickname?.trim()?.takeIf { it.isNotEmpty() }
            ?: username?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallback

    /** 登录用户名展示：未登录时显示"未登录"（不是空串，界面不会留下一行空白） */
    fun usernameDisplay(username: String?): String =
        username?.trim()?.takeIf { it.isNotEmpty() } ?: "未登录"

    /** Melos ID 展示：0 或缺失时回落 [fallback]（与 UserStore.getUserId 的默认值保持一致） */
    fun userIdDisplay(user: UserInfo?, fallback: String = "10001"): String =
        user?.id?.takeIf { it > 0 }?.toString() ?: fallback

    /** 简介展示：空 → 占位文案（资料页把空签名显示成一行空白会让人以为界面坏了） */
    fun bioDisplay(bio: String?, placeholder: String = "还没有填写个性签名"): String =
        bio?.trim()?.takeIf { it.isNotEmpty() } ?: placeholder

    /** 统计摘要：加入天数（如 `已加入 128 天`），时间戳非法返回 null */
    fun joinedSummary(createdAtMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
        val days = registerDays(createdAtMs, nowMs) ?: return null
        return "已加入 $days 天"
    }
}


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
