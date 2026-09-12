package com.aesthetic.music.data.cache

import android.content.Context
import com.aesthetic.music.player.AudioCacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 缓存中心：统一盘点与清理 App 的各类缓存。
 *
 * 分三类（对应设置页「缓存管理」抽屉里的三项）：
 * - [music] 下载/缓存音乐：`cacheDir/audio_cache`（[AudioCacheService] 的边听边下产物，也是逐曲列表的数据源）
 * - [images] 图片缓存：Coil 的磁盘缓存目录（封面、头像等）
 * - [temp] 临时文件：cacheDir 下除上面两者外的其余内容（临时下载分片、WebView 缓存等）
 *
 * 只统计/清理 App 私有目录，绝不触碰外部存储里的用户文件。
 */
object CacheCenter {

    /** 图片缓存目录名：Coil 2.x 默认落在 cacheDir/image_cache */
    private const val IMAGE_CACHE_DIR = "image_cache"

    /** 音乐缓存目录名：与 AudioCacheService 保持一致 */
    private const val AUDIO_CACHE_DIR = "audio_cache"

    data class Breakdown(
        val music: Long = 0L,
        val images: Long = 0L,
        val temp: Long = 0L,
        /** 已缓存歌曲数（与占用大小同一次扫描取到，避免界面分两次刷新数字对不上） */
        val songCount: Int = 0,
    ) {
        val total: Long get() = music + images + temp
    }

    /** 统计各类缓存占用（IO 线程遍历目录，文件可能在统计途中消失，全部容错） */
    suspend fun scan(context: Context): Breakdown = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val imageDir = imageCacheDir(app)
        val musicSize = AudioCacheService.getCacheSize(app)
        val songCount = AudioCacheService.getCacheCount(app)
        val imageSize = sizeOf(imageDir)
        val cacheTotal = sizeOf(app.cacheDir)
        // 临时文件 = cacheDir 总量 - 音乐 - 图片（相减得到，避免遗漏零散子目录）
        val tempSize = (cacheTotal - musicSize - imageSize).coerceAtLeast(0L)
        Breakdown(music = musicSize, images = imageSize, temp = tempSize, songCount = songCount)
    }

    /**
     * 图片缓存目录。
     *
     * Coil（2.x / 3.x 一致）默认把磁盘缓存放在 `cacheDir/image_cache`；
     * 本应用没有自定义 ImageLoader，因此直接按约定取目录，
     * 不引入 Coil 的磁盘缓存 API（其 directory 类型随版本在 File/okio.Path 之间变化）。
     */
    private fun imageCacheDir(app: Context): File = File(app.cacheDir, IMAGE_CACHE_DIR)

    /** 清理下载/缓存音乐（走 AudioCacheService，保证索引与文件同步删除） */
    suspend fun clearMusic(context: Context) = withContext(Dispatchers.IO) {
        AudioCacheService.clearCache(context.applicationContext)
    }

    /** 清理图片缓存：直接清空目录内容（Coil 会按需重建，缺失的条目自动回源） */
    suspend fun clearImages(context: Context) = withContext(Dispatchers.IO) {
        deleteContents(imageCacheDir(context.applicationContext))
    }

    /** 清理临时文件 */
    suspend fun clearTemp(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val keep = setOf(
            File(app.cacheDir, AUDIO_CACHE_DIR).absolutePath,
            imageCacheDir(app).absolutePath,
        )
        app.cacheDir.listFiles()?.forEach { child ->
            if (child.absolutePath !in keep) runCatching { child.deleteRecursively() }
        }
    }

    /** 清理全部三类缓存 */
    suspend fun clearAll(context: Context) {
        clearMusic(context)
        clearImages(context)
        clearTemp(context)
    }

    private fun sizeOf(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    private fun deleteContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
    }

    /** 人类可读大小（与 AudioCacheService.formatSize 同规则，便于两处显示一致） */
    fun formatSize(bytes: Long): String = AudioCacheService.formatSize(bytes)
}
