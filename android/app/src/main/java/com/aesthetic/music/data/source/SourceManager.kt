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
import com.aesthetic.music.service.LogReporter

object SourceManager {

    private const val TAG = "SourceManager"

    /** 切换沙箱脚本必须串行：两个并发解析同时换源会互相把对方的脚本顶掉 */
    private val switchLock = Mutex()

    @Volatile
    private var loadedPluginId: String? = null

    private val _lastSourceName = MutableStateFlow<String?>(null)
    val lastSourceName: StateFlow<String?> = _lastSourceName.asStateFlow()

    fun init(context: android.content.Context) {
        SourceRegistryStore.init(context)
        SourceStatsStore.init(context)
    }

    suspend fun preloadActive(): Boolean {
        val active = SourceRegistryStore.activeId()?.let { SourceRegistryStore.find(it) }
            ?: SourceRegistryStore.list().firstOrNull { it.enable }
            ?: return false
        SourceRegistryStore.setActiveId(active.id)
        return ensureLoaded(active)
    }

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

        val qualityList = when (quality.lowercase()) {
            "hires", "master", "flac", "lossless" -> listOf(quality, "320k", "128k")
            "320k" -> listOf("320k", "128k")
            else -> listOf(quality)
        }

        val tried = mutableListOf<String>()
        for (plugin in candidates) {
            val started = System.currentTimeMillis()
            val target = SourcePlatform.of(song.platform)
                ?.let { SourceProbe.pluginTarget(plugin.id, it) }
            var url: String? = null
            for (q in qualityList) {
                url = try {
                    resolveWithPlugin(plugin, song, q)
                } catch (e: Exception) {
                    Log.w(TAG, "音源[${plugin.name}]音质[$q]解析失败: ${e.message}")
                    null
                }
                if (!url.isNullOrBlank()) break
            }
            val cost = System.currentTimeMillis() - started
            if (!url.isNullOrBlank()) {
                target?.let {
                    SourceStatsStore.recordObserved(it, cost, ok = true)
                }
                SourceRegistryStore.markUsed(plugin.id)
                _lastSourceName.value = plugin.name
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

        if (SourcePolicy.builtinFallbackAvailable(song.platform)) {
            for (q in qualityList) {
                try {
                    val direct = PlatformMusicService.getDirectSongUrl(song, q)
                    if (!direct.url.isNullOrBlank()) {
                        _lastSourceName.value = direct.sourceName?.takeIf { it.isNotBlank() } ?: "内置直连"
                        return direct
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "内置直连音质[$q]兜底失败 ${song.platform}:${song.id}", e)
                }
            }
        }

        val altPlatforms = listOf("kuwo", "netease", "kugou", "qq").filter { it != song.platform }
        val searchKeyword = "${song.title} ${song.artist}".trim()
        if (searchKeyword.isNotBlank()) {
            for (altPlatform in altPlatforms) {
                try {
                    val searchCandidates = PlatformMusicService.search(searchKeyword, limit = 5, platform = altPlatform)
                    val matchedPair = SongMatcher.findBestMatch(song, searchCandidates, minScore = 0.75f)
                    if (matchedPair != null) {
                        val matchedSong = matchedPair.first
                        Log.i(TAG, "跨平台智能换源命中: [${song.platform}] ${song.title} -> [${matchedSong.platform}] ${matchedSong.title} (相似度: ${(matchedPair.second * 100).toInt()}%)")
                        for (q in qualityList) {
                            try {
                                val altDirect = PlatformMusicService.getDirectSongUrl(matchedSong, q)
                                if (!altDirect.url.isNullOrBlank()) {
                                    val sourceLabel = "换源: ${matchedSong.platform.uppercase()}"
                                    _lastSourceName.value = sourceLabel
                                    return altDirect.copy(sourceName = sourceLabel)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "跨平台换源搜索失败 [$altPlatform]: ${e.message}")
                }
            }
        }

        val failure = Exception(
            if (tried.isEmpty()) {
                "未获取到播放直链：请先在「插件管理」中导入音源插件"
            } else {
                "已尝试 ${tried.joinToString("、")}及跨平台换源，均未取到播放直链"
            }
        )
        LogReporter.e("SourceManager", "所有音源均失败 song=${song.platform}:${song.id}", failure)
        throw failure
    }

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

    suspend fun <T> withPlugin(plugin: SourcePlugin, block: suspend () -> T): T? =
        switchLock.withLock {
            if (!ensureLoadedLocked(plugin)) return@withLock null
            block()
        }

    private suspend fun resolveWithPlugin(plugin: SourcePlugin, song: Song, quality: String): String? =
        withPlugin(plugin) {
            UserApiEngine.resolveMusicUrl(song.platform, song, quality).getOrNull()
        }

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

    private suspend fun ensureLoaded(plugin: SourcePlugin): Boolean = switchLock.withLock {
        ensureLoadedLocked(plugin)
    }

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

    suspend fun restoreActive() {
        preloadActive()
    }

    /** 当前沙箱里加载的插件 id（删除插件时据此判断要不要把沙箱也清掉） */
    fun currentPluginIdOrNull(): String? = loadedPluginId

    fun unloadEngine() {
        UserApiEngine.unloadScript()
        loadedPluginId = null
    }
}
