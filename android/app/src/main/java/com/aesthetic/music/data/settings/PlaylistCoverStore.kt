package com.aesthetic.music.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * 自建歌单封面（本机图片）落盘。
 *
 * 为什么不直接保存相册返回的 `content://` URI：该 URI 的读权限只在当次授权/当前进程内有效，
 * 重启或被系统回收后封面会全部变成空白。所以选图后立刻拷贝进应用私有目录，歌单里只存绝对路径。
 *
 * 拷贝时顺手做等比压缩：手机相册原图动辄 10MB 以上，作为 56dp 的小封面既浪费磁盘也拖慢加载。
 *
 * 与其它 Store 一样在 Application 创建时 [init]，因此删除/判断这类调用不需要再传 Context。
 */
object PlaylistCoverStore {

    private const val DIR_NAME = "playlist_covers"

    /** 压缩后的最长边（px）：远超 56dp/88dp 封面的实际显示需求，兼顾清晰度与体积 */
    private const val MAX_EDGE = 720
    private const val JPEG_QUALITY = 88

    /** 单张原图上限：超过直接放弃，避免把超大文件整个读进内存 */
    private const val MAX_SOURCE_BYTES = 48 * 1024 * 1024

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** 封面目录（未 init 时返回 null，调用方按"失败"处理） */
    private fun dir(): File? = appContext?.let { File(it.filesDir, DIR_NAME) }

    /**
     * 把系统选择器返回的图片拷贝进应用私有目录。
     * @return 落盘后的绝对路径；失败返回 null（调用方提示"封面设置失败"）
     */
    fun savePickedImage(uri: Uri): String? {
        val ctx = appContext ?: return null
        val bytes = try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } ?: return null

        if (bytes.isEmpty() || bytes.size > MAX_SOURCE_BYTES) return null

        val bitmap = try {
            decodeScaled(bytes)
        } catch (_: Exception) {
            null
        } ?: return null

        return try {
            val base = dir() ?: return null
            base.mkdirs()
            val target = File(base, "cover_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            target.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** 是否为应用私有目录内的本机封面（网络 URL 与外部路径一律 false） */
    fun isLocalCover(path: String?): Boolean {
        if (path.isNullOrBlank() || path.startsWith("http")) return false
        val root = try {
            dir()?.canonicalPath
        } catch (_: Exception) {
            null
        } ?: return false
        val canonical = try {
            File(path).canonicalPath
        } catch (_: Exception) {
            return false
        }
        return canonical.startsWith(root + File.separator)
    }

    /** 删除本机封面文件（歌单删除、封面被替换、选图后取消时调用）；网络 URL 与外部路径不动 */
    fun delete(path: String?) {
        if (!isLocalCover(path)) return
        try {
            File(path!!).delete()
        } catch (_: Exception) {
        }
    }

    /** 两遍解码：先测尺寸算采样率，再真正解码并按最长边等比缩放 */
    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_EDGE || bounds.outHeight / (sample * 2) >= MAX_EDGE) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= MAX_EDGE) return decoded

        val ratio = MAX_EDGE.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != decoded) decoded.recycle()
        return scaled
    }

    // ==================== 云端可同步封面（内联 data URL） ====================

    /** 内联封面的最长边：歌单卡最大显示 88dp，288px 已经足够清晰，体积控制在十几 KB */
    private const val SYNC_COVER_EDGE = 288

    /** 内联封面的 JPEG 质量：75% 在 288px 下肉眼无损，体积约为 88% 的一半 */
    private const val SYNC_COVER_QUALITY = 75

    /**
     * 把选中的图片压成 `data:image/jpeg;base64,...`，用于**跨设备同步**。
     *
     * 为什么要这么绕：本项目没有图片上传接口（后端只有 APK 上传），
     * 而歌单的 `cover` 字段本身是会随「歌单」这条记录同步到云端的字符串。
     * 所以把图片直接编码进这个字段——换设备登录就能看到同一张封面，不需要服务端配合。
     *
     * 失败返回 null（调用方回落到「仅本机路径」的行为，不影响选图本身）。
     */
    fun encodeCoverDataUrl(uri: Uri): String? {
        val ctx = appContext ?: return null
        val bytes = try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_SOURCE_BYTES) return null

        val bitmap = try {
            decodeScaledTo(bytes, SYNC_COVER_EDGE)
        } catch (_: Exception) {
            null
        } ?: return null

        return try {
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, SYNC_COVER_QUALITY, out)
            val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** 与 [decodeScaled] 相同，但最长边可指定（同步封面用小尺寸） */
    private fun decodeScaledTo(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxEdge || bounds.outHeight / (sample * 2) >= maxEdge) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxEdge) return decoded
        val ratio = maxEdge.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != decoded) decoded.recycle()
        return scaled
    }
}
