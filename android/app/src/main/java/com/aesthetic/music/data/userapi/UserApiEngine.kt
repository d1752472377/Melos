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
            val message = t.message.orEmpty()
            // 部分音源脚本的 lyric 接口在无歌词时返回 null，脚本自己访问 data.lyric 会抛
            // “cannot read property 'lyric' of null”。这是正常的“没有歌词”，不上报 WARN。
            if (message.contains("lyric") && message.contains("null")) {
                return Result.failure(Exception("音源未提供歌词"))
            }
            LogReporter.w("UserApi", "歌词解析失败 platform=$platform", t)
            Result.failure(Exception(message.ifBlank { "歌词解析失败" }))
        }
    }

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
