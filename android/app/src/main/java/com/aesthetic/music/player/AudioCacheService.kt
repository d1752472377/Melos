package com.aesthetic.music.player

import android.content.Context
import com.aesthetic.music.data.settings.DownloadFailurePolicy
import com.aesthetic.music.data.settings.DownloadNaming
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.UserNotice
import com.aesthetic.music.service.LogReporter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object AudioCacheService {

    private const val INDEX_FILE_NAME = "_index.json"

    private val KNOWN_PLATFORMS = setOf("kuwo", "netease", "chksz", "qq", "kugou")

    private data class CacheEntry(
        val name: String,
        val artist: String,
        val platformCode: String,
        val songId: String,
        val filePath: String,
        val fileSize: Long,
        val manual: Boolean = false,
    )

    private val gson = Gson()

    /** 共享 OkHttp 客户端：此前每次下载都 new 一个 client，线程池/连接池全浪费 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val downloadJobs = HashMap<String, kotlinx.coroutines.Job>()

    @Volatile
    private var protectedPath: String? = null

    fun setProtectedPath(path: String?) {
        protectedPath = path
    }

    /** 索引内存缓存（@Volatile 保证跨线程可见，配合 indexLock 使用） */
    @Volatile
    private var indexCache: MutableMap<String, CacheEntry>? = null
    private val indexLock = Any()

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "audio_cache").apply { mkdirs() }

    private fun indexFile(context: Context) = File(cacheDir(context), INDEX_FILE_NAME)

    private fun cacheKey(platformCode: String, songId: String, quality: String) =
        "$platformCode\u0001$songId\u0001$quality"

    private fun loadIndexFromDisk(context: Context): MutableMap<String, CacheEntry> {
        return try {
            val f = indexFile(context)
            if (!f.exists()) return mutableMapOf()
            val type = object : TypeToken<Map<String, CacheEntry>>() {}.type
            val parsed = gson.fromJson<Map<String, CacheEntry>>(f.readText(), type) ?: emptyMap()
            // 兼容旧版 "_" 分隔的 key（仅当平台段是已知平台 id 时迁移，否则保留原样）
            val migrated = mutableMapOf<String, CacheEntry>()
            for ((k, v) in parsed) {
                if (k.contains('\u0001')) {
                    migrated[k] = v
                } else {
                    val platform = k.substringBefore('_')
                    val rest = k.substringAfter('_', "")
                    if (platform in KNOWN_PLATFORMS && rest.isNotEmpty()) {
                        migrated["$platform\u0001$rest"] = v
                    } else {
                        migrated[k] = v
                    }
                }
            }
            migrated
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

    private fun mutateIndex(context: Context, block: (MutableMap<String, CacheEntry>) -> Unit) {
        synchronized(indexLock) {
            val index = getIndexLocked(context)
            block(index)
            try {
                indexFile(context).writeText(gson.toJson(index))
            } catch (e: Exception) {
                LogReporter.w("AudioCache", "缓存索引写入失败", e)
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

    private fun truncateBytes(s: String, maxBytes: Int): String {
        val bytes = s.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return s
        val sb = StringBuilder()
        var used = 0
        for (ch in s) {
            val b = ch.toString().toByteArray(Charsets.UTF_8).size
            if (used + b > maxBytes) break
            sb.append(ch)
            used += b
        }
        return sb.toString()
    }

    private fun sanitize(name: String, maxBytes: Int = 60): String =
        // 注意：raw string 不做 \uXXXX 转义，控制字符范围必须用普通字符串
        truncateBytes(
            name.replace(Regex("[\\\\/:*?\"<>|\u0000-\u001f\u007f]"), "_")
                .replace(Regex("""\s+"""), " ")
                .trim(),
            maxBytes,
        )

    private fun baseFileName(name: String, artist: String, songId: String): String {
        val title = sanitize(name.ifBlank { "未知歌曲" }, 60)
        val singer = sanitize(artist.ifBlank { "未知歌手" }, 40)
        val id = songId.replace(Regex("""[^\w-]"""), "_").take(20).ifBlank { "x" }
        val stem = when (SettingsStore.readPrefs().downloadNaming) {
            DownloadNaming.TITLE -> title
            DownloadNaming.ARTIST_TITLE -> "$singer-$title"
            DownloadNaming.TITLE_ARTIST -> "$title-$singer"
        }
        return "$stem-$id"
    }

    fun getCachedPath(context: Context, platformCode: String, songId: String, quality: String): String? {
        return try {
            val entry = queryIndex(context) { it[cacheKey(platformCode, songId, quality)] } ?: return null
            val f = File(entry.filePath)
            if (f.exists() && f.length() > 10240) entry.filePath else null
        } catch (_: Exception) {
            null
        }
    }

    fun cacheAudio(
        context: Context,
        platformCode: String,
        songId: String,
        url: String,
        name: String,
        artist: String,
        quality: String,
        scope: CoroutineScope,
        manual: Boolean = false,
        onDone: (String?) -> Unit,
    ) {
        if (url.isBlank()) { onDone(null); return }
        val prefs = SettingsStore.readPrefs()
        if (!DownloadPolicy.allowed(context, prefs, manual)) {
            onDone(null)
            return
        }
        val key = cacheKey(platformCode, songId, quality)
        synchronized(indexLock) {
            downloadJobs[key]?.cancel()
        }
        val job = scope.launch(Dispatchers.IO) {
            val dir = cacheDir(context)
            val ext = extractExt(url)
            val baseName = baseFileName(name, artist, songId)
            var done: String? = null
            val maxAttempts =
                if (prefs.downloadFailurePolicy == DownloadFailurePolicy.RETRY) 3 else 1
            var lastError: Throwable? = null
            // 记录本协程 Job：不能引用尚未初始化完成的外层 val（编译器拒绝自引用）；
            // 清理逻辑也用到它，声明必须提到 try 之前
            val selfJob = coroutineContext[kotlinx.coroutines.Job]
            try {
                getCachedPath(context, platformCode, songId, quality)?.let { onDone(it); return@launch }
                synchronized(indexLock) { if (selfJob != null) downloadJobs[key] = selfJob }

                var attempt = 0
                while (attempt < maxAttempts && done == null) {
                    attempt++
                    val tmp = File(dir, "$baseName.$ext.tmp-${System.nanoTime()}")
                    try {
                        tmp.delete()
                        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                tmp.delete()
                            } else {
                                resp.body?.byteStream()?.use { input ->
                                    tmp.outputStream().use { output -> input.copyTo(output) }
                                }
                                if (tmp.length() > 10240) {
                                    val target = File(dir, "$baseName.$ext")
                                    if (target.exists()) target.delete()
                                    if (tmp.renameTo(target)) {
                                        mutateIndex(context) {
                                            it[key] = CacheEntry(
                                                name, artist, platformCode, songId,
                                                target.absolutePath, target.length(),
                                                manual = manual,
                                            )
                                        }
                                        enforceCacheLimitIfNeeded(context)
                                        done = target.absolutePath
                                    } else {
                                        tmp.delete()
                                    }
                                } else {
                                    tmp.delete()
                                }
                            }
                        }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        tmp.delete()
                        throw ce
                    } catch (e: Exception) {
                        lastError = e
                        tmp.delete()
                    }
                    if (done == null && attempt < maxAttempts) delay(800L * attempt)
                }
            } finally {
                synchronized(indexLock) { if (downloadJobs[key] === selfJob) downloadJobs.remove(key) }
            }
            if (done == null) {
                LogReporter.w(
                    "AudioCache",
                    "缓存失败 song=$platformCode:$songId quality=$quality manual=$manual attempts=$maxAttempts",
                    lastError,
                )
            }
            if (done == null && prefs.downloadFailurePolicy == DownloadFailurePolicy.NOTIFY) {
                UserNotice.post("缓存失败：「${name.ifBlank { songId }}」，可在下载设置里调整失败策略")
            }
            onDone(done)
        }
    }

    private fun enforceCacheLimitIfNeeded(context: Context) {
        try {
            val files = cacheDir(context).listFiles()
                ?.filter { it.isFile && it.name != INDEX_FILE_NAME }
                ?: return
            var total = files.sumOf { it.length() }
            val limit = SettingsStore.readPrefs().cacheLimitBytes
            if (total <= limit) return
            synchronized(indexLock) {
                val index = getIndexLocked(context)
                val keyByPath = HashMap<String, String>(index.size)
                for ((k, v) in index) keyByPath[v.filePath] = k
                for (f in files.sortedBy { it.lastModified() }) {
                    if (total <= limit) break
                    if (f.absolutePath == protectedPath) continue
                    val size = f.length()
                    if (f.delete()) {
                        total -= size
                        keyByPath.remove(f.absolutePath)?.let { index.remove(it) }
                    }
                }
                try {
                    indexFile(context).writeText(gson.toJson(index))
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            LogReporter.w("AudioCache", "缓存容量清理失败", e)
        }
    }

    /** 用户调整缓存上限后立即执行一次容量清理（IO 线程调用） */
    fun enforceCacheLimit(context: Context) = enforceCacheLimitIfNeeded(context)

    fun getCacheSize(context: Context): Long =
        try {
            cacheDir(context).listFiles()?.filter { it.isFile && it.name != INDEX_FILE_NAME }?.sumOf { it.length() } ?: 0
        } catch (_: Exception) { 0 }

    fun getCacheCount(context: Context): Int =
        try {
            queryIndex(context) { it.values.toList() }.count { File(it.filePath).exists() }
        } catch (_: Exception) { 0 }

    fun listCached(context: Context): List<CachedSong> =
        try {
            queryIndex(context) { it.values.toList() }
                .mapNotNull { e ->
                    val f = File(e.filePath)
                    if (f.exists()) {
                        CachedSong(
                            title = e.name,
                            artist = e.artist,
                            size = f.length(),
                            filePath = e.filePath,
                            platformCode = e.platformCode,
                            songId = e.songId,
                            manual = e.manual,
                        )
                    } else null
                }
                .sortedByDescending { it.size }
        } catch (_: Exception) { emptyList() }

    data class CachedSong(
        val title: String,
        val artist: String,
        val size: Long,
        val filePath: String,
        val platformCode: String = "",
        val songId: String = "",
        val manual: Boolean = false,
    )

    fun removeCacheByPath(context: Context, path: String) {
        try {
            File(path).delete()
            var removed: List<CacheEntry> = emptyList()
            mutateIndex(context) { index ->
                removed = index.entries.filter { it.value.filePath == path }.map { it.value }
                index.entries.removeAll { it.value.filePath == path }
            }
            recordDownloadDeletes(removed)
        } catch (_: Exception) {
        }
    }

    fun clearCache(context: Context) {
        val removed = try { queryIndex(context) { it.values.toList() } } catch (_: Exception) { emptyList() }
        try {
            cacheDir(context).listFiles()?.forEach { if (it.isFile) it.delete() }
        } catch (_: Exception) {
        }
        synchronized(indexLock) {
            indexCache = mutableMapOf()
            try {
                indexFile(context).writeText(gson.toJson(emptyMap<String, CacheEntry>()))
            } catch (_: Exception) {
            }
        }
        recordDownloadDeletes(removed)
    }

    fun removeCache(context: Context, platformCode: String, songId: String, quality: String) {
        try {
            var removed: CacheEntry? = null
            mutateIndex(context) { index ->
                removed = index.remove(cacheKey(platformCode, songId, quality))
                removed?.let { File(it.filePath).delete() }
            }
            removed?.let { recordDownloadDeletes(listOf(it)) }
        } catch (_: Exception) {
        }
    }

    private fun recordDownloadDeletes(entries: List<CacheEntry>) {
        val valid = entries.filter { it.platformCode.isNotBlank() && it.songId.isNotBlank() }
        if (valid.isEmpty()) return
        val deviceId = com.aesthetic.music.data.sync.SyncStateStore.deviceId()
        com.aesthetic.music.data.sync.SyncStateStore.recordDeletesForCurrentUser(
            com.aesthetic.music.data.sync.SyncCollections.DOWNLOADS,
            valid.map { com.aesthetic.music.data.sync.SyncKey.download(deviceId, it.platformCode, it.songId) },
        )
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
