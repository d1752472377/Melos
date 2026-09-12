package com.aesthetic.music.data.source

import android.util.Log
import com.aesthetic.music.data.api.LyricData
import com.aesthetic.music.data.api.SongUrlData
import com.aesthetic.music.data.api.platform.PlatformMusicService
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.userapi.ScriptMetadata
import com.aesthetic.music.data.userapi.UserApiEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 音源路由：把"用哪个音源解析这首歌"这件事集中到一处。
 *
 * 之前播放链路是写死的——`MusicRepository` 直接调 [UserApiEngine]，
 * 只有一个脚本时够用，但也就意味着没有"换源"一说。这里按 [SourcePolicy] 的策略
 * 依次尝试候选插件，失败继续，最后回落到内置直连，并把每次尝试的实测耗时记进 [SourceStatsStore]（自动最快的数据来源）。
 *
 * 线程与切换成本：QuickJS 沙箱一次只持有一个脚本，切换插件 = 重新编译执行一份 JS。
 * 因此这里记住"当前已加载的插件"，同源连续解析不再重复编译；只有真的要换源时才付这次代价。
 */
object SourceManager {

    private const val TAG = "SourceManager"

    /** 切换沙箱脚本必须串行：两个并发解析同时换源会互相把对方的脚本顶掉 */
    private val switchLock = Mutex()

    @Volatile
    private var loadedPluginId: String? = null

    /**
     * 最近一次成功取到直链的音源名（"某插件" 或 "官方直连"）。
     *
     * 用途：媒体卡片上的 `专辑 · 无损 · 音源` 信息行。真实报出来源比写死"自定义音源"更有用——
     * 用户能一眼看出这首歌是哪个源在供链，排查"某首歌播不了"时直接有用。
     */
    private val _lastSourceName = MutableStateFlow<String?>(null)
    val lastSourceName: StateFlow<String?> = _lastSourceName.asStateFlow()

    fun init(context: android.content.Context) {
        SourceRegistryStore.init(context)
        SourceStatsStore.init(context)
    }

    /**
     * 启动时后台加载"当前指定的插件"。
     *
     * 沙箱一次只持有一个脚本，加载动作统一由这里负责（[UserApiEngine] 不再自行加载旧单脚本文件），
     * 这样"沙箱里到底是哪个插件"与 [loadedPluginId] 永远一致，不会出现路由以为在用 A、实际加载的是 B。
     */
    suspend fun preloadActive(): Boolean {
        val active = SourceRegistryStore.activeId()?.let { SourceRegistryStore.find(it) }
            ?: SourceRegistryStore.list().firstOrNull { it.enable }
            ?: return false
        SourceRegistryStore.setActiveId(active.id)
        return ensureLoaded(active)
    }

    /**
     * 解析播放直链。
     *
     * @throws Exception 所有音源都失败时抛出，消息里带上尝试过的源，便于定位
     */
    suspend fun resolveUrl(song: Song, quality: String = "lossless"): SongUrlData {
        val prefs = SettingsStore.readPrefs()
        val probes = SourceStatsStore.all()
        val candidates = SourcePolicy.candidates(
            plugins = SourceRegistryStore.list(),
            songPlatform = song.platform,
            probes = probes,
            prefs = prefs,
            activeId = SourceRegistryStore.activeId(),
        )

        val tried = mutableListOf<String>()
        for (plugin in candidates) {
            val started = System.currentTimeMillis()
            val target = SourcePlatform.of(song.platform)
                ?.let { SourceProbe.pluginTarget(plugin.id, it) }
            val url = try {
                resolveWithPlugin(plugin, song, quality)
            } catch (e: Exception) {
                Log.w(TAG, "音源[${plugin.name}]解析失败: ${e.message}")
                null
            }
            val cost = System.currentTimeMillis() - started
            if (!url.isNullOrBlank()) {
                target?.let {
                    SourceStatsStore.recordObserved(it, cost, ok = true)
                    // 失败记录在同一次成功后被覆盖：源已经证明可用，不该继续背着"失败"标签
                }
                SourceRegistryStore.markUsed(plugin.id)
                _lastSourceName.value = plugin.name
                // 智能切换：本次实测明显变慢时不给提示也不强行换（播放不该被打断），
                // 但速度样本会让下一次"自动最快"改选更快的源。
                return SongUrlData(
                    url = url,
                    platform = song.platform,
                    sourceName = plugin.name,
                )
            }
            target?.let { SourceStatsStore.recordObserved(it, cost, ok = false, failReason = "未取到直链") }
            tried += plugin.name
            if (!SourcePolicy.shouldContinueAfterFailure(prefs)) break
        }

        // 兜底：内置平台直连（见 SourcePolicy.builtinFallbackAvailable 的说明）
        if (SourcePolicy.builtinFallbackAvailable(song.platform)) {
            try {
                val direct = PlatformMusicService.getDirectSongUrl(song, quality)
                if (!direct.url.isNullOrBlank()) {
                    _lastSourceName.value = direct.sourceName?.takeIf { it.isNotBlank() } ?: "内置直连"
                    return direct
                }
            } catch (e: Exception) {
                Log.w(TAG, "内置直连兜底失败 ${song.platform}:${song.id}", e)
            }
        }

        throw Exception(
            if (tried.isEmpty()) {
                "未获取到播放直链：请先在「插件管理」中导入音源插件"
            } else {
                "已尝试 ${tried.joinToString("、")}，均未取到播放直链"
            }
        )
    }

