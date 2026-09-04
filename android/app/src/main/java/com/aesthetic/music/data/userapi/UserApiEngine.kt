package com.aesthetic.music.data.userapi

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.model.Song
import com.google.gson.JsonObject
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Android 本地音源脚本执行引擎（基于 QuickJS）
 * 完整模拟 LX Music 客户端规范，在移动设备本地沙箱中安全运行用户音源脚本。
 */
object UserApiEngine {

    private const val TAG = "UserApiEngine"

    private var appContext: Context? = null
    private var handlerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    private var jsContext: QuickJSContext? = null
    private var instanceKey: String = ""
    private var isInited = false

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = AppJson.gson

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // 当前在途的 Native 业务请求（等待脚本解析完成），key 为 requestKey
    private val pendingNativeRequests = ConcurrentHashMap<String, CompletableDeferred<Result<String>>>()

    // 当前在途的 Script 发起的 HTTP 请求，key 为 requestKey，用于支持取消请求
    private val activeHttpCalls = ConcurrentHashMap<String, Call>()

    private val requestIdCounter = AtomicLong(1000)

    // 状态发布
    private val _activeMetadata = MutableStateFlow<ScriptMetadata?>(null)
    val activeMetadata: StateFlow<ScriptMetadata?> = _activeMetadata.asStateFlow()

    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        UserApiStore.init(context)

        // 启动专用 QuickJS 线程
        val thread = HandlerThread("MelosUserApiThread").apply { start() }
        handlerThread = thread
        workerHandler = Handler(thread.looper)

        workerHandler?.post {
            try {
                QuickJSLoader.init()
                Log.i(TAG, "QuickJSLoader initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "QuickJSLoader init failed: ${e.message}", e)
            }
        }

        // 尝试加载本地存储的历史脚本
        val savedScript = UserApiStore.getScriptContent()
        val savedMeta = UserApiStore.getMetadata()
        if (!savedScript.isNullOrBlank()) {
            loadScript(savedScript, savedMeta)
        }
    }

    fun isAvailable(): Boolean = _isEngineReady.value && jsContext != null

    fun getScriptName(): String = _activeMetadata.value?.name ?: "自定义音源"

    /**
     * 重新加载或安装新脚本
     */
    fun loadScript(scriptContent: String, meta: ScriptMetadata? = null) {
        val handler = workerHandler ?: return
        handler.post {
            destroyEngineInternal()

            val actualMeta = meta ?: UserApiStore.parseScriptHeader(scriptContent)
            instanceKey = UUID.randomUUID().toString()
            isInited = false

            try {
                val qjs = QuickJSContext.create()
                jsContext = qjs

                // 注入宿主通信桥梁与原生工具函数
                setupNativeBindings(qjs, instanceKey)

                // 加载并执行预加载脚本
                val preloadCode = readAsset("script/user-api-preload.js")
                if (preloadCode.isNullOrBlank()) {
                    Log.e(TAG, "Preload script missing in assets!")
                    return@post
                }
                qjs.evaluate(preloadCode)

                // 初始化全局 lx 环境
                qjs.getGlobalObject().getJSFunction("lx_setup")?.call(
                    instanceKey,
                    actualMeta.id.ifEmpty { "custom_script" },
                    actualMeta.name,
                    actualMeta.description,
                    actualMeta.version,
                    actualMeta.author,
                    actualMeta.homepage,
                    scriptContent
                )

                // 执行用户脚本源码
                qjs.evaluate(scriptContent)

                _activeMetadata.value = actualMeta
                _isEngineReady.value = true
                Log.i(TAG, "UserApi script loaded successfully: [${actualMeta.name}] v${actualMeta.version}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load UserApi script: ${e.message}", e)
                _isEngineReady.value = false
            }
        }
    }

    /**
     * 卸载清理当前运行的脚本环境
     */
    fun unloadScript() {
        workerHandler?.post {
            destroyEngineInternal()
            _activeMetadata.value = null
            _isEngineReady.value = false
            UserApiStore.clearScript()
        }
    }

