package com.aesthetic.music.service

import android.content.Context
import android.os.Build
import com.aesthetic.music.BuildConfig
import com.aesthetic.music.data.api.ApiClient
import com.aesthetic.music.data.api.BreadcrumbItem
import com.aesthetic.music.data.api.LogBatchItem
import com.aesthetic.music.data.api.LogBatchPayload
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.data.settings.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object LogReporter {

    object Level {
        const val DEBUG = "DEBUG"
        const val INFO = "INFO"
        const val WARN = "WARN"
        const val ERROR = "ERROR"
    }

    private const val TAG = "LogReporter"

    private const val FLUSH_INTERVAL_MS = 30_000L
    private const val FLUSH_BATCH_SIZE = 15
    private const val MAX_QUEUE = 200
    private const val DISK_QUEUE_MAX_FILES = 10
    private const val RETRY_BACKOFF_MS = 60_000L

    private const val DEDUP_WINDOW_MS = 5 * 60_000L
    private const val MAX_PER_MINUTE = 20

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ArrayDeque<LogBatchItem>()
    private val queueLock = Any()

    private val dedup = HashMap<String, Pair<Long, LogBatchItem>>()

    private val minuteBucket = AtomicInteger(0)
    private val minuteStart = AtomicLong(0)
    private var droppedThisMinute = AtomicInteger(0)

    private var backoffMs = 0L

    private val sessionId: String = UUID.randomUUID().toString().take(8)

    private data class Crumb(val timeMs: Long, val level: String, val tag: String, val message: String)

    private val breadcrumbs = ArrayDeque<Crumb>()
    private const val MAX_BREADCRUMBS = 40

    private fun addBreadcrumb(level: String, tag: String, message: String, timeMs: Long) {
        synchronized(breadcrumbs) {
            breadcrumbs.addLast(Crumb(timeMs, level, tag, message.take(200)))
            while (breadcrumbs.size > MAX_BREADCRUMBS) breadcrumbs.removeFirst()
        }
    }

    fun breadcrumb(tag: String, message: String) {
        addBreadcrumb("BREADCRUMB", tag, message, System.currentTimeMillis())
    }

    private fun breadcrumbsForUpload(): List<BreadcrumbItem>? {
        val snapshot = synchronized(breadcrumbs) { breadcrumbs.toList() }
        if (snapshot.isEmpty()) return null
        return snapshot.map {
            BreadcrumbItem(timeMs = it.timeMs, level = it.level, tag = it.tag, message = it.message)
        }
    }

    @Volatile
    private var debugUntil = 0L

    @Volatile
    private var appContext: Context? = null

    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        LogContext.init(context)
        // 启动即补传上次残留的磁盘队列（崩溃日志等）
        scope.launch { drainDiskQueue() }
        scope.launch { flushLoop() }
    }

    fun e(tag: String, message: String, error: Throwable? = null, extra: Map<String, String>? = null) {
        enqueue(Level.ERROR, tag, message, error, extra)
    }

    fun w(tag: String, message: String, error: Throwable? = null, extra: Map<String, String>? = null) {
        enqueue(Level.WARN, tag, message, error, extra)
    }

    fun i(tag: String, message: String, extra: Map<String, String>? = null) {
        android.util.Log.i(tag, message)
        if (System.currentTimeMillis() < debugUntil) {
            enqueue(Level.INFO, tag, message, null, extra)
        }
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) android.util.Log.d(tag, message)
    }

    private fun enqueue(level: String, tag: String, message: String, error: Throwable?, extra: Map<String, String>?) {
        val stack = error?.let { stackOf(it) }.orEmpty()
        android.util.Log.println(
            when (level) {
                Level.ERROR -> android.util.Log.ERROR
                Level.WARN -> android.util.Log.WARN
                Level.INFO -> android.util.Log.INFO
                else -> android.util.Log.DEBUG
            }, tag, message + if (stack.isNotEmpty()) "\n$stack" else ""
        )

        val now = System.currentTimeMillis()
        if (now - minuteStart.get() >= 60_000) {
            minuteStart.set(now)
            minuteBucket.set(0)
            val dropped = droppedThisMinute.getAndSet(0)
            if (dropped > 0) {
                forceEnqueue(LogBatchItem(
                    level = Level.WARN, tag = TAG, message = "日志限流丢弃 $dropped 条/分钟",
                    timeMs = now, sessionId = sessionId,
                ))
            }
        }
        if (minuteBucket.incrementAndGet() > MAX_PER_MINUTE) {
            droppedThisMinute.incrementAndGet()
            return
        }

        val fp = "$tag|$message|${stack.hashCode()}"
        val item = LogBatchItem(
            level = level, tag = tag, message = message.take(2000), stack = stack.take(8000),
            extra = extra, timeMs = now, sessionId = sessionId,
            thread = Thread.currentThread().name,
        )
        addBreadcrumb(level, tag, message, now)
        synchronized(queueLock) {
            val prev = dedup[fp]
            if (prev != null && now - prev.first < DEDUP_WINDOW_MS) {
                val old = prev.second
                dedup[fp] = prev.first to old.copy(count = old.count + 1)
                return
            }
            dedup[fp] = now to item
            if (dedup.size > 300) {
                dedup.entries.removeIf { now - it.value.first > DEDUP_WINDOW_MS }
            }
            if (queue.size >= MAX_QUEUE) queue.removeFirst()
            queue.addLast(item)
        }
        if (queue.size >= FLUSH_BATCH_SIZE) {
            scope.launch { flush(reason = "batch-full") }
        }
    }

    /** 不限流不去重的强制入队（内部使用：限流提示、崩溃补传） */
    private fun forceEnqueue(item: LogBatchItem) {
        synchronized(queueLock) {
            if (queue.size >= MAX_QUEUE) queue.removeFirst()
            queue.addLast(item)
        }
    }

    private suspend fun flushLoop() {
        while (true) {
            delay(if (backoffMs > 0) backoffMs else FLUSH_INTERVAL_MS)
            flush(reason = "periodic")
        }
    }

    private suspend fun flush(reason: String) {
        val items = synchronized(queueLock) {
            if (queue.isEmpty()) return
            val take = queue.toList()
            queue.clear()
            take
        }
        val ok = upload(items)
        if (ok) {
            backoffMs = 0
        } else {
            backoffMs = (if (backoffMs == 0L) RETRY_BACKOFF_MS else (backoffMs * 2).coerceAtMost(30 * 60_000L))
            spillToDisk(items)
        }
    }

    private suspend fun upload(items: List<LogBatchItem>): Boolean {
        val ctx = appContext ?: return false
        if (!SyncServerStore.isConfigured()) return false
        return try {
            val contextMap = LogContext.snapshotOrEmpty()
            val crumbs = if (items.any { it.level == Level.ERROR }) breadcrumbsForUpload() else null
            val enriched = if (crumbs == null) items else items.map { it.copy(breadcrumbs = crumbs) }
            val payload = LogBatchPayload(
                logs = enriched,
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                os = "Android ${Build.VERSION.RELEASE}",
                device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                appUid = UserStore.user()?.id?.takeIf { it > 0 },
                context = contextMap,
            )
            val resp = ApiClient.api.logsBatch(payload)
            if (resp.code == 200 && resp.data != null) {
                resp.data.debugUntil?.takeIf { it > System.currentTimeMillis() }?.let {
                    debugUntil = it
                    android.util.Log.i(TAG, "远程排障开启：INFO/DEBUG 上报至 $it")
                }
                true
            } else {
                android.util.Log.w(TAG, "日志上报被拒 code=${resp.code} msg=${resp.msg}")
                true
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "日志上报失败（${items.size} 条将落盘重试）: ${e.message}")
            false
        }
    }

    // ==================== 磁盘队列（崩溃/断网兜底） ====================

    private fun diskDir(): File? = appContext?.let {
        (it.getExternalFilesDir(null) ?: it.filesDir)?.let { base -> File(base, "logqueue").apply { mkdirs() } }
    }

    private fun spillToDisk(items: List<LogBatchItem>) {
        val keep = items.filter { it.level == Level.WARN || it.level == Level.ERROR }
        if (keep.isEmpty()) return
        val dir = diskDir() ?: return
        try {
            val json = com.aesthetic.music.data.AppJson.gson.toJson(keep)
            File(dir, "batch_${System.currentTimeMillis()}.json").writeText(json)
            dir.listFiles()?.sortedByDescending { it.lastModified() }
                ?.drop(DISK_QUEUE_MAX_FILES)?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    private suspend fun drainDiskQueue() {
        val dir = diskDir() ?: return
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        for (f in files) {
            try {
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    java.util.List::class.java, LogBatchItem::class.java).type
                val items: List<LogBatchItem> = com.aesthetic.music.data.AppJson.gson.fromJson(f.readText(), type)
                if (items.isNotEmpty() && upload(items)) {
                    f.delete()
                } else {
                    break
                }
            } catch (_: Exception) {
                f.delete()
            }
        }
    }

    fun crashFlush(crashLogPath: String?, error: Throwable? = null) {
        try {
            val ctx = appContext ?: return
            val frozenCrumbs = breadcrumbsForUpload()
            val frozenContext = LogContext.snapshotOrEmpty()
            val crashItem = LogBatchItem(
                level = Level.ERROR, tag = "CrashHandler",
                message = "进程崩溃，崩溃日志: ${crashLogPath ?: "(写入失败)"}",
                stack = error?.let { stackOf(it) },
                timeMs = System.currentTimeMillis(), sessionId = sessionId,
                thread = Thread.currentThread().name,
                breadcrumbs = frozenCrumbs,
                context = frozenContext,
            )
            val items = synchronized(queueLock) {
                val list = queue.toList()
                queue.clear()
                list
            } + crashItem
            spillToDisk(items)
        } catch (_: Exception) {}
    }

    private fun stackOf(t: Throwable): String = try {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        sw.toString()
    } catch (_: Exception) {
        ""
    }
}