    /**
     * 解析歌词。
     *
     * 歌词不像直链那样必须用最快源：优先当前/指定的插件，失败再走内置接口，
     * 因此这里不做多源遍历（遍历会让歌词慢一倍，收益却很小）。
     */
    suspend fun resolveLyric(song: Song): LyricData {
        val plugin = SourcePolicy.candidates(
            plugins = SourceRegistryStore.list(),
            songPlatform = song.platform,
            probes = SourceStatsStore.all(),
            prefs = SettingsStore.readPrefs(),
            activeId = SourceRegistryStore.activeId(),
        ).firstOrNull()

        if (plugin != null) {
            try {
                ensureLoaded(plugin)
                val lrc = UserApiEngine.resolveLyric(song.platform, song).getOrNull()
                if (!lrc.isNullOrBlank()) {
                    return LyricData(lyric = lrc, platform = song.platform, sourceName = plugin.name)
                }
            } catch (e: Exception) {
                Log.w(TAG, "插件歌词解析失败[${plugin.name}]: ${e.message}")
            }
        }

        return PlatformMusicService.getLyric(song)
    }

    // ==================== 内部 ====================

    /**
     * 在"不会中途被换源"的前提下用某个插件执行一段解析。
     *
     * 沙箱一次只有一个脚本：如果只锁"加载"而放开"解析"，并发的另一处换源就会让本次解析
     * 落到别的脚本上（检测会把结果记到错误的插件名下）。因此加载与解析必须在同一把锁里。
     *
     * @return 插件不可用时返回 null（源码缺失/编译失败）
     */
    suspend fun <T> withPlugin(plugin: SourcePlugin, block: suspend () -> T): T? =
        switchLock.withLock {
            if (!ensureLoadedLocked(plugin)) return@withLock null
            block()
        }

    /** 用指定插件解析直链；脚本未就绪或换源失败时返回 null */
    private suspend fun resolveWithPlugin(plugin: SourcePlugin, song: Song, quality: String): String? =
        withPlugin(plugin) {
            UserApiEngine.resolveMusicUrl(song.platform, song, quality).getOrNull()
        }

    /**
     * 确保沙箱里加载的正是这个插件（调用方需已持有 [switchLock]，见 [withPlugin]）。
     *
     * @return 是否可用（脚本源码缺失或编译失败时为 false）
     */
    private suspend fun ensureLoadedLocked(plugin: SourcePlugin): Boolean {
        if (loadedPluginId == plugin.id && UserApiEngine.isAvailable()) return true
        val content = SourceRegistryStore.getContent(plugin.id)
        if (content.isNullOrBlank()) {
            Log.w(TAG, "插件源码缺失: ${plugin.name}(${plugin.id})")
            return false
        }
        val meta = ScriptMetadata(
            id = plugin.id,
            name = plugin.name,
            version = plugin.version,
            description = plugin.description,
            author = plugin.author,
            homepage = plugin.homepage,
        )
        val result = UserApiEngine.loadScript(content, meta, pluginId = plugin.id)
        if (result.isSuccess) {
            loadedPluginId = plugin.id
            // 顺手把脚本自报的支持源写回注册表，音源列表/检测据它展示
            val declared = UserApiEngine.declaredSources.value
            if (declared.isNotEmpty()) {
                SourceRegistryStore.update(plugin.id) { it.copy(declaredSources = declared) }
            }
            return true
        }
        loadedPluginId = null
        Log.w(TAG, "插件沙箱加载失败[${plugin.name}]: ${result.exceptionOrNull()?.message}")
        return false
    }

    /** 加载指定插件（供启动预热使用，内部自行加锁） */
    private suspend fun ensureLoaded(plugin: SourcePlugin): Boolean = switchLock.withLock {
        ensureLoadedLocked(plugin)
    }

    /** 供插件管理页"手动切换/重新加载"使用：强制重新加载某插件 */
    suspend fun activate(pluginId: String): Result<String> {
        val plugin = SourceRegistryStore.find(pluginId)
            ?: return Result.failure(Exception("未找到该插件"))
        SourceRegistryStore.setActiveId(plugin.id)
        loadedPluginId = null
        return if (ensureLoaded(plugin)) {
            SourceRegistryStore.markUsed(plugin.id)
            Result.success(plugin.name)
        } else {
            Result.failure(Exception("脚本加载失败，请检查脚本内容或重新导入"))
        }
    }

    /** 恢复"当前指定插件"到沙箱（检测跑完一圈后调用，避免沙箱停在最后被检测的插件上） */
    suspend fun restoreActive() {
        preloadActive()
    }

    /** 当前沙箱里加载的插件 id（删除插件时据此判断要不要把沙箱也清掉） */
    fun currentPluginIdOrNull(): String? = loadedPluginId

    /** 卸载当前沙箱脚本（保留插件记录，仅停止使用） */
    fun unloadEngine() {
        UserApiEngine.unloadScript()
        loadedPluginId = null
    }
}
