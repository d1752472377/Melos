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

/**
 * 音源检测：对每个源发**真实请求**，报告"正确与否"和"响应速度"。
 *
 * 检测内容（不是模拟，全部是真请求）：
 * 1. 内置直连源：向该平台发一次真实搜索请求 —— 判定解析是否正确（命中数 > 0）；
 * 2. 插件源：先用该平台真实搜索拿到一首真实歌曲，再让脚本解析它的直链，
 *    最后对拿到的地址发一次 HTTP 探测（HEAD，必要时退化为 Range 请求），
 *    只有"能取到音频地址且地址可访问"才算通过 —— 只判断"返回了字符串"会把 403/404 的失效源当成正常；
 * 3. 脚本声明了但我们没有搜索入口的平台（如咪咕）：如实标记"无法自动检测"，
 *    不猜、也不假装成功。
 *
 * 检测目标与并发：目标数量 = 内置 4 + 插件数 × 其声明平台数，通常十条上下，
 * 每个平台内部一次搜索一次解析，全部并发执行；单个目标失败不影响其它目标。
 */
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

    /**
     * 检测全部目标。
     *
     * 并行度安排（受"沙箱一次只有一个脚本"约束）：
     * - 内置直连目标彼此无冲突，全部并发；
     * - 插件目标按插件分组，**组间串行**（不同插件必须换源），组内多个平台**串行**执行，
     *   每次解析都通过 [SourceManager.withPlugin] 锁住沙箱，保证结果记在正确的插件名下。
     *   检测本身是低频操作，用几秒换"结论可信"是划算的。
     *
     * @param keyword 探测用关键词（默认取一个大众歌手，保证各平台都能搜到结果）
     * @param onProgress 每个目标出结果时回调（页面上逐条点亮，而不是等全部跑完）
     */
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
            // 一次性锁定该插件的沙箱会话，组内串行执行；加载失败时如实标记失败
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
        // 检测会把沙箱停在最后测过的插件上；跑完恢复"当前指定插件"，避免影响接下来的播放
        SourceManager.restoreActive()
    }

    /** 检测单个目标（页面上"重测这一项"用；插件目标会自动锁定沙箱） */
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

    /** 捕获网络异常并转成 FAIL 结论（保证一个目标失败不会中断整轮检测） */
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

    /** 组装当前需要检测的目标清单 */
    fun buildTargets(plugins: List<SourcePlugin>): List<DetectTarget> {
        val targets = mutableListOf<DetectTarget>()
        SourcePlatform.builtins.forEach { targets += DetectTarget.Builtin(it) }
        plugins.filter { it.enable }.forEach { plugin ->
            val declared = plugin.declaredPlatforms.ifEmpty {
                // 还没抓到脚本声明（未加载过）：按内置四平台先测，避免列表里"看起来没检测项"
                SourcePlatform.builtins
            }
            declared.forEach { platform ->
                targets += DetectTarget.PluginPlatform(plugin, platform)
            }
        }
        return targets
    }

    // ==================== 内置直连 ====================

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

    // ==================== 插件 ====================

    private suspend fun detectPlugin(
        plugin: SourcePlugin,
        platform: SourcePlatform,
        keyword: String,
    ): SourceProbe {
        val id = SourceProbe.pluginTarget(plugin.id, platform)
        if (!platform.hasBuiltin) {
            // 没有内置搜索就取不到该平台的真实歌曲；用假 ID 去测只会得到一个"脚本的失败"，
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
            // 1. 真实搜索，拿到一首该平台的真实歌曲（脚本解析需要真实 id/hash/songmid）
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

            // 2. 让脚本解析直链
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

            // 3. 探测真实地址是否可访问：这一步才是"请求正确与否"的关键
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

    // ==================== 地址探测 ====================

    data class UrlProbeResult(val ok: Boolean, val summary: String)

    /**
     * 探测音频地址是否真实可访问。
     *
     * 先 HEAD（最省流量）；有些 CDN 不支持 HEAD 会返回 405/403，此时退化为 `Range: bytes=0-0` 的 GET，
     * 只取一个字节即可判断状态码与 Content-Type —— 不下载整首歌。
     */
    suspend fun probeUrl(url: String): UrlProbeResult = withContext(Dispatchers.IO) {
        val head = try {
            runCatching { execute(Request.Builder().url(url).head().build()) }.getOrNull()
        } catch (e: Exception) {
            null
        }
        val result = head?.takeIf { it.first != 405 && it.first != 403 && it.first != 501 }
            ?: runCatching {
                execute(
                    Request.Builder()
                        .url(url)
                        .header("Range", "bytes=0-0")
                        .build()
                )
            }.getOrNull()

        if (result == null) return@withContext UrlProbeResult(false, "无法连接音频地址")
        val (code, contentType, length) = result
        val audioLike = contentType.contains("audio", ignoreCase = true) ||
            contentType.contains("octet-stream", ignoreCase = true) ||
            contentType.contains("video", ignoreCase = true) ||
            contentType.contains("mpeg", ignoreCase = true)
        val ok = (code == 200 || code == 206) && audioLike
        val sizeText = if (length > 0) "，${length / 1024}KB" else ""
        UrlProbeResult(
            ok = ok,
            summary = if (ok) {
                "HTTP $code，${contentType.ifBlank { "未知类型" }}$sizeText"
            } else {
                "HTTP $code" + if (contentType.isNotBlank()) "，类型 $contentType" else ""
            },
        )
    }

    /** 返回 (状态码, Content-Type, Content-Length) */
    private fun execute(request: Request): Triple<Int, String, Long> {
        httpClient.newCall(request).execute().use { resp ->
            return Triple(
                resp.code,
                resp.header("Content-Type").orEmpty(),
                resp.header("Content-Length")?.toLongOrNull() ?: -1L,
            )
        }
    }

    companion object {
        private const val TAG = "SourceDetector"

        /** 探测关键词：选一个各平台都有大量版权的歌手，避免"检测失败其实是搜不到" */
        const val DEFAULT_KEYWORD = "周杰伦"
    }
}

/** 检测目标 */
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
