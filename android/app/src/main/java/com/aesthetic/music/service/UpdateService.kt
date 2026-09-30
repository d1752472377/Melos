package com.aesthetic.music.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.aesthetic.music.BuildConfig
import com.aesthetic.music.data.api.ApiClient
import com.aesthetic.music.data.settings.SyncServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 更新信息（对应服务器 update.json 字段，参考 App 的 UpdateInfo） */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val apkSize: Long,
    /** 旧字段，保留用于兼容历史 update.json；有 [sha256] 时不再使用 */
    val md5: String,
    /** 首选摘要算法；服务端未下发时为空，此时回退 [md5] */
    val sha256: String,
    val forceUpdate: Boolean,
    val updateLog: String,
    val publishTime: String,
)

/**
 * 在线更新服务：
 * 优先向当前自建/云端后端探测最新版本 → 若无新版本或不可达回退至 GitHub 静态 update.json →
 * 比对 versionCode → 下载 APK（支持相对路径自动补全） → FileProvider 调系统安装。
 */
object UpdateService {

    /** 官方服务器更新描述文件地址（GitHub Release 资产，经 Vercel 转发） */
    const val UPDATE_URL = "https://music.dovislab.com/update.json"

    /** 更新检查结果三态：此前"网络失败"与"确无更新"都返回 null，UI 会把断网误报成已是最新版 */
    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        data class Failed(val reason: String? = null) : CheckResult()
    }

    suspend fun checkUpdate(): CheckResult = withContext(Dispatchers.IO) {
        // 1. 若配置了后端服务器（或默认内置服务器），优先向自建后端查询最新发布的版本
        var serverHasNoNewerVersion = false
        if (SyncServerStore.isConfigured()) {
            try {
                val resp = ApiClient.api.latestVersion()
                val data = resp.data
                if (resp.code == 200 && data != null) {
                    if (data.versionCode > BuildConfig.VERSION_CODE) {
                        val serverBase = SyncServerStore.getServerUrl().trimEnd('/')
                        val fullApkUrl = if (data.apkUrl.startsWith("http://") || data.apkUrl.startsWith("https://")) {
                            data.apkUrl
                        } else {
                            "$serverBase/${data.apkUrl.trimStart('/')}"
                        }
                        val pubTime = if (data.createdAt > 0) {
                            val ms = if (data.createdAt < 10000000000L) data.createdAt * 1000L else data.createdAt
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
                        } else ""
                        val info = UpdateInfo(
                            versionName = data.versionName,
                            versionCode = data.versionCode.toInt(),
                            apkUrl = fullApkUrl,
                            apkSize = data.fileSize,
                            md5 = "",
                            sha256 = data.hashSha256,
                            forceUpdate = data.forceUpdate,
                            updateLog = data.changelog,
                            publishTime = pubTime,
                        )
                        return@withContext CheckResult.Available(info)
                    } else {
                        // 后端有版本记录，但版本号不高于当前客户端
                        serverHasNoNewerVersion = true
                    }
                }
            } catch (_: Exception) {
                // 后端查询失败（如离线、404、反代异常等），继续向下回退到官方 UPDATE_URL
            }
        }

        // 2. 回退机制：若自建后端无更高版本或请求异常，回退检查官方静态 update.json
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(UPDATE_URL).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext if (serverHasNoNewerVersion) CheckResult.UpToDate else CheckResult.Failed("HTTP ${resp.code}")
                }
                val body = resp.body?.string() ?: return@withContext if (serverHasNoNewerVersion) CheckResult.UpToDate else CheckResult.Failed("空响应")
                val json = JSONObject(body)
                val info = UpdateInfo(
                    versionName = json.optString("versionName"),
                    versionCode = json.optInt("versionCode"),
                    apkUrl = json.optString("apkUrl"),
                    apkSize = json.optLong("apkSize"),
                    md5 = json.optString("md5"),
                    sha256 = json.optString("sha256"),
                    forceUpdate = json.optBoolean("forceUpdate"),
                    updateLog = json.optString("updateLog"),
                    publishTime = json.optString("publishTime"),
                )
                // 比本地 versionCode 高才算有新版本
                if (info.versionCode > BuildConfig.VERSION_CODE) CheckResult.Available(info) else CheckResult.UpToDate
            }
        } catch (e: Exception) {
            // 网络异常必须显式区分，不能静默当成"无更新"
            if (serverHasNoNewerVersion) CheckResult.UpToDate else CheckResult.Failed(e.message)
        }
    }

    /** 下载 APK 到外部存储 updates 目录，返回本地路径 */
    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val safeVersion = info.versionName.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { "latest" }
        val file = File(dir, "aesthetic_music_${safeVersion}.apk")
        if (file.exists()) file.delete()
        // 清理历史版本 APK：此前只删同名版本文件，旧版本永久占用 updates/ 且不计入缓存统计
        dir.listFiles()?.forEach { it.isFile && it != file && it.name.startsWith("aesthetic_music_") && it.delete() }

        val downloadUrl = if (info.apkUrl.startsWith("http://") || info.apkUrl.startsWith("https://")) {
            info.apkUrl
        } else {
            val serverBase = SyncServerStore.getServerUrl().trimEnd('/')
            "$serverBase/${info.apkUrl.trimStart('/')}"
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        // resp.use 保证失败/异常分支也释放连接（此前 !isSuccessful 直接 throw，连接泄漏）
        client.newCall(Request.Builder().url(downloadUrl).build()).execute().use { resp ->
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

            // 大小 + 摘要双重校验，避免坏包/半包进入系统安装器（会表现为“解析软件包时出现问题”）
            if (info.apkSize > 0 && received != info.apkSize) {
                file.delete()
                throw Exception("安装包大小校验失败（$received / ${info.apkSize}），请重新检查更新")
            }
            // 优先 SHA-256；服务端未下发时回退 MD5（兼容历史 update.json）
            selectDigestSpec(sha256 = info.sha256, md5 = info.md5)?.let { spec ->
                val actual = computeFileDigest(file, spec.algorithm)
                if (!actual.equals(spec.expected, ignoreCase = true)) {
                    file.delete()
                    throw Exception("安装包校验失败（${spec.algorithm}），请重新检查更新")
                }
            }
            // 签名一致性校验：防服务端/更新通道被攻陷后分发伪造 APK
            // （md5/url 都出自同一 TLS 通道，属于"自证"，签名校验才是独立信任锚点）
            if (!verifyApkSignature(context, file.absolutePath)) {
                file.delete()
                throw Exception("安装包签名与当前应用不一致，已拦截，请通过官方渠道更新")
            }
            file.absolutePath
        }
    }

    /**
     * 校验下载 APK 的签名证书与当前已安装应用一致。
     * 注意：debug 签名包与 release 签名包互不匹配，用 debug 包测正式更新会在此拦截（属预期行为）。
     */
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
                // 若 signingInfo 为空（部分 Android 9+ 定制 ROM 或特定打包方式），回退旧版签名方式
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

    /** 引导用户去系统设置开启"允许安装未知应用" */
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

    /** 调系统安装器安装 APK（FileProvider + ACTION_VIEW）。
     *  返回 false 表示因缺少"允许安装未知应用"权限被拦截（调用方应据此引导去设置） */
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
