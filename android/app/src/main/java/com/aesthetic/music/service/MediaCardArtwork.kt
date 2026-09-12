package com.aesthetic.music.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.util.LruCache
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.aesthetic.music.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 媒体卡片的封面位图。
 *
 * 为什么必须有这一层：原来自定义 [MelosMediaNotificationProvider] 只设了标题文字，
 * 完全没有 `setLargeIcon` —— 于是通知栏卡片、锁屏、HyperOS 的超级岛读到的都是"没有封面"的媒体通知，
 * 表现就是用户看到的"卡片啥也没有"。media3 自带的 DefaultMediaNotificationProvider 有一整套
 * BitmapLoader 负责这件事，我们换成自定义 Provider 时把它一起丢了。
 *
 * 实现选择 Coil（App 内其它地方加载封面也用 Coil）：直接复用它的内存/磁盘缓存，
 * 不重复下载同一张封面，也不引第二套图片栈。
 *
 * 线程约定：`cached()` 可在任意线程调用；[prefetch] 在后台线程解码，完成回调回主线程。
 */
object MediaCardArtwork {

    private const val TAG = "MediaCardArtwork"

    /** 只缓存最近几张：通知卡片只会用到当前歌，多留几张是为了切歌来回跳时不反复解码 */
    private val cache = LruCache<String, Bitmap>(6)

    /** 编码后的 JPEG 字节缓存：避免每次切歌/重建通知都重新压缩一张图 */
    private val byteCache = LruCache<String, ByteArray>(6)

    /** 交给系统元数据的封面尺寸上限（Binder 事务有大小限制，不能传原图） */
    private const val ARTWORK_MAX_PX = 320

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    /** 通知大图标尺寸：512 足够清晰，又不会因为太大而在系统侧被截断或浪费内存 */
    private const val SIZE_PX = 512

    @Volatile
    private var pendingUrl: String? = null

    /**
     * 取已解码的封面（未就绪时返回 null，调用方照常渲染无封面的卡片）。
     *
     * 两级查找：
     * 1. 本对象的缓存（由 [prefetch] 填充）；
     * 2. **Coil 的内存缓存兜底** —— App 界面里的封面也是 Coil 加载的，
     *    从列表点歌时那张图通常已经在内存里，于是"第一首歌"也能立刻有封面，
     *    不用等我们的预取跑完。
     */
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

    /**
     * 预加载封面，解码完成后在主线程回调 [onReady]。
     *
     * 同一张封面已有缓存或正在加载时直接返回：通知会因播放状态变化被反复重建，
     * 每次重建都发一次解码请求是纯浪费。
     */
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
                        // 通知需要用软件位图：硬件位图在部分 ROM 的 SystemUI 里无法直接绘制
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
                Log.w(TAG, "封面加载失败: ${e.message}")
            } finally {
                synchronized(this@MediaCardArtwork) { if (pendingUrl == key) pendingUrl = null }
            }
        }
    }

    /**
     * 从封面取一个强调色，用于卡片的底色（`setColorized`）。
     *
     * 不引 Palette 库：通知底色不需要专业调色，把位图缩到 16x16 取平均即可，
     * 并且刻意往暗处压一点——系统在色底上用白字，太亮的底色会让文字糊掉。
     */
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
            // 压暗到 55%：保证白字对比度
            val dim = { v: Int -> (v * 0.55f).toInt().coerceIn(0, 255) }
            (0xFF shl 24) or (dim(avgR) shl 16) or (dim(avgG) shl 8) or dim(avgB)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 当前播放歌曲的封面（供悬浮胶囊等其它入口复用同一份缓存） */
    fun currentSongArtwork(context: Context): Bitmap? =
        cached(context, PlayerManager.get(context).state.value.currentSong?.artwork)

    /**
     * 把封面编码成可以塞进媒体会话元数据的字节（`MediaMetadata.setArtworkData`）。
     *
     * 为什么必须要字节而不是只给 URL：Android 13+ 的媒体卡片（通知栏/锁屏/超级岛）读的是
     * **MediaSession 的元数据**，而它拿到的 artworkUri 是我们歌曲里的 `https://...` ——
     * SystemUI 在另一个进程里，既没有我们的网络栈也没有权限读应用私有文件，
     * 于是封面显示不出来（卡片看起来"空的"）。artworkData 是随元数据一起过 Binder 的真实图片字节，
     * 各家系统都能直接解码。
     *
     * 尺寸压到 320px / JPEG 85：Binder 事务有大小限制（几百 KB 级），原图直传会抛
     * TransactionTooLargeException；320 足够卡片与锁屏清晰显示。
     *
     * @return 编码后的 JPEG 字节；没有缓存封面时返回 null（此时只有 artworkUri，封面可能不显示）
     */
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
            Log.w(TAG, "封面编码失败: ${e.message}")
            null
        }
    }

    /** 等比缩到最长边不超过 [maxPx]；已经足够小就原样返回 */
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