    private fun destroyEngineInternal() {
        pendingNativeRequests.values.forEach { it.cancel() }
        pendingNativeRequests.clear()
        activeHttpCalls.values.forEach { it.cancel() }
        activeHttpCalls.clear()

        try {
            jsContext?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "QuickJS destroy exception: ${e.message}")
        }
        jsContext = null
        isInited = false
    }

    private fun readAsset(path: String): String? {
        return try {
            appContext?.assets?.open(path)?.use { input ->
                input.bufferedReader(StandardCharsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "readAsset failed for $path: ${e.message}")
            null
        }
    }

    /**
     * 注册 QuickJS 与 Native 之间的桥接函数
     */
    private fun setupNativeBindings(qjs: QuickJSContext, key: String) {
        val global = qjs.getGlobalObject()

        // 核心事件通信函数
        global.setProperty("__lx_native_call__", JSCallFunction { args ->
            if (args.isEmpty() || args[0] != key) return@JSCallFunction null
            val action = args.getOrNull(1) as? String ?: return@JSCallFunction null
            val data = args.getOrNull(2) as? String ?: ""
            handleJsNativeCall(action, data)
            null
        })

        // 定时器桥接
        global.setProperty("__lx_native_call__set_timeout", JSCallFunction { args ->
            val id = args.getOrNull(0)
            val timeout = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
            workerHandler?.postDelayed({
                callJsAction("__set_timeout__", id)
            }, timeout)
            null
        })

        // 常用加密与编码工具桥接
        global.setProperty("__lx_native_call__utils_str2b64", JSCallFunction { args ->
            val str = args.getOrNull(0) as? String ?: ""
            try {
                Base64.encodeToString(str.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            } catch (e: Exception) {
                ""
            }
        })

        global.setProperty("__lx_native_call__utils_b642buf", JSCallFunction { args ->
            val str = args.getOrNull(0) as? String ?: ""
            try {
                val bytes = Base64.decode(str, Base64.NO_WRAP)
                val sb = StringBuilder("[")
                for (i in bytes.indices) {
                    sb.append((bytes[i].toInt() and 0xFF))
                    if (i < bytes.size - 1) sb.append(",")
                }
                sb.append("]")
                sb.toString()
            } catch (e: Exception) {
                "[]"
            }
        })

        global.setProperty("__lx_native_call__utils_str2md5", JSCallFunction { args ->
            val encodedStr = args.getOrNull(0) as? String ?: ""
            try {
                val rawStr = URLDecoder.decode(encodedStr, "UTF-8")
                val md = MessageDigest.getInstance("MD5")
                val digest = md.digest(rawStr.toByteArray(StandardCharsets.UTF_8))
                digest.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                ""
            }
        })

        global.setProperty("__lx_native_call__utils_aes_encrypt", JSCallFunction { args ->
            val data = args.getOrNull(0) as? String ?: ""
            val keyStr = args.getOrNull(1) as? String ?: ""
            val ivStr = args.getOrNull(2) as? String ?: ""
            val mode = args.getOrNull(3) as? String ?: ""
            CryptoUtils.aesEncrypt(data, keyStr, ivStr, mode)
        })

        global.setProperty("__lx_native_call__utils_rsa_encrypt", JSCallFunction { args ->
            val data = args.getOrNull(0) as? String ?: ""
            val keyStr = args.getOrNull(1) as? String ?: ""
            val padding = args.getOrNull(2) as? String ?: ""
            CryptoUtils.rsaEncrypt(data, keyStr, padding)
        })
    }

    /**
     * 处理 JS 发给 Native 的各类指令
     */
    private fun handleJsNativeCall(action: String, data: String) {
        when (action) {
            "init" -> {
                isInited = true
                Log.i(TAG, "UserApi script reported inited: $data")
            }
            "request" -> {
                // 脚本请求执行 HTTP 访问
                handleScriptHttpRequest(data)
            }
            "cancelRequest" -> {
                val reqKey = data.trim('"')
                activeHttpCalls.remove(reqKey)?.cancel()
            }
            "response" -> {
                // 脚本完成了一次音源解析，将结果回传给 Native
                handleScriptActionResponse(data)
            }
            "showUpdateAlert" -> {
                Log.i(TAG, "Script update alert: $data")
            }
            else -> {
                Log.w(TAG, "Unhandled nativeCall action: $action")
            }
        }
    }

    /**
     * 执行脚本发起的 HTTP 网络请求
     */
    private fun handleScriptHttpRequest(rawJson: String) {
        engineScope.launch {
            try {
                val root = gson.fromJson(rawJson, JsonObject::class.java)
                val requestKey = root.get("requestKey")?.asString ?: return@launch
                val url = root.get("url")?.asString ?: return@launch
                val options = root.getAsJsonObject("options") ?: JsonObject()

                val method = options.get("method")?.asString?.uppercase() ?: "GET"
                val headersObj = options.getAsJsonObject("headers")
                val timeout = options.get("timeout")?.asLong ?: 15000L

                val reqBuilder = Request.Builder().url(url)
                reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

                if (headersObj != null) {
                    for (entry in headersObj.entrySet()) {
                        if (!entry.value.isJsonNull) {
                            reqBuilder.header(entry.key, entry.value.asString)
                        }
                    }
                }

                if (method != "GET" && method != "HEAD") {
                    val bodyStr = options.get("body")?.let {
                        if (it.isJsonObject || it.isJsonArray) it.toString() else it.asString
                    }
                    val formObj = options.getAsJsonObject("form")

                    val reqBody = when {
                        formObj != null -> {
                            val formBuilder = FormBody.Builder()
                            for (entry in formObj.entrySet()) {
                                formBuilder.add(entry.key, entry.value.asString)
                            }
                            formBuilder.build()
                        }
                        bodyStr != null -> {
                            val mediaType = headersObj?.get("Content-Type")?.asString
                                ?: headersObj?.get("content-type")?.asString
                                ?: "application/json; charset=utf-8"
                            bodyStr.toRequestBody(mediaType.toMediaTypeOrNull())
                        }
                        else -> {
                            ByteArray(0).toRequestBody(null)
                        }
                    }
                    reqBuilder.method(method, reqBody)
                } else {
                    reqBuilder.method(method, null)
                }

                val client = if (timeout != 15000L) {
                    httpClient.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS).build()
                } else {
                    httpClient
                }

                val call = client.newCall(reqBuilder.build())
                activeHttpCalls[requestKey] = call

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        activeHttpCalls.remove(requestKey)
                        sendHttpResponseToJs(requestKey, error = e.message ?: "Network error", response = null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        activeHttpCalls.remove(requestKey)
                        try {
                            val code = response.code
                            val responseBody = response.body?.string().orEmpty()
                            val headersMap = mutableMapOf<String, String>()
                            for (name in response.headers.names()) {
                                headersMap[name] = response.header(name).orEmpty()
                            }

                            // 尝试将响应体解析为 JSON 结构，方便 JS 脚本直接以对象形式读取
                            val parsedBody: Any = try {
                                gson.fromJson(responseBody, JsonObject::class.java)
                            } catch (_: Exception) {
                                responseBody
                            }

                            val respMap = mapOf(
                                "statusCode" to code,
                                "headers" to headersMap,
                                "body" to parsedBody,
                                "rawText" to responseBody
                            )

                            sendHttpResponseToJs(requestKey, error = null, response = respMap)
                        } catch (e: Exception) {
                            sendHttpResponseToJs(requestKey, error = e.message ?: "Read body failed", response = null)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "handleScriptHttpRequest error: ${e.message}", e)
            }
        }
    }

    private fun sendHttpResponseToJs(requestKey: String, error: String?, response: Any?) {
        val payload = mapOf(
            "requestKey" to requestKey,
            "error" to error,
            "response" to response
        )
        callJsAction("response", payload)
    }

    /**
     * 处理脚本返回的解析结果（响应给 Kotlin 调用的挂起函数）
     */
    private fun handleScriptActionResponse(rawJson: String) {
        try {
            val root = gson.fromJson(rawJson, JsonObject::class.java)
            val reqKey = root.get("requestKey")?.asString ?: return
            val deferred = pendingNativeRequests.remove(reqKey) ?: return

            val status = root.get("status")?.asBoolean ?: false
            if (status) {
                val resultElem = root.get("result")
                val url = when {
                    resultElem == null || resultElem.isJsonNull -> null
                    resultElem.isJsonPrimitive -> resultElem.asString
                    resultElem.isJsonObject -> {
                        resultElem.asJsonObject.get("url")?.asString
                    }
                    else -> resultElem.toString()
                }

                if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    deferred.complete(Result.success(url))
                } else if (!url.isNullOrBlank()) {
                    // 部分歌词直接返回整段文本而非直链 URL
                    deferred.complete(Result.success(url))
                } else {
                    deferred.complete(Result.failure(Exception("音源脚本返回的地址无效")))
                }
            } else {
                val msg = root.get("errorMessage")?.asString ?: "音源脚本解析失败"
                deferred.complete(Result.failure(Exception(msg)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleScriptActionResponse exception: ${e.message}", e)
        }
    }

    private fun callJsAction(action: String, data: Any?) {
        val handler = workerHandler ?: return
        handler.post {
            try {
                val jsonStr = if (data != null) gson.toJson(data) else null
                val fn = jsContext?.getGlobalObject()?.getJSFunction("__lx_native__")
                fn?.call(instanceKey, action, jsonStr)
            } catch (e: Exception) {
                Log.e(TAG, "callJsAction error ($action): ${e.message}")
            }
        }
    }

    /**
     * 核心接口：解析歌曲音频播放直链 URL
     */
    suspend fun resolveMusicUrl(platform: String, song: Song, quality: String): Result<String> {
        if (!isAvailable()) {
            return Result.failure(Exception("音源脚本未就绪或未安装"))
        }

        val lxSource = mapPlatformToLxSource(platform)
        val lxQuality = mapQualityToLxQuality(quality)

        val requestKey = "req_url_${requestIdCounter.incrementAndGet()}"
        val deferred = CompletableDeferred<Result<String>>()
        pendingNativeRequests[requestKey] = deferred

        val requestData = mapOf(
            "requestKey" to requestKey,
            "data" to mapOf(
                "source" to lxSource,
                "action" to "musicUrl",
                "info" to mapOf(
                    "type" to "music",
                    "quality" to lxQuality,
                    "musicInfo" to mapOf(
                        "id" to song.id,
                        "songmid" to song.id,
                        "hash" to song.id,
                        "copyrightId" to song.id,
                        "title" to song.title,
                        "name" to song.title,
                        "artist" to song.artist,
                        "singer" to song.artist,
                        "album" to song.album,
                        "platform" to lxSource
                    )
                )
            )
        )

        callJsAction("request", requestData)

        // 15 秒超时保护
        val result = withTimeoutOrNull(15000L) {
            deferred.await()
        }

        pendingNativeRequests.remove(requestKey)

        return result ?: Result.failure(Exception("音源脚本解析超时（15秒）"))
    }

    /**
     * 核心接口：解析歌词
     */
    suspend fun resolveLyric(platform: String, song: Song): Result<String> {
        if (!isAvailable()) {
            return Result.failure(Exception("音源脚本未就绪或未安装"))
        }

        val lxSource = mapPlatformToLxSource(platform)
        val requestKey = "req_lrc_${requestIdCounter.incrementAndGet()}"
        val deferred = CompletableDeferred<Result<String>>()
        pendingNativeRequests[requestKey] = deferred

        val requestData = mapOf(
            "requestKey" to requestKey,
            "data" to mapOf(
                "source" to lxSource,
                "action" to "lyric",
                "info" to mapOf(
                    "type" to "music",
                    "musicInfo" to mapOf(
                        "id" to song.id,
                        "songmid" to song.id,
                        "hash" to song.id,
                        "copyrightId" to song.id,
                        "title" to song.title,
                        "name" to song.title,
                        "artist" to song.artist,
                        "album" to song.album,
                        "platform" to lxSource
                    )
                )
            )
        )

        callJsAction("request", requestData)

        val result = withTimeoutOrNull(10000L) {
            deferred.await()
        }

        pendingNativeRequests.remove(requestKey)

        return result ?: Result.failure(Exception("歌词解析超时（10秒）"))
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
