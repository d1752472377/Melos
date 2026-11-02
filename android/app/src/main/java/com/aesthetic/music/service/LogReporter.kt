package com.aesthetic.music.service

import android.content.Context
import android.os.Build
import com.aesthetic.music.BuildConfig
import com.aesthetic.music.data.api.ApiClient
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

/**
 * 客户端日志上报（自建轻量"错误收集"通道）：
 *
 * ## 分级
 * - [Level.ERROR] / [Level.WARN]：默认上报；
 * - [Level.INFO]：只写本地 logcat 与磁盘环形缓冲，不上报（除非远程开关打开，见下）；
 * - [Level.DEBUG]：只进 logcat（Debug 构建也仅 logcat）。
 *
 * ## 批量与缓冲
 * 先进内存队列，周期性（每 [FLUSH_INTERVAL_MS] 30 秒）或攒够 [FLUSH_BATCH_SIZE] 15 条
 * 合并成一次 `POST /api/logs/batch`；上传成功才删本地记录，失败回磁盘队列下次重试
 * （指数退避）。崩溃场景：CrashHandler 先把崩溃日志**同步落盘**，下次启动补传。
 *
 * ## 防日志风暴（教训来自逐秒心跳）
 * - 去重：相同 (tag + message + stack 指纹) 在 [DEDUP_WINDOW_MS] 5 分钟内只上报 1 条，
 *   重复出现只累加 count（下次一并带上）；
 * - 限流：每分钟最多 [MAX_PER_MINUTE] 20 条入队，超出丢弃并计数。
 *
 * ## 隐私
 * 只上报 App 版本/系统版本/设备型号/网络类型等诊断信息；调用方责任：不要把
 * 密码、token、完整手机号放进 message/extra。
 *
 * ## 远程开关
 * 服务端可在响应里下发 `debugUntil`（epoch ms）：在那之前本设备 INFO/DEBUG 也上报——
 * 用于"只有某台手机出问题"的定向排查。
 */
object LogReporter {

    /** 日志级别（数值越大越严重） */
    object Level {
        const val DEBUG = "DEBUG"
        const val INFO = "INFO"
        const val WARN = "WARN"
        const val ERROR = "ERROR"
    }

    private const val TAG = "LogReporter"

    // ===== 缓冲与批量参数 =====
    private const val FLUSH_INTERVAL_MS = 30_000L
    private const val FLUSH_BATCH_SIZE = 15
    private const val MAX_QUEUE = 200              // 内存队列上限（磁盘队列另有上限）
    private const val DISK_QUEUE_MAX_FILES = 30    // 磁盘待发文件上限
    private const val RETRY_BACKOFF_MS = 60_000L   // 失败后退避基数

    // ===== 防风暴 =====
    private const val DEDUP_WINDOW_MS = 5 * 60_000L
    private const val MAX_PER_MINUTE = 20

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ArrayDeque<LogBatchItem>()
    private val queueLock = Any()

    /** 去重指纹 -> (首次时间, 次数, 条目) */
    private val dedup = HashMap<String, Pair<Long, LogBatchItem>>()

    /** 每分钟入队计数 */
    private val minuteBucket = AtomicInteger(0)
    private val minuteStart = AtomicLong(0)
    private var droppedThisMinute = AtomicInteger(0)

    /** 上传失败退避（指数） */
    private var backoffMs = 0L

    /** 会话 ID：进程存活期间不变，便于把一批日志串起来 */
    private val sessionId: String = UUID.randomUUID().toString().take(8)

    /** 远程下发的 DEBUG 采集截止时间（0 = 未开启） */
    @Volatile
    private var debugUntil = 0L

