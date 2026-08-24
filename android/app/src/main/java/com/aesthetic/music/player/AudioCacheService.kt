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
 * - 索引常驻内存：首次访问读盘一次，之后所有查询零磁盘 I/O，
 *   写入同步落盘 + 更新内存，全程单把锁保证一致
 * - 缓存总量超上限（默认 2GB）时，按最后修改时间从旧到新自动清理
 */
object AudioCacheService {

    private const val INDEX_FILE_NAME = "_index.json"

    /** 缓存目录总大小上限：超过后自动删除最旧的音频文件 */
    private const val MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024 // 2GB

    private data class CacheEntry(
        val name: String,
        val artist: String,
        val platformCode: String,
        val songId: String,
        val filePath: String,
        val fileSize: Long,
    )

    private val gson = Gson()

    /** 索引内存缓存（@Volatile 保证跨线程可见，配合 indexLock 使用） */
    @Volatile
    private var indexCache: MutableMap<String, CacheEntry>? = null
    private val indexLock = Any()

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "audio_cache").apply { mkdirs() }

    private fun indexFile(context: Context) = File(cacheDir(context), INDEX_FILE_NAME)

    private fun cacheKey(platformCode: String, songId: String) = "${platformCode}_$songId"

    private fun loadIndexFromDisk(context: Context): MutableMap<String, CacheEntry> {
        return try {
            val f = indexFile(context)
            if (!f.exists()) return mutableMapOf()
            val type = object : TypeToken<Map<String, CacheEntry>>() {}.type
            gson.fromJson<Map<String, CacheEntry>>(f.readText(), type).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    /** 取索引（优先内存缓存）；必须在持有 indexLock 时调用 */
    private fun getIndexLocked(context: Context): MutableMap<String, CacheEntry> {
        indexCache?.let { return it }
        val loaded = loadIndexFromDisk(context)
        indexCache = loaded
        return loaded
    }

    /** 锁内只读查询：block 中不要做耗时磁盘操作，需要时先把结果拷出去 */
    private fun <T> queryIndex(context: Context, block: (Map<String, CacheEntry>) -> T): T =
        synchronized(indexLock) { block(getIndexLocked(context)) }

    /** 锁内修改索引并落盘（落盘失败不影响内存一致性） */
    private fun mutateIndex(context: Context, block: (MutableMap<String, CacheEntry>) -> Unit) {
        synchronized(indexLock) {
            val index = getIndexLocked(context)
            block(index)
            try {
                indexFile(context).writeText(gson.toJson(index))
            } catch (_: Exception) {
            }
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

    /** 检查是否已有缓存，返回本地文件路径（不存在/过小返回 null）。索引走内存，无重复读盘 */
    fun getCachedPath(context: Context, platformCode: String, songId: String): String? {
        return try {
            val entry = queryIndex(context) { it[cacheKey(platformCode, songId)] } ?: return null
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
                    mutateIndex(context) {
                        it[cacheKey(platformCode, songId)] =
                            CacheEntry(name, artist, platformCode, songId, target.absolutePath, target.length())
                    }
                    enforceCacheLimitIfNeeded(context)
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

    /**
     * 缓存总量超上限时，把音频文件按最后修改时间从旧到新删除，
     * 直到回到上限以内；被删文件同步从索引中移除。IO 线程调用。
     */
    private fun enforceCacheLimitIfNeeded(context: Context) {
        try {
            val files = cacheDir(context).listFiles()
                ?.filter { it.isFile && it.name != INDEX_FILE_NAME }
                ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_CACHE_BYTES) return
            synchronized(indexLock) {
                val index = getIndexLocked(context)
                for (f in files.sortedBy { it.lastModified() }) {
                    if (total <= MAX_CACHE_BYTES) break
                    val size = f.length()
                    if (f.delete()) {
                        total -= size
                        index.entries.removeAll { it.value.filePath == f.absolutePath }
                    }
                }
                try {
                    indexFile(context).writeText(gson.toJson(index))
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    fun getCacheSize(context: Context): Long =
        try {
            cacheDir(context).listFiles()?.filter { it.isFile && it.name != INDEX_FILE_NAME }?.sumOf { it.length() } ?: 0
        } catch (_: Exception) { 0 }

    fun getCacheCount(context: Context): Int =
        try {
            queryIndex(context) { it.values.toList() }.count { File(it.filePath).exists() }
        } catch (_: Exception) { 0 }

    /** 缓存列表（用于缓存列表页展示），按大小降序 */
    fun listCached(context: Context): List<CachedSong> =
        try {
            queryIndex(context) { it.values.toList() }
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

    /** 删除单个缓存（按路径），并同步清掉索引条目避免残留 */
    fun removeCacheByPath(context: Context, path: String) {
        try {
            File(path).delete()
            mutateIndex(context) { index ->
                index.entries.removeAll { it.value.filePath == path }
            }
        } catch (_: Exception) {
        }
    }

    fun clearCache(context: Context) {
        try {
            cacheDir(context).listFiles()?.forEach { if (it.isFile) it.delete() }
        } catch (_: Exception) {
        }
        synchronized(indexLock) { indexCache = mutableMapOf() }
    }

    fun removeCache(context: Context, platformCode: String, songId: String) {
        try {
            mutateIndex(context) { index ->
                index.remove(cacheKey(platformCode, songId))?.let { File(it.filePath).delete() }
            }
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
