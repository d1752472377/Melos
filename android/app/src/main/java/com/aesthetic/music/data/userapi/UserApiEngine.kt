package com.aesthetic.music.data.userapi

import android.content.Context
import android.util.Log
import com.aesthetic.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import com.aesthetic.music.service.LogReporter

/**
 * Android 本地音源脚本执行引擎。
 *
 * 现在它不再自带一套 preload.js + 原生桥接，而是加载服务器端同款的
 * `assets/script/lx-core.js`：同一份 JS、同一套 `__host` 契约，宿主实现见
 * [LxRuntime] / [HarlonQuickJsEngine]。这样服务器上验证过的脚本行为和 APK 一致。
 *
 * 对外 API（SourceManager / SourceController 使用）保持不变：
 * [loadScript]、[unloadScript]、[resolveMusicUrl]、[resolveLyric]、
 * [isAvailable] 以及 [declaredSources]、[isEngineReady]、[engineError]、[loadedPluginId]。
 */
object UserApiEngine {

    private const val TAG = "UserApiEngine"
    private const val CORE_ASSET = "script/lx-core.js"
    private const val INIT_TIMEOUT_MS = 8_000L
    private const val URL_TIMEOUT_MS = 15_000L
    private const val LYRIC_TIMEOUT_MS = 10_000L

    private var appContext: Context? = null
    private val loadMutex = Mutex()
    private val generation = AtomicLong(0)

    @Volatile private var runtime: LxRuntime? = null
    @Volatile private var ready = false
    @Volatile private var coreCache: String? = null

    private val _activeMetadata = MutableStateFlow<ScriptMetadata?>(UserApiStore.getMetadata())
    val activeMetadata: StateFlow<ScriptMetadata?> = _activeMetadata.asStateFlow()

    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError.asStateFlow()

    private val _declaredSources = MutableStateFlow<List<String>>(emptyList())
    val declaredSources: StateFlow<List<String>> = _declaredSources.asStateFlow()

    /** 脚本声明的音质（key 为 LX 源标识 wy/tx/kg/kw/mg），检测面板按它逐档测 */
    private val _declaredQualities = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val declaredQualities: StateFlow<Map<String, List<String>>> = _declaredQualities.asStateFlow()

