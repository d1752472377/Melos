package com.aesthetic.music.ui.source

import android.content.Context
import android.net.Uri
import android.util.Log
import com.aesthetic.music.data.source.DetectTarget
import com.aesthetic.music.data.source.PlatformQualityGroup
import com.aesthetic.music.data.source.PluginDetectState
import com.aesthetic.music.data.source.ProbeStatus
import com.aesthetic.music.data.source.SourceDetector
import com.aesthetic.music.data.source.SourceManager
import com.aesthetic.music.data.source.SourcePlatform
import com.aesthetic.music.data.source.SourcePlugin
import com.aesthetic.music.data.source.SourceProbe
import com.aesthetic.music.data.source.SourceRegistryStore
import com.aesthetic.music.service.LogReporter
import com.aesthetic.music.data.source.SourceStatsStore
import com.aesthetic.music.data.userapi.UserApiEngine
import com.aesthetic.music.data.userapi.UserApiStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 插件管理页的状态与动作。
 *
 * 页面本身只做渲染：插件列表、检测结论、当前生效源都从这里取；
 * 导入/启用/指定平台/检测/删除都在这里落地，并负责把"用户点了但没生效"的原因讲清楚
 * （例如"这是一份 HTML 而不是脚本"），而不是静默失败。
 */
