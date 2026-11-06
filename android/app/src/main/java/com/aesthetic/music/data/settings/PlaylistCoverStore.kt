package com.aesthetic.music.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

data class PlaylistCoverPick(
    val path: String,
    val dataUrl: String?,
)

object PlaylistCoverStore {

    private const val DIR_NAME = "playlist_covers"

    private const val MAX_EDGE = 720
    private const val JPEG_QUALITY = 88

    private const val MAX_SOURCE_BYTES = 48 * 1024 * 1024

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun dir(): File? = appContext?.let { File(it.filesDir, DIR_NAME) }

    fun pick(uri: Uri): PlaylistCoverPick? {
        val bytes = readSourceBytes(uri) ?: return null
        val path = savePickedImageBytes(bytes) ?: return null
        return PlaylistCoverPick(path = path, dataUrl = encodeCoverDataUrlBytes(bytes))
    }

    private fun readSourceBytes(uri: Uri): ByteArray? {
        val ctx = appContext ?: return null
        val bytes = try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } ?: return null
        return bytes.takeIf { it.isNotEmpty() && it.size <= MAX_SOURCE_BYTES }
    }

    fun savePickedImage(uri: Uri): String? =
        readSourceBytes(uri)?.let { savePickedImageBytes(it) }

    private fun savePickedImageBytes(bytes: ByteArray): String? {
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

    fun delete(path: String?) {
        if (!isLocalCover(path)) return
        try {
            File(path!!).delete()
        } catch (_: Exception) {
        }
    }

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

    private const val SYNC_COVER_EDGE = 288

    private const val SYNC_COVER_QUALITY = 75

    fun encodeCoverDataUrl(uri: Uri): String? =
        readSourceBytes(uri)?.let { encodeCoverDataUrlBytes(it) }

    private fun encodeCoverDataUrlBytes(bytes: ByteArray): String? {
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