    private val _loadedPluginId = MutableStateFlow<String?>(null)
    val loadedPluginId: StateFlow<String?> = _loadedPluginId.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        UserApiStore.init(context)
        UserApiStore.getMetadata()?.let { _activeMetadata.value = it }
    }

    fun isAvailable(): Boolean = ready && runtime != null

    fun getScriptName(): String = _activeMetadata.value?.name ?: "自定义音源"

    /**
     * 重新加载或安装新脚本，挂起等待 QuickJS 编译与 init 结果。
     *
     * 同一个对象只持有一个 [LxRuntime]；旧的会在新脚本成功加载后关闭。
     */
    suspend fun loadScript(
        scriptContent: String,
        meta: ScriptMetadata? = null,
        pluginId: String? = null,
    ): Result<ScriptMetadata> = loadMutex.withLock {
        val gen = generation.incrementAndGet()
        val actualMeta = meta ?: UserApiStore.parseScriptHeader(scriptContent)
        _activeMetadata.value = actualMeta
        _loadedPluginId.value = pluginId
        _declaredSources.value = emptyList()
        _declaredQualities.value = emptyMap()
        _isEngineReady.value = false
        _engineError.value = null

        closeRuntime()

        try {
            val core = readCoreAsset()
            val rt = LxRuntime(
                engineFactory = { HarlonQuickJsEngine() },
                coreJs = core,
                onLog = { line -> Log.d("UserApi-JS", line) },
                onUpdateAlert = { alert -> Log.i(TAG, "Script update alert: $alert") },
            )
            val info = JSONObject()
                .put("id", actualMeta.id)
                .put("name", actualMeta.name)
                .put("version", actualMeta.version)
                .put("description", actualMeta.description)
                .put("author", actualMeta.author)
                .put("homepage", actualMeta.homepage)
                .put("env", "mobile")

            val init = rt.load(scriptContent, info.toString(), INIT_TIMEOUT_MS)

            // unloadScript() / 新的 loadScript() 可能在这 8 秒里发生。
            if (gen != generation.get()) {
                rt.close()
                return@withLock Result.failure(Exception("加载已取消"))
            }
            if (!init.ready) {
                rt.close()
                val message = init.message ?: "source init failed"
                _engineError.value = message
                _activeMetadata.value = actualMeta
                Log.w(TAG, "用户脚本 init 失败: $message")
                return@withLock Result.failure(Exception(message))
            }

            runtime = rt
            ready = true
            _isEngineReady.value = true
            _engineError.value = null
            _declaredSources.value = declaredSourcesFrom(init.sources)
            _declaredQualities.value = declaredQualitiesFrom(init.sources)
            _activeMetadata.value = actualMeta
            Log.i(TAG, "用户脚本已加载: [${actualMeta.name}] v${actualMeta.version}, sources=${_declaredSources.value}")
            Result.success(actualMeta)
        } catch (t: Throwable) {
            closeRuntime()
            val message = t.message ?: t.toString()
            _engineError.value = message
            _isEngineReady.value = false
            _activeMetadata.value = actualMeta
            Log.e(TAG, "用户脚本加载失败: $message", t)
            LogReporter.e("UserApi", "用户脚本加载失败", t)
            Result.failure(Exception(message))
        }
    }

    /**
     * 卸载当前运行的脚本环境（不删除已安装的插件记录）。
     */
    fun unloadScript() {
        generation.incrementAndGet()
        closeRuntime()
        _activeMetadata.value = null
        _isEngineReady.value = false
        _engineError.value = null
        _declaredSources.value = emptyList()
        _declaredQualities.value = emptyMap()
        _loadedPluginId.value = null
    }

    private fun closeRuntime() {
        ready = false
        val old = runtime
        runtime = null
        old?.close()
    }

    private suspend fun readCoreAsset(): String {
        coreCache?.let { return it }
        val ctx = appContext ?: error("UserApiEngine 尚未初始化")
        return withContext(Dispatchers.IO) {
            ctx.assets.open(CORE_ASSET).use { input ->
                input.bufferedReader(StandardCharsets.UTF_8).readText()
            }
        }.also { coreCache = it }
    }

    /** 只列出真正声明了 musicUrl 的源（core 已经按上游规则过滤过 type/actions）。 */
    private fun declaredSourcesFrom(sources: JSONObject?): List<String> {
        if (sources == null) return emptyList()
        val result = mutableListOf<String>()
        val keys = sources.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = sources.optJSONObject(key) ?: continue
            if (obj.optString("type") != "music") continue
            val actions = obj.optJSONArray("actions") ?: continue
            var supportsUrl = false
            for (i in 0 until actions.length()) {
                if (actions.optString(i) == "musicUrl") {
                    supportsUrl = true
                    break
                }
            }
            if (supportsUrl) result.add(key.lowercase())
        }
        return result
    }

    /** key 是脚本的 LX 源标识（wy/tx/kg/kw/mg），value 是已过滤的音质列表。 */
    private fun declaredQualitiesFrom(sources: JSONObject?): Map<String, List<String>> {
        if (sources == null) return emptyMap()
        val result = linkedMapOf<String, List<String>>()
        val keys = sources.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = sources.optJSONObject(key) ?: continue
            val arr = obj.optJSONArray("qualitys") ?: continue
            val qualities = buildList {
                for (i in 0 until arr.length()) {
                    val quality = arr.optString(i)
                    if (quality.isNotBlank()) add(quality)
                }
            }
            if (qualities.isNotEmpty()) result[key.lowercase()] = qualities
        }
        return result
    }

    /**
     * 解析歌曲音频播放直链。
     */
    suspend fun resolveMusicUrl(platform: String, song: Song, quality: String): Result<String> {
        val rt = runtime
        if (!ready || rt == null) {
            return Result.failure(Exception("音源脚本未就绪或未安装"))
        }

        val lxSource = mapPlatformToLxSource(platform)
        val lxQuality = mapQualityToLxQuality(quality)
        val reqInfo = JSONObject()
            .put("type", lxQuality)
            .put("musicInfo", buildMusicInfo(song, lxSource))

        return try {
            val result = rt.request(lxSource, "musicUrl", reqInfo, URL_TIMEOUT_MS)
            val url = result.optJSONObject("data")?.optString("url").orEmpty()
            if (url.isBlank()) {
                Result.failure(Exception("音源脚本返回的地址无效"))
            } else {
                Result.success(url)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "resolveMusicUrl 失败: ${t.message}")
            LogReporter.w("UserApi", "取链失败 platform=$platform quality=$quality", t)
            Result.failure(Exception(t.message ?: "音源脚本解析失败"))
        }
    }

    /**
     * 解析歌词（在线源通常没有歌词动作，失败后 SourceManager 会回退到内置接口）。
     */
    suspend fun resolveLyric(platform: String, song: Song): Result<String> {
        val rt = runtime
        if (!ready || rt == null) {
            return Result.failure(Exception("音源脚本未就绪或未安装"))
        }

        val lxSource = mapPlatformToLxSource(platform)
        val reqInfo = JSONObject()
            .put("type", "128k")
            .put("musicInfo", buildMusicInfo(song, lxSource))

        return try {
            val result = rt.request(lxSource, "lyric", reqInfo, LYRIC_TIMEOUT_MS)
            val lyric = result.optJSONObject("data")?.optString("lyric").orEmpty()
            if (lyric.isBlank()) {
                Result.failure(Exception("音源脚本未返回歌词"))
            } else {
                Result.success(lyric)
            }
        } catch (t: Throwable) {
            LogReporter.w("UserApi", "歌词解析失败 platform=$platform", t)
            Result.failure(Exception(t.message ?: "歌词解析失败"))
        }
    }

    /**
     * 脚本收到的是 Q-music 的旧格式（songmid/hash/songId/copyrightId 平铺在顶层）。
     * 本 App 的 Song 只有 id，所以哪些字段脚本会读，就把 id 填到哪些字段里。
     */
    private fun buildMusicInfo(song: Song, source: String): JSONObject {
        val id = song.id
        val types = JSONArray().apply {
            put(JSONObject().put("type", "128k"))
            put(JSONObject().put("type", "320k"))
            put(JSONObject().put("type", "flac"))
            put(JSONObject().put("type", "flac24bit"))
        }
        return JSONObject()
            .put("id", id)
            .put("songmid", id)
            .put("songId", id)
            .put("hash", id)
            .put("copyrightId", id)
            .put("title", song.title)
            .put("name", song.title)
            .put("artist", song.artist)
            .put("singer", song.artist)
            .put("album", song.album)
            .put("albumName", song.album)
            .put("albumId", "")
            .put("img", song.artwork ?: "")
            .put("interval", "0")
            .put("platform", source)
            .put("source", source)
            .put("types", types)
            .put("_types", JSONObject())
    }

    private fun mapPlatformToLxSource(platform: String): String {
        return when (platform.lowercase()) {
            "kuwo", "kw" -> "kw"
            "kugou", "kg" -> "kg"
            "qq", "tx", "tencent" -> "tx"
            "netease", "wy", "163" -> "wy"
            "migu", "mg" -> "mg"
            else -> platform
        }
    }

    private fun mapQualityToLxQuality(quality: String): String {
        return when (quality.lowercase()) {
            "lossless", "flac" -> "flac"
            "high", "320k" -> "320k"
            "standard", "128k" -> "128k"
            "flac24bit" -> "flac24bit"
            else -> "128k"
        }
    }
}
