package com.aesthetic.music.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.aesthetic.music.BuildConfig
import com.aesthetic.music.data.api.ApiClient
import com.aesthetic.music.data.settings.SyncServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import com.aesthetic.music.service.LogReporter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val fallbackApkUrl: String = "",
    val apkSize: Long,
    /** 旧字段，保留用于兼容历史 update.json；有 [sha256] 时不再使用 */
    val md5: String,
    val sha256: String,
    val forceUpdate: Boolean,
    val updateLog: String,
    val publishTime: String,
)

object UpdateService {

    private const val TAG = "UpdateService"

    /** 版本检查只认自建后端；后端返回即结束，不再等待海外 update.json 兜底。 */
    private const val CHECK_TIMEOUT_MS = 8_000L

    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        data class Failed(val reason: String? = null) : CheckResult()
    }

    suspend fun checkUpdate(): CheckResult = withContext(Dispatchers.IO) {
        val resp = try {
            withTimeout(CHECK_TIMEOUT_MS) { ApiClient.api.latestVersion() }
        } catch (e: TimeoutCancellationException) {
            LogReporter.w("Update", "检查更新超时")
            return@withContext CheckResult.Failed("检查更新超时")
        } catch (e: Exception) {
            LogReporter.w("Update", "检查更新失败", e)
            return@withContext CheckResult.Failed(e.message)
        }

        val data = resp.data
        if (resp.code != 200) {
            return@withContext CheckResult.Failed("HTTP ${resp.code}")
        }
        if (data == null) {
            return@withContext CheckResult.UpToDate
        }

        if (data.versionCode <= BuildConfig.VERSION_CODE) {
            return@withContext CheckResult.UpToDate
        }

        val serverBase = SyncServerStore.getServerUrl().trimEnd('/')
        val fullApkUrl = if (data.apkUrl.startsWith("http://") || data.apkUrl.startsWith("https://")) {
            data.apkUrl
        } else {
            "$serverBase/${data.apkUrl.trimStart('/')}"
        }
        val publishTime = data.createdAt
        val info = UpdateInfo(
            versionName = data.versionName,
            versionCode = data.versionCode.toInt(),
            apkUrl = fullApkUrl,
            fallbackApkUrl = data.apkUrlFallback,
            apkSize = data.fileSize,
            md5 = "",
            sha256 = data.hashSha256,
            forceUpdate = data.forceUpdate,
            updateLog = data.changelog,
            publishTime = publishTime,
        )
        CheckResult.Available(info)
    }

    private val downloadMutex = Mutex()

    fun getApkFile(context: Context, info: UpdateInfo): File {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val safeVersion = info.versionName.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { "latest" }
        return File(dir, "aesthetic_music_${safeVersion}.apk")
    }

    fun isApkReady(context: Context, info: UpdateInfo): Boolean {
        val file = getApkFile(context, info)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return false

        if (info.apkSize > 0L && file.length() != info.apkSize) return false

        selectDigestSpec(sha256 = info.sha256, md5 = info.md5)?.let { spec ->
            val actual = computeFileDigest(file, spec.algorithm)
            if (!actual.equals(spec.expected, ignoreCase = true)) return false
        }

        if (!verifyApkSignature(context, file.absolutePath)) {
            LogReporter.w("Update", "安装包签名校验失败 path=${file.name}")
            return false
        }

        return true
    }

    private const val DOWNLOAD_MAX_ATTEMPTS = 8

    private const val DOWNLOAD_MAX_STALLS = 2

    private fun resolveDownloadUrl(apkUrl: String): String =
        if (apkUrl.startsWith("http://") || apkUrl.startsWith("https://")) {
            apkUrl
        } else {
            val serverBase = SyncServerStore.getServerUrl().trimEnd('/')
            "$serverBase/${apkUrl.trimStart('/')}"
        }

    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            val file = getApkFile(context, info)
            val dir = file.parentFile ?: File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }

            if (isApkReady(context, info)) {
                if (info.apkSize > 0) onProgress(info.apkSize, info.apkSize)
                return@withLock file.absolutePath
            }

            val tmpFile = File(dir, "${file.name}.tmp")

            dir.listFiles()?.forEach {
                if (it.isFile && it != file && it != tmpFile &&
                    it.name.startsWith("aesthetic_music_") && !it.name.endsWith(".tmp")
                ) {
                    it.delete()
                }
            }
            if (file.exists()) file.delete()

            val downloadUrls = buildList {
                add(resolveDownloadUrl(info.apkUrl))
                val fb = resolveDownloadUrl(info.fallbackApkUrl)
                if (info.fallbackApkUrl.isNotBlank() && fb !in this) add(fb)
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            var lastError: Exception? = null
            for (url in downloadUrls) {
                try {
                    Log.i(TAG, "开始下载 $url")
                    downloadWithResume(client, url, tmpFile, info.apkSize, onProgress)
                    lastError = null
                    break
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "下载失败（$url）: ${e.message}，尝试下一个地址")
                    if (tmpFile.exists()) tmpFile.delete()
                }
            }
            if (lastError != null) throw lastError

            val received = tmpFile.length()
            if (info.apkSize > 0 && received != info.apkSize) {
                tmpFile.delete()
                throw Exception("安装包大小校验失败（$received / ${info.apkSize}），请重新检查更新")
            }
            selectDigestSpec(sha256 = info.sha256, md5 = info.md5)?.let { spec ->
                val actual = computeFileDigest(tmpFile, spec.algorithm)
                if (!actual.equals(spec.expected, ignoreCase = true)) {
                    tmpFile.delete()
                    throw Exception("安装包校验失败（${spec.algorithm}），请重新检查更新")
                }
            }
            if (!verifyApkSignature(context, tmpFile.absolutePath)) {
                LogReporter.w("Update", "新下载安装包签名不一致，已拦截")
                tmpFile.delete()
                throw Exception("安装包签名与当前应用不一致，已拦截，请通过官方渠道更新")
            }

            if (tmpFile.renameTo(file)) {
                file.absolutePath
            } else {
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
                file.absolutePath
            }
        }
    }

    private suspend fun downloadWithResume(
        client: OkHttpClient,
        url: String,
        tmpFile: File,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        var stalls = 0
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < DOWNLOAD_MAX_ATTEMPTS) {
            attempt++
            var start = if (tmpFile.exists()) tmpFile.length() else 0L
            if (expectedSize > 0 && start == expectedSize) return
            if (expectedSize > 0 && start > expectedSize) {
                tmpFile.delete()
                start = 0L
            }

            var receivedThisAttempt = 0L
            try {
                val request = Request.Builder()
                    .url(url)
                    .apply { if (start > 0) header("Range", "bytes=$start-") }
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("下载失败: HTTP ${resp.code}")
                    val body = resp.body ?: throw Exception("下载失败: 空响应")
                    val resuming = resp.code == 206 && start > 0
                    if (!resuming) start = 0L
                    val total = when {
                        expectedSize > 0 -> expectedSize
                        else -> start + body.contentLength()
                    }
                    java.io.FileOutputStream(tmpFile, resuming).use { output ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                receivedThisAttempt += n
                                if (total > 0) onProgress(start + receivedThisAttempt, total)
                            }
                        }
                    }
                }
                val now = tmpFile.length()
                if (expectedSize <= 0 || now == expectedSize) return
                lastError = Exception("下载不完整（$now / $expectedSize）")
                if (receivedThisAttempt < 1024) stalls++ else stalls = 0
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (receivedThisAttempt < 1024) stalls++ else stalls = 0
                Log.w(TAG, "下载中断（第 $attempt 次，已收到 $receivedThisAttempt 字节）: ${e.message}")
            }

            if (stalls >= DOWNLOAD_MAX_STALLS) break
            delay(minOf(attempt, 5) * 1_000L)
        }
        throw lastError ?: Exception("下载失败，请检查网络后重试")
    }
    private fun verifyApkSignature(context: Context, filePath: String): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val archive = pm.getPackageArchiveInfo(filePath, PackageManager.GET_SIGNING_CERTIFICATES) ?: return false
                val current = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES) ?: return false
                val a = archive.signingInfo?.apkContentsSigners?.firstOrNull()
                val c = current.signingInfo?.apkContentsSigners?.firstOrNull()
                if (a != null && c != null) {
                    return a.toByteArray().contentEquals(c.toByteArray())
                }
                @Suppress("DEPRECATION")
                val archiveLegacy = pm.getPackageArchiveInfo(filePath, PackageManager.GET_SIGNATURES) ?: return false
                @Suppress("DEPRECATION")
                val currentLegacy = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES) ?: return false
                val aLegacy = archiveLegacy.signatures?.firstOrNull() ?: return false
                val cLegacy = currentLegacy.signatures?.firstOrNull() ?: return false
                aLegacy.toByteArray().contentEquals(cLegacy.toByteArray())
            } else {
                @Suppress("DEPRECATION")
                val archive = pm.getPackageArchiveInfo(filePath, PackageManager.GET_SIGNATURES) ?: return false
                @Suppress("DEPRECATION")
                val current = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES) ?: return false
                val a = archive.signatures?.firstOrNull() ?: return false
                val c = current.signatures?.firstOrNull() ?: return false
                a.toByteArray().contentEquals(c.toByteArray())
            }
        } catch (_: Exception) {
            false
        }
    }

    /** 是否有"允许安装未知应用"权限（Android 8+）。
     *  此前未检查：无权限时跳系统安装页会直接失败，而 UI 永远停在"正在打开安装程序…"无引导 */
    fun canInstallPackage(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(context: Context) {
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun installApk(context: Context, filePath: String): Boolean {
        if (!canInstallPackage(context)) {
            openInstallPermissionSettings(context)
            return false
        }
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
        context.startActivity(intent)
        return true
    }
}