class SourceController(
    private val scope: CoroutineScope,
    private val onToast: (String) -> Unit,
) {

    private val _plugins = MutableStateFlow(SourceRegistryStore.list())
    val plugins: StateFlow<List<SourcePlugin>> = _plugins.asStateFlow()

    private val _probes = MutableStateFlow(SourceStatsStore.all())
    val probes: StateFlow<Map<String, SourceProbe>> = _probes.asStateFlow()

    /** 当前需要展示/检测的全部目标（内置四平台 + 每个插件声明的平台） */
    private val _targets = MutableStateFlow(emptyList<DetectTarget>())
    val targets: StateFlow<List<DetectTarget>> = _targets.asStateFlow()

    private val _activeId = MutableStateFlow(SourceRegistryStore.activeId())
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _detecting = MutableStateFlow(false)
    val detecting: StateFlow<Boolean> = _detecting.asStateFlow()

    private val _detectProgress = MutableStateFlow(0 to 0)
    val detectProgress: StateFlow<Pair<Int, Int>> = _detectProgress.asStateFlow()

    private val _lastDetectAt = MutableStateFlow(0L)
    val lastDetectAt: StateFlow<Long> = _lastDetectAt.asStateFlow()

    /** 沙箱里的脚本 id（用于区分"已安装"与"正在使用"） */
    val loadedPluginId: StateFlow<String?> = UserApiEngine.loadedPluginId
    val engineReady: StateFlow<Boolean> = UserApiEngine.isEngineReady
    val engineError: StateFlow<String?> = UserApiEngine.engineError
    val declaredSources: StateFlow<List<String>> = UserApiEngine.declaredSources
    val declaredQualities: StateFlow<Map<String, List<String>>> = UserApiEngine.declaredQualities

    private val _pluginDetect = MutableStateFlow<PluginDetectState?>(null)
    val pluginDetect: StateFlow<PluginDetectState?> = _pluginDetect.asStateFlow()
    private var pluginDetectJob: kotlinx.coroutines.Job? = null

    private val detector = SourceDetector()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    init {
        refresh()
    }

    /** 重新读一遍注册表与检测结果（导入/删除/启用变更后调用） */
    fun refresh() {
        val list = SourceRegistryStore.list()
        _plugins.value = list
        _probes.value = SourceStatsStore.all()
        _targets.value = detector.buildTargets(list)
        _activeId.value = SourceRegistryStore.activeId()
    }

    // ==================== 导入 ====================

    /**
     * 导入脚本内容。
     *
     * @param targetPlatform 导入时指定的平台（空串 = 全平台通用）
     */
    fun importFromContent(content: String, targetPlatform: String) {
        val problem = validate(content)
        if (problem != null) {
            onToast(problem)
            return
        }
        val meta = UserApiStore.parseScriptHeader(content)
        val plugin = SourceRegistryStore.pluginFromMeta(meta, platformCode(targetPlatform), content)
        refresh()
        scope.launch {
            val result = SourceManager.activate(plugin.id)
            refresh()
            onToast(
                if (result.isSuccess) {
                    "已导入并启用「${plugin.name}」" + declaredSummary()
                } else {
                    "已保存「${plugin.name}」，但加载失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                }
            )
        }
    }

    /** 从系统文件选择器拿到的脚本文件：读取内容后走与文本导入相同的校验流程 */
    fun importFromUri(context: Context, uri: Uri, targetPlatform: String) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 4 * 1024 * 1024) null else bytes.toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            LogReporter.w("Source", "读取脚本文件失败", e)
            null
        }
        if (text.isNullOrBlank()) {
            LogReporter.w("Source", "导入脚本失败：文件为空或过大")
            onToast("读取文件失败，请换一个文件或用「从连接导入」")
            return
        }
        importFromContent(text, targetPlatform)
    }

    /** 从链接导入：先下载，再走与文件导入相同的校验与登记流程 */
    fun importFromUrl(url: String, targetPlatform: String, onFinished: (Boolean, String) -> Unit) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            onFinished(false, "请填写脚本链接")
            return
        }
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            onFinished(false, "链接需要以 http:// 或 https:// 开头")
            return
        }
        scope.launch {
            val content = try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(trimmed)
                        .header("User-Agent", BROWSER_UA)
                        .build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                        resp.body?.string().orEmpty()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "下载脚本失败: ${e.message}")
                LogReporter.w("Source", "下载脚本失败", e)
                onFinished(false, "下载失败：${e.message ?: "网络异常"}")
                return@launch
            }
            val problem = validate(content)
            if (problem != null) {
                LogReporter.w("Source", "脚本校验未通过：$problem")
                onFinished(false, problem)
                return@launch
            }
            importFromContent(content, targetPlatform)
            onFinished(true, "导入成功")
        }
    }

    /** 导入前校验：把"看起来会失败"的内容挡在沙箱外，并给出人能看懂的原因 */
    private fun validate(content: String): String? {
        val text = content.trim()
        if (text.isEmpty()) return "内容为空，没有可导入的脚本"
        if (text.length > MAX_SCRIPT_CHARS) return "脚本过大（超过 2MB），请确认文件是否正确"
        val head = text.take(512).lowercase()
        if (head.contains("<!doctype html") || head.contains("<html")) {
            return "这看起来是网页而不是脚本（可能下载到了网页或错误页）"
        }
        if (!head.contains("/*!") && !text.contains("musicUrl")) {
            // 不阻断：有些脚本写法不同，只是提醒可能不符合 LX 音源规范
            onToast("提示：脚本里没有找到 musicUrl 声明，可能不是标准 LX 音源")
        }
        return null
    }

    private fun declaredSummary(): String {
        val declared = UserApiEngine.declaredSources.value
        if (declared.isEmpty()) return ""
        return "，脚本声明支持 " + declared.joinToString("/") { lxLabel(it) }
    }

    fun setEnable(id: String, enable: Boolean) {
        SourceRegistryStore.update(id) { it.copy(enable = enable) }
        val currentActive = SourceRegistryStore.activeId()
        val currentLoaded = loadedPluginId.value
        if (!enable && (currentActive == id || currentLoaded == id)) {
            val next = SourceRegistryStore.list().firstOrNull { it.id != id && it.enable }
            if (next != null) {
                activate(next.id)
            } else {
                SourceRegistryStore.setActiveId(null)
                unload()
            }
        } else {
            refresh()
        }
    }

    /** 指定平台（空串 = 全平台通用） */
    fun setTargetPlatform(id: String, platformCode: String) {
        SourceRegistryStore.update(id) { it.copy(targetPlatform = platformCode) }
        refresh()
    }

    /** 手动指定当前音源（并立刻加载进沙箱，用户马上就能听到效果） */
    fun activate(id: String) {
        scope.launch {
            val result = SourceManager.activate(id)
            refresh()
            onToast(
                result.fold(
                    onSuccess = { "已切换到「$it」" },
                    onFailure = { it.message ?: "切换失败" },
                )
            )
        }
    }

    /** 重新加载当前指定插件（脚本改过、或沙箱异常时使用） */
    fun reloadActive() {
        scope.launch {
            val ok = SourceManager.preloadActive()
            refresh()
            onToast(if (ok) "音源已重新加载" else "重新加载失败，请检查脚本或重新导入")
        }
    }

    fun unload() {
        SourceManager.unloadEngine()
        refresh()
        onToast("已停止使用音源脚本，将回落到内置直连")
    }

    fun remove(id: String) {
        val name = SourceRegistryStore.find(id)?.name ?: "该插件"
        SourceRegistryStore.remove(id)
        if (SourceManager.currentPluginIdOrNull() == id) SourceManager.unloadEngine()
        refresh()
        onToast("已删除「$name」")
    }

    // ==================== 检测 ====================

    /**
     * 检测全部音源（真实请求）。
     *
     * 逐条回填结果：检测是网络操作，一次要跑好几秒，
     * 等全部完成才刷新会让用户以为"卡住了"。
     */
    fun detectAll() {
        if (_detecting.value) return
        val plugins = SourceRegistryStore.list()
        val targets = detector.buildTargets(plugins)
        if (targets.isEmpty()) {
            onToast("没有可检测的音源")
            return
        }
        _detecting.value = true
        _detectProgress.value = 0 to targets.size
        scope.launch {
            try {
                var done = 0
                val results = detector.detectAll(plugins) { probe ->
                    SourceStatsStore.save(listOf(probe))
                    _probes.value = SourceStatsStore.all()
                    done++
                    _detectProgress.value = done to targets.size
                }
                _lastDetectAt.value = System.currentTimeMillis()
                refresh()
                val ok = results.count { it.status == ProbeStatus.OK }
                val slow = results.count { it.status == ProbeStatus.SLOW }
                val fail = results.count { it.status == ProbeStatus.FAIL }
                val untestable = results.count { it.status == ProbeStatus.UNTESTABLE }
                onToast(
                    buildString {
                        append("检测完成：正常 $ok")
                        if (slow > 0) append(" / 偏慢 $slow")
                        if (fail > 0) append(" / 失败 $fail")
                        if (untestable > 0) append(" / 无法自动检测 $untestable")
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "检测异常: ${e.message}")
                LogReporter.w("Source", "音源检测异常", e)
                onToast("检测中断：${e.message ?: "未知错误"}")
            } finally {
                _detecting.value = false
                refresh()
            }
        }
    }

    /** 只重测一个目标 */
    fun detectOne(targetId: String) {
        if (_detecting.value) return
        val target = _targets.value.firstOrNull { it.id == targetId } ?: return
        scope.launch {
            val probe = detector.detectOne(target)
            SourceStatsStore.save(listOf(probe))
            _lastDetectAt.value = System.currentTimeMillis()
            refresh()
            onToast("${target.label}：${probe.status.label}（${probe.latencyLabel}）")
        }
    }

    // ==================== 插件逐档检测 ====================

    /**
     * 打开某个插件的检测：quick=true 只测声明的最高两档，false 测全部声明的音质。
     *
     * 结果放在 [pluginDetect] 里实时更新，不写 [SourceStatsStore]；列表卡片仍然使用
     * 平台级的检测结果。
     */
    fun startPluginDetect(pluginId: String, quick: Boolean) {
        if (pluginDetectJob?.isActive == true) return
        val plugin = SourceRegistryStore.find(pluginId)
        if (plugin == null) {
            onToast("插件不存在")
            return
        }
        val platforms = plugin.declaredPlatforms.ifEmpty { SourcePlatform.builtins }
        _pluginDetect.value = PluginDetectState(
            plugin = plugin,
            quick = quick,
            running = true,
            total = 0,
            done = 0,
            groups = platforms.map { PlatformQualityGroup(it) },
        )

        pluginDetectJob = scope.launch {
            try {
                val loaded = SourceManager.withPlugin(plugin) {
                    val declared = UserApiEngine.declaredQualities.value
                    val plan = platforms.associateWith { platform ->
                        val all = declared[platform.lxCode].orEmpty().ifEmpty { listOf("128k") }
                        if (quick) all.takeLast(2) else all
                    }
                    val total = plan.values.sumOf { it.size }
                    var done = 0
                    _pluginDetect.update { it?.copy(total = total) }

                    fun record(platform: SourcePlatform, probe: com.aesthetic.music.data.source.QualityProbe) {
                        done++
                        _pluginDetect.update { state ->
                            state?.copy(
                                done = done,
                                groups = state.groups.map { group ->
                                    if (group.platform == platform) {
                                        group.copy(qualities = group.qualities + probe)
                                    } else {
                                        group
                                    }
                                },
                            )
                        }
                    }

                    for ((platform, qualities) in plan) {
                        try {
                            detector.detectPluginQualities(plugin, platform, qualities) { probe ->
                                record(platform, probe)
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            qualities.forEach { quality ->
                                record(
                                    platform,
                                    com.aesthetic.music.data.source.QualityProbe(
                                        quality = quality,
                                        status = ProbeStatus.FAIL,
                                        detail = e.message ?: "检测异常",
                                    ),
                                )
                            }
                        }
                    }
                }
                _pluginDetect.update {
                    if (loaded == null) {
                        it?.copy(running = false, error = "脚本未能加载到沙箱")
                    } else {
                        it?.copy(running = false)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "插件检测中断: ${e.message}")
                LogReporter.w("Source", "插件检测中断", e)
                _pluginDetect.update { it?.copy(running = false, error = e.message ?: "检测中断") }
            } finally {
                // 取消/关闭面板时也要把沙箱恢复到用户当前指定的插件。
                withContext(kotlinx.coroutines.NonCancellable) {
                    runCatching { SourceManager.restoreActive() }
                }
            }
        }
    }

    /** 关闭检测面板；仍在跑就取消。 */
    fun clearPluginDetect() {
        if (pluginDetectJob?.isActive == true) {
            pluginDetectJob?.cancel()
        }
        pluginDetectJob = null
        _pluginDetect.value = null
    }

    companion object {
        private const val TAG = "SourceController"

        /** 脚本下载用的 UA：有些脚本托管站点对空 UA 直接 403 */
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36"

        private const val MAX_SCRIPT_CHARS = 2 * 1024 * 1024

        /** 平台选项：空串表示全平台通用，其余为四个内置平台（咪咕没有内置搜索，不单列） */
        val platformOptions: List<Pair<String, String>> =
            listOf("" to "全平台通用") +
                SourcePlatform.builtins.map { it.code to it.label }

        /** 把界面上的平台 code 归一化：非法值一律当作"通用" */
        fun platformCode(code: String): String = SourcePlatform.of(code)?.code ?: ""

        /** LX 源标识 → 显示名（脚本声明里用的是 wy/tx/kg/kw/mg） */
        fun lxLabel(lx: String): String = SourcePlatform.labelOf(lx)
    }
}
