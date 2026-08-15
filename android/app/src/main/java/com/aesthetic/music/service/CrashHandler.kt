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

    fun init(context: Context) {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = File(context.getExternalFilesDir(null), "crash").apply { mkdirs() }
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
}
