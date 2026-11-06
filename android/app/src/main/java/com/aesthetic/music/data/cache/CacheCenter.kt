package com.aesthetic.music.data.cache

import android.content.Context
import com.aesthetic.music.player.AudioCacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheCenter {

    private const val IMAGE_CACHE_DIR = "image_cache"

    private const val AUDIO_CACHE_DIR = "audio_cache"

    data class Breakdown(
        val music: Long = 0L,
        val images: Long = 0L,
        val temp: Long = 0L,
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
        val tempSize = (cacheTotal - musicSize - imageSize).coerceAtLeast(0L)
        Breakdown(music = musicSize, images = imageSize, temp = tempSize, songCount = songCount)
    }

    private fun imageCacheDir(app: Context): File = File(app.cacheDir, IMAGE_CACHE_DIR)

    suspend fun clearMusic(context: Context) = withContext(Dispatchers.IO) {
        AudioCacheService.clearCache(context.applicationContext)
    }

    suspend fun clearImages(context: Context) = withContext(Dispatchers.IO) {
        deleteContents(imageCacheDir(context.applicationContext))
    }

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

    fun formatSize(bytes: Long): String = AudioCacheService.formatSize(bytes)
}
