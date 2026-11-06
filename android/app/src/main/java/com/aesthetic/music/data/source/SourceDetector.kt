package com.aesthetic.music.data.source

import android.util.Log
import com.aesthetic.music.data.api.platform.PlatformMusicService
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.userapi.UserApiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SourceDetector(
    private val timeoutMs: Long = 12_000L,
) {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun detectAll(
        plugins: List<SourcePlugin>,
        keyword: String = DEFAULT_KEYWORD,
        onProgress: (SourceProbe) -> Unit,
    ): List<SourceProbe> = coroutineScope {
        val targets = buildTargets(plugins)
        val builtinTargets = targets.filterIsInstance<DetectTarget.Builtin>()
        val pluginTargets = targets.filterIsInstance<DetectTarget.PluginPlatform>()

        val builtinJobs = builtinTargets.map { target ->
            async(Dispatchers.IO) { safeDetect(target, keyword) }
        }

        val pluginProbes = mutableListOf<SourceProbe>()
        pluginTargets.groupBy { it.plugin.id }.values.forEach { group ->
            val plugin = group.first().plugin
            val results = SourceManager.withPlugin(plugin) {
                group.map { safeDetect(it, keyword) }
            } ?: group.map { target ->
                SourceProbe(
                    target = target.id,
                    status = ProbeStatus.FAIL,
                    detail = "脚本未能加载到沙箱（源码缺失或语法错误）",
                    checkedAt = System.currentTimeMillis(),
                )
            }
            results.forEach { probe ->
                withContext(Dispatchers.Main) { onProgress(probe) }
                pluginProbes += probe
            }
        }

        val builtinProbes = builtinJobs.awaitAll().also { probes ->
            probes.forEach { withContext(Dispatchers.Main) { onProgress(it) } }
        }

        builtinProbes + pluginProbes
    }.also {
        SourceManager.restoreActive()
    }

    suspend fun detectOne(target: DetectTarget, keyword: String = DEFAULT_KEYWORD): SourceProbe =
        when (target) {
            is DetectTarget.Builtin -> detectBuiltin(target.platform, keyword)
            is DetectTarget.PluginPlatform -> SourceManager.withPlugin(target.plugin) {
                detectPlugin(target.plugin, target.platform, keyword)
            } ?: SourceProbe(
                target = target.id,
                status = ProbeStatus.FAIL,
                detail = "脚本未能加载到沙箱（源码缺失或语法错误）",
                checkedAt = System.currentTimeMillis(),
            )
        }

    private suspend fun safeDetect(target: DetectTarget, keyword: String): SourceProbe = try {
        when (target) {
            is DetectTarget.Builtin -> detectBuiltin(target.platform, keyword)
            is DetectTarget.PluginPlatform -> detectPlugin(target.plugin, target.platform, keyword)
        }
    } catch (e: Exception) {
        Log.w(TAG, "检测失败[${target.label}]: ${e.message}")
        SourceProbe(
            target = target.id,
            status = ProbeStatus.FAIL,
            detail = e.message ?: "检测异常",
            checkedAt = System.currentTimeMillis(),
        )
    }

    fun buildTargets(plugins: List<SourcePlugin>): List<DetectTarget> {
        val targets = mutableListOf<DetectTarget>()
        SourcePlatform.builtins.forEach { targets += DetectTarget.Builtin(it) }
        plugins.filter { it.enable }.forEach { plugin ->
            val declared = plugin.declaredPlatforms.ifEmpty {
                SourcePlatform.builtins
            }
            declared.forEach { platform ->
                targets += DetectTarget.PluginPlatform(plugin, platform)
            }
        }
        return targets
    }

    private suspend fun detectBuiltin(platform: SourcePlatform, keyword: String): SourceProbe {
        val id = SourceProbe.builtinTarget(platform)
        if (!platform.hasBuiltin) {
            return SourceProbe(
                target = id,
                status = ProbeStatus.UNTESTABLE,
                detail = "${platform.label}没有内置搜索入口，只能通过插件解析",
                checkedAt = System.currentTimeMillis(),
            )
        }
        val started = System.currentTimeMillis()
        return try {
            val songs = withContext(Dispatchers.IO) {
                PlatformMusicService.search(keyword, limit = 5, platform = platform.code)
            }
            val cost = System.currentTimeMillis() - started
            val ok = songs.isNotEmpty()
            SourceProbe(
                target = id,
                status = SourceProbe.statusOf(cost, ok),
                latencyMs = cost,
                detail = if (ok) {
                    "搜索「$keyword」命中 ${songs.size} 首，例如 ${songs.first().title}"
                } else {
                    "搜索「$keyword」没有返回结果（接口可能已变更）"
                },
                checkedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            val cost = System.currentTimeMillis() - started
            SourceProbe(
                target = id,
                status = ProbeStatus.FAIL,
                latencyMs = cost,
                detail = "搜索请求失败：${e.message ?: "未知错误"}",
                checkedAt = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun detectPlugin(
        plugin: SourcePlugin,
        platform: SourcePlatform,
        keyword: String,
    ): SourceProbe {
        val id = SourceProbe.pluginTarget(plugin.id, platform)
        if (!platform.hasBuiltin) {
            // 而无法区分"平台不支持"与"探测样本无效"，因此如实说明不能自动检测。
            return SourceProbe(
                target = id,
                status = ProbeStatus.UNTESTABLE,
                detail = "脚本已声明支持 ${platform.label}，但该平台没有内置搜索入口，无法自动取证（可实际播放一首该平台歌曲验证）",
                checkedAt = System.currentTimeMillis(),
            )
        }

        val started = System.currentTimeMillis()
        return try {
            val songs = withContext(Dispatchers.IO) {
                PlatformMusicService.search(keyword, limit = 3, platform = platform.code)
            }
            val song: Song = songs.firstOrNull()
                ?: return SourceProbe(
                    target = id,
                    status = ProbeStatus.FAIL,
                    latencyMs = System.currentTimeMillis() - started,
                    detail = "无法取得 ${platform.label} 的探测歌曲（搜索无结果），请稍后重试",
                    checkedAt = System.currentTimeMillis(),
                )

            val url = withContext(Dispatchers.IO) {
                UserApiEngine.resolveMusicUrl(platform.code, song, "128k").getOrNull()
            }
            if (url.isNullOrBlank()) {
                return SourceProbe(
                    target = id,
                    status = ProbeStatus.FAIL,
                    latencyMs = System.currentTimeMillis() - started,
                    detail = "脚本未解析出直链（探测歌曲：${song.title}）",
                    checkedAt = System.currentTimeMillis(),
                )
            }

            val head = probeUrl(url)
            val cost = System.currentTimeMillis() - started
            SourceProbe(
                target = id,
                status = SourceProbe.statusOf(cost, ok = head.ok),
                latencyMs = cost,
                detail = if (head.ok) {
                    "解析成功，地址可访问（${head.summary}）"
                } else {
                    "解析返回地址但不可访问：${head.summary}"
                },
                checkedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            SourceProbe(
                target = id,
                status = ProbeStatus.FAIL,
                latencyMs = System.currentTimeMillis() - started,
                detail = e.message ?: "检测异常",
                checkedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun detectPluginQualities(
        plugin: SourcePlugin,
        platform: SourcePlatform,
        qualities: List<String>,
        keyword: String = DEFAULT_KEYWORD,
        onQuality: (QualityProbe) -> Unit = {},
    ): List<QualityProbe> {
        if (qualities.isEmpty()) return emptyList()
        if (!platform.hasBuiltin) {
            return qualities.map { quality ->
                QualityProbe(
                    quality = quality,
                    status = ProbeStatus.UNTESTABLE,
                    detail = "${platform.label}没有内置搜索入口",
                ).also(onQuality)
            }
        }

        val songs = try {
            withContext(Dispatchers.IO) {
                PlatformMusicService.search(keyword, limit = 3, platform = platform.code)
            }
        } catch (e: Exception) {
            return qualities.map { quality ->
                QualityProbe(
                    quality = quality,
                    status = ProbeStatus.FAIL,
                    detail = "搜索失败：${e.message ?: "网络异常"}",
                ).also(onQuality)
            }
        }
        val song = songs.firstOrNull() ?: return qualities.map { quality ->
            QualityProbe(
                quality = quality,
                status = ProbeStatus.FAIL,
                detail = "无法取得探测歌曲（搜索无结果）",
            ).also(onQuality)
        }

        return qualities.map { quality ->
            val started = System.currentTimeMillis()
            val probe = try {
                val url = withContext(Dispatchers.IO) {
                    UserApiEngine.resolveMusicUrl(platform.code, song, quality).getOrNull()
                }
                if (url.isNullOrBlank()) {
                    QualityProbe(
                        quality = quality,
                        status = ProbeStatus.FAIL,
                        latencyMs = System.currentTimeMillis() - started,
                        detail = "脚本未返回地址",
                    )
                } else {
                    val head = probeUrl(url)
                    val cost = System.currentTimeMillis() - started
                    QualityProbe(
                        quality = quality,
                        status = if (head.ok) SourceProbe.statusOf(cost, ok = true) else ProbeStatus.FAIL,
                        latencyMs = cost,
                        sizeBytes = head.sizeBytes,
                        detail = head.summary,
                    )
                }
            } catch (e: Exception) {
                QualityProbe(
                    quality = quality,
                    status = ProbeStatus.FAIL,
                    latencyMs = System.currentTimeMillis() - started,
                    detail = e.message ?: "检测异常",
                )
            }
            onQuality(probe)
            probe
        }
    }

    data class UrlProbeResult(val ok: Boolean, val summary: String, val sizeBytes: Long = -1L)

    suspend fun probeUrl(url: String): UrlProbeResult = withContext(Dispatchers.IO) {
        val head = try {
            runCatching { execute(Request.Builder().url(url).head().build()) }.getOrNull()
        } catch (e: Exception) {
            null
        }
        val result = head?.takeIf { it.code != 405 && it.code != 403 && it.code != 501 }
            ?: runCatching {
                execute(
                    Request.Builder()
                        .url(url)
                        .header("Range", "bytes=0-0")
                        .build()
                )
            }.getOrNull()

        if (result == null) return@withContext UrlProbeResult(false, "无法连接音频地址")
        val audioLike = result.contentType.contains("audio", ignoreCase = true) ||
            result.contentType.contains("octet-stream", ignoreCase = true) ||
            result.contentType.contains("video", ignoreCase = true) ||
            result.contentType.contains("mpeg", ignoreCase = true)
        val ok = (result.code == 200 || result.code == 206) && audioLike
        val size = if (result.totalBytes > 0) result.totalBytes else result.length
        val sizeText = if (size > 0) "，${size / 1024}KB" else ""
        UrlProbeResult(
            ok = ok,
            summary = if (ok) {
                "HTTP ${result.code}，${result.contentType.ifBlank { "未知类型" }}$sizeText"
            } else {
                "HTTP ${result.code}" + if (result.contentType.isNotBlank()) "，类型 ${result.contentType}" else ""
            },
            sizeBytes = size,
        )
    }

    private data class ProbeResponse(
        val code: Int,
        val contentType: String,
        val length: Long,
        val totalBytes: Long,
    )

    private fun execute(request: Request): ProbeResponse {
        httpClient.newCall(request).execute().use { resp ->
            val total = resp.header("Content-Range")
                ?.substringAfterLast('/')
                ?.toLongOrNull()
                ?: -1L
            return ProbeResponse(
                code = resp.code,
                contentType = resp.header("Content-Type").orEmpty(),
                length = resp.header("Content-Length")?.toLongOrNull() ?: -1L,
                totalBytes = total,
            )
        }
    }

    companion object {
        private const val TAG = "SourceDetector"

        const val DEFAULT_KEYWORD = "周杰伦"
    }
}

sealed interface DetectTarget {
    val id: String
    val label: String

    data class Builtin(val platform: SourcePlatform) : DetectTarget {
        override val id: String = SourceProbe.builtinTarget(platform)
        override val label: String = "内置直连 · ${platform.label}"
    }

    data class PluginPlatform(val plugin: SourcePlugin, val platform: SourcePlatform) : DetectTarget {
        override val id: String = SourceProbe.pluginTarget(plugin.id, platform)
        override val label: String = "${plugin.name} · ${platform.label}"
    }
}
