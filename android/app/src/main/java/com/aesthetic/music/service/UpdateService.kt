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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /**
     * 兜底下载地址（后端下发，通常是本机相对路径）。
     *
     * 主地址被指到大带宽镜像后，这里留一条「后端本机直发」的退路：
     * 镜像挂了/被墙了也能下，只是慢一点。为空表示没有兜底。
     */
    val fallbackApkUrl: String = "",
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

    private const val TAG = "UpdateService"

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
                            fallbackApkUrl = data.apkUrlFallback,
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

    private val downloadMutex = Mutex()

    /**
     * 获取下载目标文件对象。
     */
    fun getApkFile(context: Context, info: UpdateInfo): File {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val safeVersion = info.versionName.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { "latest" }
        return File(dir, "aesthetic_music_${safeVersion}.apk")
    }

    /**
     * 校验本地是否已存在完整合法的安装包。
     * 若已就绪，可直接调系统安装程序，无需重复发起网络下载。
     */
    fun isApkReady(context: Context, info: UpdateInfo): Boolean {
        val file = getApkFile(context, info)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return false

        // 大小校验
        if (info.apkSize > 0L && file.length() != info.apkSize) return false

        // 摘要校验
        selectDigestSpec(sha256 = info.sha256, md5 = info.md5)?.let { spec ->
            val actual = computeFileDigest(file, spec.algorithm)
            if (!actual.equals(spec.expected, ignoreCase = true)) return false
        }

        // 签名一致性校验
        if (!verifyApkSignature(context, file.absolutePath)) return false

        return true
    }

    /** 下载 APK 到外部存储 updates 目录，返回本地路径。若本地已有完整合法安装包则直接秒开，绝不重复下载。 */
    /** 单个源最多尝试几次（每次都会从已下载的字节处续传） */
    private const val DOWNLOAD_MAX_ATTEMPTS = 8

    /** 单次连接里连续多少轮「一个字节都没收到」就放弃这个源 */
    private const val DOWNLOAD_MAX_STALLS = 2

    /** 解析 apkUrl：相对路径补全为「当前同步服务器 + 路径」 */
    private fun resolveDownloadUrl(apkUrl: String): String =
        if (apkUrl.startsWith("http://") || apkUrl.startsWith("https://")) {
            apkUrl
        } else {
            val serverBase = SyncServerStore.getServerUrl().trimEnd('/')
            "$serverBase/${apkUrl.trimStart('/')}"
        }

    /**
     * 下载 APK 到外部存储 updates 目录，返回本地路径。
     *
     * ## 为什么必须支持断点续传
     * 自建服务器是 3M 小带宽（实测满速约 430 KB/s，8.5MB 的包要 20 秒以上），
     * 而手机端的网络在 20 秒里完全可能抖一下、切个基站、或者用户锁屏。
     * 旧实现是「一条连接从头下到尾，中途断了就整包重来」——在 3M 带宽上
     * 只要断一次，用户看到的就是「根本下载不下来」。
     *
     * 现在：**每次中断都保留已下载的 .tmp，下一次带 `Range` 从断点续传**，
     * 单个源最多重试 [DOWNLOAD_MAX_ATTEMPTS] 次；只有连续两次「一个字节都没收到」
     * 才判定这个源彻底不通。这样 8.5MB 即使在 100 KB/s 的弱网下也能分几次下完。
     *
     * 下载完成后仍然做**大小 + 摘要 + 签名**三重校验，坏包/半包不会进系统安装器。
     */
    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            val file = getApkFile(context, info)
            val dir = file.parentFile ?: File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }

            // 1. 本地已有完整合法的安装包 → 直接复用，绝不重复下载
            if (isApkReady(context, info)) {
                if (info.apkSize > 0) onProgress(info.apkSize, info.apkSize)
                return@withLock file.absolutePath
            }

            val tmpFile = File(dir, "${file.name}.tmp")

            // 2. 清理历史遗留的旧版本 APK（保留目标文件与**正在续传的 .tmp**）
            dir.listFiles()?.forEach {
                if (it.isFile && it != file && it != tmpFile &&
                    it.name.startsWith("aesthetic_music_") && !it.name.endsWith(".tmp")
                ) {
                    it.delete()
                }
            }
            if (file.exists()) file.delete()

            // 3. 断点续传下载（主地址=大带宽镜像，失败再退回后端本机直发）
            val downloadUrls = buildList {
                add(resolveDownloadUrl(info.apkUrl))
                val fb = resolveDownloadUrl(info.fallbackApkUrl)
                if (info.fallbackApkUrl.isNotBlank() && fb !in this) add(fb)
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                // 单次读超时放宽到 60s：3M 带宽 + 弱网时，一次读卡住几秒很常见，
                // 但真的 60s 没数据就当这次连接废了，交给外层续传
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
                    // 换地址前把半包清掉：不同源的字节不一定连续（虽然校验会拦住，但省一次白下）
                    if (tmpFile.exists()) tmpFile.delete()
                }
            }
            if (lastError != null) throw lastError

            // 4. 大小 + 摘要 + 签名三重校验，避免坏包/半包进入系统安装器
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
                tmpFile.delete()
                throw Exception("安装包签名与当前应用不一致，已拦截，请通过官方渠道更新")
            }

            // 5. 校验通过，原子重命名为正式 apk 文件
            if (tmpFile.renameTo(file)) {
                file.absolutePath
            } else {
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
                file.absolutePath
            }
        }
    }

    /**
     * 带续传的下载循环：断一次就带 `Range: bytes=<已下载>-` 接着下。
     *
     * 三种响应都要处理：
     *  - `206`：服务端支持续传，追加写入；
     *  - `200`（我们发了 Range 却拿到整包）：服务端不支持 Range（或代理把 Range 吃了），
     *    删掉旧的分片从头写，不能追加（否则文件前段会重复）；
     *  - 其它：抛错，交给外层重试。
     */
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
                    if (!resuming) start = 0L // 200：服务端忽略了 Range，必须从头写
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
                // 连接正常结束但字节不够（服务端提前关连接）：继续续传
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
            // 退避 1s, 2s, 3s…（最多 5s），给网络一点恢复时间
            delay(minOf(attempt, 5) * 1_000L)
        }
        throw lastError ?: Exception("下载失败，请检查网络后重试")
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
