package com.aesthetic.music.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    /** 服务器更新描述文件地址（GitHub Release 资产，经 Vercel 转发） */
    const val UPDATE_URL = "https://music.dovislab.com/update.json"

    /** 更新检查结果三态：此前"网络失败"与"确无更新"都返回 null，UI 会把断网误报成已是最新版 */
    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        data class Failed(val reason: String? = null) : CheckResult()
    }

    suspend fun checkUpdate(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            // resp.use 保证无论成功/失败/异常都释放连接（此前非 2xx 与空响应分支直接 return，
            // Response 未 close，OkHttp 连接池里的连接泄漏）
            client.newCall(Request.Builder().url(UPDATE_URL).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext CheckResult.Failed("HTTP ${resp.code}")
                val body = resp.body?.string() ?: return@withContext CheckResult.Failed("空响应")
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
                if (info.versionCode > BuildConfig.VERSION_CODE) CheckResult.Available(info) else CheckResult.UpToDate
            }
        } catch (e: Exception) {
            // 网络异常必须显式区分，不能静默当成"无更新"
            CheckResult.Failed(e.message)
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
        // 清理历史版本 APK：此前只删同名版本文件，旧版本永久占用 updates/ 且不计入缓存统计
        dir.listFiles()?.forEach { it.isFile && it != file && it.name.startsWith("aesthetic_music_") && it.delete() }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        // resp.use 保证失败/异常分支也释放连接（此前 !isSuccessful 直接 throw，连接泄漏）
        client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { resp ->
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
                // 流式计算摘要：整包 readBytes 会把几十上百 MB APK 读进内存，低内存设备 OOM
                val digest = java.security.MessageDigest.getInstance("MD5")
                file.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(info.md5, ignoreCase = true)) {
                    file.delete()
                    throw Exception("安装包校验失败，请重新检查更新")
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
                val a = archive.signingInfo?.apkContentsSigners?.firstOrNull() ?: return false
                val c = current.signingInfo?.apkContentsSigners?.firstOrNull() ?: return false
                a.toByteArray().contentEquals(c.toByteArray())
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        return true
    }
}
