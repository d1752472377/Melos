package com.aesthetic.music.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.aesthetic.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** 更新信息（对应服务器 update.json 字段，参考 App 的 UpdateInfo） */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val apkSize: Long,
    val md5: String,
    val forceUpdate: Boolean,
    val updateLog: String,
    val publishTime: String,
)

/**
 * 在线更新服务（移植自参考 App 的 UpdateService）：
 * 拉取 update.json → 比对 versionCode → 下载 APK → FileProvider 调系统安装。
 */
object UpdateService {

    /** 服务器更新描述文件地址（部署后端时同目录放 update.json） */
    const val UPDATE_URL = "http://43.248.3.147:18787/update.json"

    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val resp = client.newCall(Request.Builder().url(UPDATE_URL).build()).execute()
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val info = UpdateInfo(
                versionName = json.optString("versionName"),
                versionCode = json.optInt("versionCode"),
                apkUrl = json.optString("apkUrl"),
                apkSize = json.optLong("apkSize"),
                md5 = json.optString("md5"),
                forceUpdate = json.optBoolean("forceUpdate"),
                updateLog = json.optString("updateLog"),
                publishTime = json.optString("publishTime"),
            )
            // 比本地 versionCode 高才算有新版本
            if (info.versionCode > BuildConfig.VERSION_CODE) info else null
        } catch (_: Exception) {
            null // 网络异常静默失败，不影响主流程
        }
    }

    /** 下载 APK 到外部存储 updates 目录，返回本地路径 */
    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val file = File(dir, "aesthetic_music_${info.versionName}.apk")
        if (file.exists()) file.delete()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        val resp = client.newCall(Request.Builder().url(info.apkUrl).build()).execute()
        if (!resp.isSuccessful) throw Exception("下载失败: HTTP ${resp.code}")
        val body = resp.body ?: throw Exception("下载失败: 空响应")
        val total = body.contentLength()
        var received = 0L
        body.byteStream().use { input ->
            file.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    received += n
                    if (total > 0) onProgress(received, total)
                }
            }
        }

        // 大小 + MD5 双重校验，避免坏包/半包进入系统安装器（会表现为“解析软件包时出现问题”）
        if (info.apkSize > 0 && received != info.apkSize) {
            file.delete()
            throw Exception("安装包大小校验失败（$received / ${info.apkSize}），请重新检查更新")
        }
        if (info.md5.isNotBlank()) {
            val digest = java.security.MessageDigest.getInstance("MD5").digest(file.readBytes())
            val actual = digest.joinToString("") { "%02x".format(it) }
            if (!actual.equals(info.md5, ignoreCase = true)) {
                file.delete()
                throw Exception("安装包校验失败，请重新检查更新")
            }
        }
        file.absolutePath
    }

    /** 调系统安装器安装 APK（FileProvider + ACTION_VIEW） */
    fun installApk(context: Context, filePath: String) {
        val file = File(filePath)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