    @Volatile
    private var appContext: Context? = null

    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        // 启动即补传上次残留的磁盘队列（崩溃日志等）
        scope.launch { drainDiskQueue() }
        scope.launch { flushLoop() }
    }

    // ==================== 对外 API ====================

    /** ERROR：总是上报（含堆栈可选） */
    fun e(tag: String, message: String, error: Throwable? = null, extra: Map<String, String>? = null) {
        enqueue(Level.ERROR, tag, message, error, extra)
    }

    /** WARN：默认上报 */
    fun w(tag: String, message: String, error: Throwable? = null, extra: Map<String, String>? = null) {
        enqueue(Level.WARN, tag, message, error, extra)
    }

    /** INFO：只写本地；debugUntil 生效期间才上报 */
    fun i(tag: String, message: String, extra: Map<String, String>? = null) {
        android.util.Log.i(tag, message)
        if (System.currentTimeMillis() < debugUntil) {
            enqueue(Level.INFO, tag, message, null, extra)
        }
    }

    /** DEBUG：只进 logcat（Debug 构建有价值，Release 静默） */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) android.util.Log.d(tag, message)
    }

    // ==================== 入队（去重 + 限流） ====================

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

        // 限流：每分钟 MAX_PER_MINUTE 条，超出丢弃（只计数，附在下一批里）
        val now = System.currentTimeMillis()
        if (now - minuteStart.get() >= 60_000) {
            minuteStart.set(now)
            minuteBucket.set(0)
            val dropped = droppedThisMinute.getAndSet(0)
            if (dropped > 0) {
                // 把"刚才丢了多少条"作为一条 WARN 补进来（不走限流，避免自我吞没）
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

        // 去重：同指纹 5 分钟内只保留一条，次数累加
        val fp = "$tag|$message|${stack.hashCode()}"
        val item = LogBatchItem(
            level = level, tag = tag, message = message.take(2000), stack = stack.take(8000),
            extra = extra, timeMs = now, sessionId = sessionId,
        )
        synchronized(queueLock) {
            val prev = dedup[fp]
            if (prev != null && now - prev.first < DEDUP_WINDOW_MS) {
                val old = prev.second
                dedup[fp] = prev.first to old.copy(count = old.count + 1)
                return
            }
            dedup[fp] = now to item
            // dedup 表清理：防无限增长
            if (dedup.size > 300) {
                dedup.entries.removeIf { now - it.value.first > DEDUP_WINDOW_MS }
            }
            if (queue.size >= MAX_QUEUE) queue.removeFirst() // 挤掉最旧的
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

    // ==================== 上传 ====================

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
            // 失败：落盘，下次启动/周期补传
            backoffMs = (if (backoffMs == 0L) RETRY_BACKOFF_MS else (backoffMs * 2).coerceAtMost(30 * 60_000L))
            spillToDisk(items)
        }
    }

    private suspend fun upload(items: List<LogBatchItem>): Boolean {
        val ctx = appContext ?: return false
        if (!SyncServerStore.isConfigured()) return false
        return try {
            val payload = LogBatchPayload(
                logs = items,
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                os = "Android ${Build.VERSION.RELEASE}",
                device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                appUid = UserStore.user()?.id?.takeIf { it > 0 },
            )
            val resp = ApiClient.api.logsBatch(payload)
            if (resp.code == 200 && resp.data != null) {
                // 服务端可下发 debugUntil：临时打开本设备的 INFO/DEBUG 采集
                resp.data.debugUntil?.takeIf { it > System.currentTimeMillis() }?.let {
                    debugUntil = it
                    android.util.Log.i(TAG, "远程排障开启：INFO/DEBUG 上报至 $it")
                }
                true
            } else {
                // 4xx 之类的确定性失败不该无限重试：直接丢弃，避免死循环
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
        val dir = diskDir() ?: return
        try {
            val json = com.aesthetic.music.data.AppJson.gson.toJson(items)
            File(dir, "batch_${System.currentTimeMillis()}.json").writeText(json)
            // 超上限删最旧
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
                    break // 上传不通：留在磁盘，下次启动再试
                }
            } catch (_: Exception) {
                f.delete() // 坏文件：丢弃，不阻塞队列
            }
        }
    }

    /** 崩溃时同步调用：把当前内存队列 + 崩溃本身落盘（不碰网络，进程随时会死） */
    fun crashFlush(crashLogPath: String?) {
        try {
            val ctx = appContext ?: return
            val items = synchronized(queueLock) {
                val list = queue.toList()
                queue.clear()
                list
            } + LogBatchItem(
                level = Level.ERROR, tag = "CrashHandler",
                message = "进程崩溃，崩溃日志: ${crashLogPath ?: "(写入失败)"}",
                timeMs = System.currentTimeMillis(), sessionId = sessionId,
            )
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
