package com.aesthetic.music.player

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 音频缓存服务（移植自参考 App 的 AudioCacheService）：
 * - 缓存目录 cacheDir/audio_cache，索引 _index.json
 * - 文件命名 "歌名-歌手.ext"，下载到 .tmp 成功后重命名
 * - 播放前查缓存命中（>10KB 才有效），命中直接播本地文件秒开；
 *   未命中播在线 URL，后台异步下载缓存（不阻塞播放）
 */
object AudioCacheService {

    private data class CacheEntry(
        val name: String,
        val artist: String,
        val platformCode: String,
        val songId: String,
        val filePath: String,
        val fileSize: Long,
    )

    private val gson = Gson()

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "audio_cache").apply { mkdirs() }

    private fun indexFile(context: Context) = File(cacheDir(context), "_index.json")

    private fun cacheKey(platformCode: String, songId: String) = "${platformCode}_$songId"

    private fun loadIndex(context: Context): MutableMap<String, CacheEntry> {
        return try {
            val f = indexFile(context)
            if (!f.exists()) return mutableMapOf()
            val type = object : TypeToken<Map<String, CacheEntry>>() {}.type
            gson.fromJson<Map<String, CacheEntry>>(f.readText(), type).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveIndex(context: Context, index: Map<String, CacheEntry>) {
        try {
            indexFile(context).writeText(gson.toJson(index))
        } catch (_: Exception) {
        }
    }

    private fun extractExt(url: String): String {
        val clean = url.substringBefore('?')
        val dot = clean.lastIndexOf('.')
        if (dot in 1 until clean.length - 1) {
            val ext = clean.substring(dot + 1).lowercase()
            if (ext in listOf("mp3", "flac", "m4a", "aac", "ogg", "wav", "ape")) return ext
        }
        return "mp3"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").replace(Regex("""\s+"""), " ").trim()

    /** 检查是否已有缓存，返回本地文件路径（不存在/过小返回 null） */
    fun getCachedPath(context: Context, platformCode: String, songId: String): String? {
        return try {
            val entry = loadIndex(context)[cacheKey(platformCode, songId)] ?: return null
            val f = File(entry.filePath)
            if (f.exists() && f.length() > 10240) entry.filePath else null
        } catch (_: Exception) {
            null
        }
    }

    /** 后台异步下载缓存（不阻塞调用方），完成后回调本地路径（失败回调 null） */
    fun cacheAudio(
        context: Context,
        platformCode: String,
        songId: String,
        url: String,
        name: String,
        artist: String,
        scope: CoroutineScope,
        onDone: (String?) -> Unit,
    ) {
        if (url.isBlank()) { onDone(null); return }
        scope.launch(Dispatchers.IO) {
            val dir = cacheDir(context)
            val ext = extractExt(url)
            val baseName = "${sanitize(name.ifBlank { "未知歌曲" })}-${sanitize(artist.ifBlank { "未知歌手" })}"
            val tmp = File(dir, "$baseName.$ext.tmp")
            var done: String? = null
            try {
                // 已有缓存直接返回
                getCachedPath(context, platformCode, songId)?.let { onDone(it); return@launch }

                tmp.delete()
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val resp = client.newCall(Request.Builder().url(url).build()).execute()
                if (!resp.isSuccessful) { tmp.delete(); onDone(null); return@launch }
                resp.body?.byteStream()?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }

                if (tmp.length() > 10240) {
                    var target = File(dir, "$baseName.$ext")
                    if (target.exists()) target = File(dir, "${sanitize(name)}-${sanitize(artist)}_$songId.$ext")
                    tmp.renameTo(target)
                    val index = loadIndex(context)
                    index[cacheKey(platformCode, songId)] = CacheEntry(name, artist, platformCode, songId, target.absolutePath, target.length())
                    saveIndex(context, index)
                    done = target.absolutePath
                } else {
                    tmp.delete()
                }
            } catch (_: Exception) {
                tmp.delete()
            }
            onDone(done)
        }
    }

    fun getCacheSize(context: Context): Long =
        try {
            cacheDir(context).listFiles()?.filter { it.isFile && it.name != "_index.json" }?.sumOf { it.length() } ?: 0
        } catch (_: Exception) { 0 }

    fun getCacheCount(context: Context): Int =
        try {
            loadIndex(context).values.count { File(it.filePath).exists() }
        } catch (_: Exception) { 0 }

    /** 缓存列表（用于缓存列表页展示），按大小降序 */
    fun listCached(context: Context): List<CachedSong> =
        try {
            loadIndex(context).values
                .mapNotNull { e ->
                    val f = File(e.filePath)
                    if (f.exists()) CachedSong(e.name, e.artist, f.length(), e.filePath) else null
                }
                .sortedByDescending { it.size }
        } catch (_: Exception) { emptyList() }

    /** 缓存列表条目（供 UI 展示） */
    data class CachedSong(
        val title: String,
        val artist: String,
        val size: Long,
        val filePath: String,
    )

    /** 删除单个缓存（按路径） */
    fun removeCacheByPath(path: String) {
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }

    fun clearCache(context: Context) {
        try {
            cacheDir(context).listFiles()?.forEach { if (it.isFile) it.delete() }
        } catch (_: Exception) {
        }
    }

    fun removeCache(context: Context, platformCode: String, songId: String) {
        try {
            val index = loadIndex(context)
            val entry = index.remove(cacheKey(platformCode, songId))
            entry?.let { File(it.filePath).delete() }
            saveIndex(context, index)
        } catch (_: Exception) {
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
