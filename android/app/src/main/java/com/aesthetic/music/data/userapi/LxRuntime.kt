package com.aesthetic.music.data.userapi

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Thin adapter over whichever QuickJS binding you pick (see HarlonQuickJsEngine.kt for
 * wang.harlon.quickjs:wrapper-android, which Melos/android already uses). All calls are
 * made from ONE thread (LxRuntime's js executor); the engine is not thread-safe.
 *
 *  - bindHost: expose a global object `__host` whose methods map to [handlers] by name.
 *              Arguments arrive as Kotlin values (String / Number); return String or null.
 *  - evaluate: run script text as a global script.
 *  - callGlobal: call e.g. "__lxCore.onTimer" with the given args (then drain microtasks).
 */
interface JsEngine : AutoCloseable {
    fun bindHost(handlers: Map<String, (List<Any?>) -> Any?>)
    fun evaluate(code: String, fileName: String)
    fun callGlobal(path: String, vararg args: Any?)
}

/**
 * Same contract as src/node/runtime.js: one instance == one user script.
 * [coreJs] is build/lx-core.js (put it in assets/ and read it as text).
 *
 * infoJson: {"name","description","version","author","homepage", "env":"mobile"}
 */
class LxRuntime(
    private val engineFactory: () -> JsEngine,
    private val coreJs: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val onLog: (String) -> Unit = {},
    private val onUpdateAlert: (JSONObject) -> Unit = {},
    private val onOpenDevTools: () -> Unit = {},
) : AutoCloseable {

    private val jsThread = Executors.newSingleThreadExecutor { Thread(it, "lx-js").apply { isDaemon = true } }
    private val timerPool = Executors.newSingleThreadScheduledExecutor { Thread(it, "lx-timer").apply { isDaemon = true } }
    private val timers = ConcurrentHashMap<Int, ScheduledFuture<*>>()
    private val calls = ConcurrentHashMap<Int, Call>()
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val initDone = CompletableDeferred<JSONObject>()
    private var seq = 0

    @Volatile private var closed = false
    private lateinit var engine: JsEngine

    /** Run [block] on the JS thread. */
    private fun onJs(block: () -> Unit) {
        if (closed) return
        jsThread.execute { if (!closed) runCatching(block).onFailure { onLog("[js-error] ${it.message}") } }
    }

    // ---------------------------------------------------------------- public API

    data class InitResult(val ready: Boolean, val sources: JSONObject?, val message: String?)

    suspend fun load(script: String, infoJson: String = "{}", initTimeoutMs: Long = 8_000): InitResult {
        if (closed) return InitResult(false, null, "runtime closed")
        val ready = CompletableDeferred<Unit>()
        jsThread.execute {
            try {
                engine = engineFactory()
                engine.bindHost(hostHandlers())
                engine.evaluate(coreJs, "lx-core.js")
                ready.complete(Unit)
            } catch (t: Throwable) {
                ready.completeExceptionally(t)
            }
        }
        try {
            ready.await()
        } catch (t: Throwable) {
            return InitResult(false, null, t.message ?: "js engine init failed")
        }

        val info = JSONObject(infoJson).put("script", script)
        if (!info.has("proxy")) info.put("proxy", JSONObject().put("host", "").put("port", ""))
        jsThread.execute {
            try {
                engine.callGlobal("__lxCore.init", info.toString())
            } catch (t: Throwable) {
                initDone.completeExceptionally(t)
            }
        }

        val m = try {
            withTimeout(initTimeoutMs) { initDone.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            return InitResult(false, null, "source init timeout")
        } catch (t: Throwable) {
            return InitResult(false, null, t.message ?: "source init failed")
        }
        return if (m.optBoolean("status")) {
            InitResult(true, m.optJSONObject("data")?.optJSONObject("sources"), null)
        } else {
            InitResult(false, null, m.optString("message"))
        }
    }

    /** action: "musicUrl" | "lyric" | "pic"; reqInfo e.g. {"type":"320k","musicInfo":{...}} */
    suspend fun request(source: String, action: String, reqInfo: JSONObject, timeoutMs: Long = 20_000): JSONObject {
        if (closed) throw IOException("runtime closed")
        val key = "request__${++seq}"
        val d = CompletableDeferred<JSONObject>()
        waiters[key] = d
        val payload = JSONObject()
            .put("requestKey", key)
            .put("data", JSONObject().put("source", source).put("action", action).put("info", reqInfo))
        jsThread.execute {
            try {
                engine.callGlobal("__lxCore.request", payload.toString())
            } catch (t: Throwable) {
                waiters.remove(key)
                d.completeExceptionally(t)
            }
        }
        return try {
            withTimeout(timeoutMs) { d.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            waiters.remove(key)
            throw IOException("music API request timeout")
        }
    }

    suspend fun musicUrl(source: String, quality: String, musicInfo: JSONObject): String =
        request(source, "musicUrl", JSONObject().put("type", quality).put("musicInfo", musicInfo))
            .getJSONObject("data").getString("url")

    override fun close() {
        if (closed) return
        closed = true
        timers.values.forEach { it.cancel(false) }
        calls.values.forEach { it.cancel() }
        waiters.values.forEach { it.completeExceptionally(IOException("Cancel request")) }
        timerPool.shutdownNow()
        jsThread.execute { runCatching { engine.close() } }
        jsThread.shutdown()
    }

    // ---------------------------------------------------------------- host functions (called by JS)

    private fun hostHandlers(): Map<String, (List<Any?>) -> Any?> = mapOf(
        "log" to { a -> onLog(a[0].toString()); null },

        // core -> app messages: {channel, data, status, message}
        "deliver" to { a ->
            val m = JSONObject(a[0].toString())
            when (m.optString("channel")) {
                "init" -> initDone.complete(m)
                "response" -> {
                    val key = m.optJSONObject("data")?.optString("requestKey")
                    val d = key?.let { waiters.remove(it) }
                    if (m.optBoolean("status")) d?.complete(m.getJSONObject("data").getJSONObject("result"))
                    else d?.completeExceptionally(IOException(m.optString("message", "music API request failed")))
                }
                "showUpdateAlert" -> m.optJSONObject("data")?.let(onUpdateAlert)
                "openDevTools" -> onOpenDevTools()
            }
            null
        },

        "timerStart" to { a ->
            val id = (a[0] as Number).toInt()
            val ms = (a[1] as Number).toLong()
            timers[id] = timerPool.schedule({
                timers.remove(id)
                onJs { engine.callGlobal("__lxCore.onTimer", id) }
            }, ms, TimeUnit.MILLISECONDS)
            null
        },
        "timerClear" to { a -> timers.remove((a[0] as Number).toInt())?.cancel(false); null },

        "httpStart" to { a -> httpStart((a[0] as Number).toInt(), JSONObject(a[1].toString())); null },
        "httpAbort" to { a -> calls.remove((a[0] as Number).toInt())?.cancel(); null },

        "md5" to { a -> MessageDigest.getInstance("MD5").digest(unb64(a[0])).joinToString("") { "%02x".format(it) } },
        "aesEncrypt" to { a -> b64(aes(a[0].toString(), unb64(a[1]), unb64(a[2]), a[3].toString())) },
        "rsaEncrypt" to { a -> b64(rsaNoPadding(unb64(a[0]), a[1].toString())) },
        "randomBytes" to { a -> b64(ByteArray((a[0] as Number).toInt()).also { SecureRandom().nextBytes(it) }) },
        "inflate" to { a -> b64(inflate(unb64(a[0]))) },
        "deflate" to { a -> b64(deflate(unb64(a[0]))) },
    )

    // ---------------------------------------------------------------- HTTP (OkHttp)

    private fun httpStart(id: Int, r: JSONObject) {
        try {
            val method = r.optString("method", "get").uppercase()
            val body: RequestBody? = when {
                r.has("body") -> r.getString("body").toRequestBody(null)
                r.has("bodyB64") -> unb64(r.getString("bodyB64")).toRequestBody("application/octet-stream".toMediaType())
                r.has("json") -> r.get("json").toString().toRequestBody("application/json".toMediaType())
                r.has("form") -> FormBody.Builder().also { fb ->
                    r.getJSONObject("form").let { o -> o.keys().forEach { k -> fb.add(k, o.get(k).toString()) } }
                }.build()
                r.has("formData") -> MultipartBody.Builder().setType(MultipartBody.FORM).also { mb ->
                    r.getJSONObject("formData").let { o -> o.keys().forEach { k -> mb.addFormDataPart(k, o.get(k).toString()) } }
                }.build()
                method in setOf("POST", "PUT", "PATCH") -> ByteArray(0).toRequestBody(null)
                else -> null
            }
            val rb = Request.Builder().url(r.getString("url")).method(method, body)
            r.optJSONObject("headers")?.let { h -> h.keys().forEach { k -> rb.header(k, h.get(k).toString()) } }

            // needle does not follow redirects and has no default User-Agent; mirror that.
            val builder = client.newBuilder()
                .followRedirects(false).followSslRedirects(false)
                .callTimeout(r.optLong("timeout", 60_000), TimeUnit.MILLISECONDS)

            // Per-request proxy, same as src/node/http-needle.js.
            r.optJSONObject("proxy")?.let { p ->
                val host = p.optString("host")
                if (host.isNotEmpty()) {
                    builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, p.optInt("port", 80))))
                }
            }

            val call = builder.build().newCall(rb.build())
            calls[id] = call
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (calls.remove(id) == null) return
                    onJs { engine.callGlobal("__lxCore.onHttp", id, JSONObject().put("err", e.message ?: "request failed").toString()) }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (calls.remove(id) == null) { response.close(); return }
                    val res = response.use { resp ->
                        val headers = JSONObject()
                        for (n in resp.headers.names()) headers.put(n.lowercase(), resp.headers.values(n).joinToString(", "))
                        JSONObject()
                            .put("statusCode", resp.code)
                            .put("statusMessage", resp.message)
                            .put("headers", headers)
                            .put("rawB64", b64(resp.body?.bytes() ?: ByteArray(0)))
                    }
                    onJs { engine.callGlobal("__lxCore.onHttp", id, res.toString()) }
                }
            })
        } catch (t: Throwable) {
            calls.remove(id)
            onJs { engine.callGlobal("__lxCore.onHttp", id, JSONObject().put("err", t.message ?: "bad request").toString()) }
        }
    }

    // ---------------------------------------------------------------- crypto / zlib helpers

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: Any?) = Base64.decode(s.toString(), Base64.DEFAULT)

    /** mode like "aes-128-cbc" / "aes-256-ecb" (same strings node's createCipheriv takes). */
    private fun aes(mode: String, data: ByteArray, key: ByteArray, iv: String): ByteArray {
        val parts = mode.lowercase().split('-')
        require(parts.size == 3 && parts[0] == "aes") { "unsupported cipher: $mode" }
        val c = when (parts[2]) {
            "cbc" -> Cipher.getInstance("AES/CBC/PKCS5Padding").also {
                it.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(unb64(iv)))
            }
            "ecb" -> Cipher.getInstance("AES/ECB/PKCS5Padding").also {
                it.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            }
            else -> throw IllegalArgumentException("unsupported cipher mode: $mode")
        }
        return c.doFinal(data)
    }

    /** Public key in X.509 PEM ("BEGIN PUBLIC KEY"). core.js already left-pads data to 128 bytes. */
    private fun rsaNoPadding(data: ByteArray, pem: String): ByteArray {
        val der = Base64.decode(
            pem.replace(Regex("-----[A-Z ]+-----"), "").replace(Regex("\\s"), ""), Base64.DEFAULT
        )
        val pub = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        return Cipher.getInstance("RSA/ECB/NoPadding").also { it.init(Cipher.ENCRYPT_MODE, pub) }.doFinal(data)
    }

    private fun inflate(b: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(b)
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inf.end()
        return out.toByteArray()
    }

    private fun deflate(b: ByteArray): ByteArray {
        val def = Deflater()
        def.setInput(b)
        def.finish()
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!def.finished()) out.write(buf, 0, def.deflate(buf))
        def.end()
        return out.toByteArray()
    }
}
