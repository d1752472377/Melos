package com.aesthetic.music.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.util.LruCache
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MediaCardArtwork {

    private const val TAG = "MediaCardArtwork"

    private val cache = LruCache<String, Bitmap>(6)

    private val byteCache = LruCache<String, ByteArray>(6)

    /** 交给系统元数据的封面尺寸上限（Binder 事务有大小限制，不能传原图） */
    private const val ARTWORK_MAX_PX = 320

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private const val SIZE_PX = 512

    @Volatile
    private var pendingUrl: String? = null

    @Synchronized
    fun cached(context: Context, url: String?): Bitmap? {
        val key = url?.takeIf { it.isNotBlank() } ?: return null
        cache.get(key)?.let { return it }
        val fromCoil = runCatching {
            val bitmap = context.applicationContext.imageLoader.memoryCache
                ?.get(MemoryCache.Key(key))
                ?.bitmap
                ?: return@runCatching null
            // 硬件位图不能跨进程绘制、也不能压成 JPEG，取一份软件副本
            if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
        }.getOrNull()
        if (fromCoil != null) cache.put(key, fromCoil)
        return fromCoil
    }

    @Synchronized
    fun prefetch(context: Context, url: String?, onReady: () -> Unit) {
        val key = url?.takeIf { it.isNotBlank() } ?: return
        if (cache.get(key) != null || pendingUrl == key) return
        pendingUrl = key
        job?.cancel()
        job = scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val request = ImageRequest.Builder(context.applicationContext)
                        .data(key)
                        .size(SIZE_PX, SIZE_PX)
                        .allowHardware(false)
                        .build()
                    val result = context.applicationContext.imageLoader.execute(request)
                    (result.drawable as? BitmapDrawable)?.bitmap
                }
                if (bitmap != null) {
                    synchronized(this@MediaCardArtwork) { cache.put(key, bitmap) }
                    onReady()
                }
            } catch (e: Exception) {
                LogReporter.w("MediaCardArtwork", "封面加载失败", e)
            } finally {
                synchronized(this@MediaCardArtwork) { if (pendingUrl == key) pendingUrl = null }
            }
        }
    }

    fun accentColor(bitmap: Bitmap?): Int? {
        val source = bitmap ?: return null
        return try {
            val small = Bitmap.createScaledBitmap(source, 16, 16, true)
            var r = 0L
            var g = 0L
            var b = 0L
            var count = 0
            for (x in 0 until small.width) {
                for (y in 0 until small.height) {
                    val px = small.getPixel(x, y)
                    val alpha = (px ushr 24) and 0xFF
                    if (alpha < 128) continue
                    r += (px shr 16) and 0xFF
                    g += (px shr 8) and 0xFF
                    b += px and 0xFF
                    count++
                }
            }
            if (count == 0) return null
            val avgR = (r / count).toInt().coerceIn(0, 255)
            val avgG = (g / count).toInt().coerceIn(0, 255)
            val avgB = (b / count).toInt().coerceIn(0, 255)
            val dim = { v: Int -> (v * 0.55f).toInt().coerceIn(0, 255) }
            (0xFF shl 24) or (dim(avgR) shl 16) or (dim(avgG) shl 8) or dim(avgB)
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun artworkBytes(context: Context, url: String?): ByteArray? {
        val key = url?.takeIf { it.isNotBlank() } ?: return null
        byteCache.get(key)?.let { return it }
        val bitmap = cached(context, key) ?: return null
        return try {
            val scaled = scaleDown(bitmap, ARTWORK_MAX_PX)
            val stream = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val bytes = stream.toByteArray()
            byteCache.put(key, bytes)
            bytes
        } catch (e: Exception) {
            LogReporter.w("MediaCardArtwork", "封面编码失败", e)
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxPx: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxPx) return bitmap
        val ratio = maxPx.toFloat() / longest.toFloat()
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    fun clear() {
        cache.evictAll()
        byteCache.evictAll()
    }
}
