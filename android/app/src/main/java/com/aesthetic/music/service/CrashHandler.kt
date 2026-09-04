package com.aesthetic.music.service

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃日志：未捕获异常写入文件，便于定位闪退原因。
 * 日志位置：Android/data/com.aesthetic.music/files/crash/crash_*.log
 */
object CrashHandler {

    /** 崩溃日志最多保留份数，超出删除最旧的（此前只写不清、无限增长） */
    private const val MAX_LOG_FILES = 10

    fun init(context: Context) {
        pruneOldLogs(context)
        val appContext = context.applicationContext
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 外部存储挂载不可用时（部分 ROM 存储紧张/权限异常）退回内部存储，
                // 此前 getExternalFilesDir 返回 null 直接 NPE 被吞，崩溃日志静默丢失
                val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "crash").apply { mkdirs() }
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val file = File(dir, "crash_${System.currentTimeMillis()}.log")
                file.writeText(
                    buildString {
                        appendLine("time=$time")
                        appendLine("thread=${thread.name}")
                        appendLine("throwable=${throwable::class.java.name}: ${throwable.message}")
                        appendLine(sw.toString())
                    }
                )
                android.util.Log.e("CrashHandler", "崩溃日志已写入: ${file.absolutePath}", throwable)
            } catch (_: Exception) {
            }
            default?.uncaughtException(thread, throwable)
        }
    }

    private fun pruneOldLogs(context: Context) {
        try {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "crash")
            val logs = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            logs.drop(MAX_LOG_FILES).forEach { it.delete() }
        } catch (_: Exception) {
        }
    }
}
