package com.aesthetic.music.player

import android.content.Context
import com.aesthetic.music.data.settings.DownloadFailurePolicy
import com.aesthetic.music.data.settings.DownloadNaming
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.UserNotice
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

    /** 平台 id（管理后台可自定义，可能含下划线），用作 key 分隔符的平台白名单 */
    private val KNOWN_PLATFORMS = setOf("kuwo", "netease", "chksz", "qq", "kugou")

    private data class CacheEntry(
        val name: String,
        val artist: String,
        val platformCode: String,
        val songId: String,
        val filePath: String,
        val fileSize: Long,
        /** true = 用户主动点下载（进「我的下载」页）；false = 播放时边听边下的缓存 */
        val manual: Boolean = false,
    )

    private val gson = Gson()

    /** 共享 OkHttp 客户端：此前每次下载都 new 一个 client，线程池/连接池全浪费 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 进行中的缓存下载（key=缓存键）：切歌时取消上一首的下载，
     *  此前每次 cacheAudio 独立 launch 且不可取消，快速切歌会堆积并发整曲下载抢带宽 */
    private val downloadJobs = HashMap<String, kotlinx.coroutines.Job>()

    /** 当前正在播放的缓存文件路径：容量清理时跳过，
     *  防止"正在播 A（本地缓存）、同时后台下载 B 触发容量清理"时把 A 删掉导致读失败 */
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

    /**
     * 索引 key：platform / songId / quality 用 \u0001 分隔（各字段中都不会出现该字符），
     * 避免平台或歌曲 id 含下划线时的 key 碰撞；加音质维度后，
     * 切换音质设置不会误用旧音质缓存文件（直链按音质区分，文件内容不同）。
     */
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

    /** 按 UTF-8 字节预算截断（ext4 文件名上限 255 字节；CJK 每字占 3 字节，
     *  此前按字符 take(60) 裁剪，"60 汉字歌名 + 60 汉字歌手"=360 字节必然超限，
     *  中文长歌名缓存静默 100% 失败） */
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

    /** 文件名校净化：非法字符 + 控制字符替换、空白折叠、按字节预算裁剪 */
    private fun sanitize(name: String, maxBytes: Int = 60): String =
        // 注意：raw string 不做 \uXXXX 转义，控制字符范围必须用普通字符串
        truncateBytes(
            name.replace(Regex("[\\\\/:*?\"<>|\u0000-\u001f\u007f]"), "_")
                .replace(Regex("""\s+"""), " ")
                .trim(),
            maxBytes,
        )

    /** 缓存文件基础名：按「下载设置 → 缓存文件命名」生成，末尾附 songId 防同名覆盖。
     *  字节预算：标题 60 + '-' + 歌手 40 + '-' + id 20 + 扩展名 5 = 126 字节，远低于 255 上限 */
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

    /** 检查是否已有缓存，返回本地文件路径（不存在/过小返回 null）。索引走内存，无重复读盘 */
    fun getCachedPath(context: Context, platformCode: String, songId: String, quality: String): String? {
        return try {
            val entry = queryIndex(context) { it[cacheKey(platformCode, songId, quality)] } ?: return null
            val f = File(entry.filePath)
            if (f.exists() && f.length() > 10240) entry.filePath else null
        } catch (_: Exception) {
            null
        }
    }

    /** 后台异步下载缓存（不阻塞调用方），完成后回调本地路径（失败回调 null）。
     *  同一 key 再次缓存时先取消上一次未完成的下载 */
    fun cacheAudio(
        context: Context,
        platformCode: String,
        songId: String,
        url: String,
        name: String,
        artist: String,
        quality: String,
        scope: CoroutineScope,
        /** true = 用户主动下载：不受「边听边下」限制，但仍受「仅 WiFi」限制 */
        manual: Boolean = false,
        onDone: (String?) -> Unit,
    ) {
        if (url.isBlank()) { onDone(null); return }
        // 省流闸门：所有缓存入口都经过这里，避免某条路径绕过「边听边下 / 仅 WiFi」
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
            // 记录本协程 Job：不能引用尚未初始化完成的外层 val（编译器拒绝自引用）；
            // 清理逻辑也用到它，声明必须提到 try 之前
            val selfJob = coroutineContext[kotlinx.coroutines.Job]
            try {
                // 已有缓存直接返回
                getCachedPath(context, platformCode, songId, quality)?.let { onDone(it); return@launch }
                synchronized(indexLock) { if (selfJob != null) downloadJobs[key] = selfJob }

                // 失败策略：自动重试 = 首次 + 最多 2 次重试（递增退避）；其余策略只尝试一次
                val maxAttempts =
                    if (prefs.downloadFailurePolicy == DownloadFailurePolicy.RETRY) 3 else 1
                var attempt = 0
                while (attempt < maxAttempts && done == null) {
                    attempt++
                    // tmp 文件名带时间戳：同一首歌并发缓存时互不干扰（旧实现共用同名 tmp 会互相覆盖）
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
                                    // 同名目标已存在（并发缓存同一首歌）：先删除再重命名，保证最终文件完整
                                    if (target.exists()) target.delete()
                                    if (tmp.renameTo(target)) {
                                        mutateIndex(context) {
                                            it[key] = CacheEntry(
                                                name, artist, platformCode, songId,
                                                target.absolutePath, target.length(),
                                                manual = manual,
                                            )
                                        }
                                        // 容量清理时保护"正在播放"的文件（调用方传入当前播放路径）
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
                    } catch (_: Exception) {
                        tmp.delete()
                    }
                    if (done == null && attempt < maxAttempts) delay(800L * attempt)
                }
            } finally {
                synchronized(indexLock) { if (downloadJobs[key] === selfJob) downloadJobs.remove(key) }
            }
            // 「提示我」策略下失败要说话；其余策略静默（避免连播时刷屏）
            if (done == null && prefs.downloadFailurePolicy == DownloadFailurePolicy.NOTIFY) {
                UserNotice.post("缓存失败：「${name.ifBlank { songId }}」，可在下载设置里调整失败策略")
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
            // 上限可由用户在「缓存管理」页调整（默认 2GB）
            val limit = SettingsStore.getCacheLimitBytes()
            if (total <= limit) return
            synchronized(indexLock) {
                val index = getIndexLocked(context)
                // 预建 filePath -> key 映射，删除循环 O(1) 定位（旧写法 removeAll 是 O(n²)）
                val keyByPath = HashMap<String, String>(index.size)
                for ((k, v) in index) keyByPath[v.filePath] = k
                for (f in files.sortedBy { it.lastModified() }) {
                    if (total <= limit) break
                    // 跳过正在播放的文件（它同时是"最旧"的可能：整首播完前 lastModified 不更新）
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
        } catch (_: Exception) {
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

    /** 缓存列表（用于缓存列表页展示），按大小降序 */
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

    /** 缓存列表条目（供 UI 展示） */
    data class CachedSong(
        val title: String,
        val artist: String,
        val size: Long,
        val filePath: String,
        val platformCode: String = "",
        val songId: String = "",
        /** true = 用户主动下载；false = 播放时产生的缓存 */
        val manual: Boolean = false,
    )

    /** 删除单个缓存（按路径），并同步清掉索引条目避免残留 */
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
        // 先取删除前的索引：清空缓存是用户显式动作，要把这些歌的下载记录作为墓碑同步出去；
        // 清完再取就一条也拿不到了（"云端记录永远删不掉"就是这么来的）
        val removed = try { queryIndex(context) { it.values.toList() } } catch (_: Exception) { emptyList() }
        try {
            cacheDir(context).listFiles()?.forEach { if (it.isFile) it.delete() }
        } catch (_: Exception) {
        }
        // 同步清空内存索引并回写磁盘 _index.json：此前只清内存，冷启动后旧索引被重新加载
        //（虽然每条都会经 exists() 校验自愈，但 count/list 在自愈前短暂漂移）
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

    /**
     * 把"用户删掉的缓存条目"记成云同步墓碑（下载记录以 设备id + 平台 + 歌曲id 为键）。
     *
     * 只在**用户显式删除**的入口调用：容量上限淘汰（trim）虽然也删文件，
     * 但那是本地空间管理的副作用，不代表"我不想再要这首歌"，不该把别的设备的记录一起删掉。
     */
    private fun recordDownloadDeletes(entries: List<CacheEntry>) {
        val valid = entries.filter { it.platformCode.isNotBlank() && it.songId.isNotBlank() }
        if (valid.isEmpty()) return
        val deviceId = com.aesthetic.music.data.sync.SyncStateStore.deviceId()
        com.aesthetic.music.data.sync.SyncStateStore.recordDeletesForCurrentUser(
            com.aesthetic.music.data.sync.SyncCollections.DOWNLOADS,
            valid.map { com.aesthetic.music.data.sync.SyncKey.download(deviceId, it.platformCode, it.songId) },
        )
    }

    /**
     * 文件大小格式化。
     * 固定 Locale.US：大小是机器可读格式，不应跟随系统语言——
     * 此前隐式使用默认 Locale，在阿拉伯语/印地语等语系下会输出非拉丁数字或
     * 逗号小数点（如 "1,5 MB"），缓存页显示异常。
     */
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
