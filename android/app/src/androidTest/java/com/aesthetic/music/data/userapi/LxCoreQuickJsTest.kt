package com.aesthetic.music.data.userapi

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * 在设备上真实跑 QuickJS（wang.harlon.quickjs:wrapper-android）。
 *
 * 覆盖：
 *  - build/lx-core.js 能在 QuickJS 里编译并 init
 *  - 页面全局对象在 QuickJS 里可用：URL / structuredClone / AbortController / TextEncoder
 *  - lx.request -> OkHttp -> 本地 HTTP 服务 -> 回调 -> response 的完整链路
 *  - 顶层抛错会作为 init 失败上报
 *
 * 运行：
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class LxCoreQuickJsTest {

    @Test
    fun unifiedCoreRunsInQuickJs() = runBlocking {
        val core = readCoreAsset()
        assertTrue("lx-core.js 资源缺失或为空（${core.length} 字节）", core.length > 100_000)

        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val serverThread = thread(isDaemon = true) { serve(server) }

        val logs = CopyOnWriteArrayList<String>()
        val runtime = LxRuntime(
            engineFactory = { HarlonQuickJsEngine() },
            coreJs = core,
            onLog = { line ->
                logs += line
                Log.d("LxCoreTest", line)
            },
        )
        try {
            val script = SCRIPT.replace("__PORT__", port.toString())
            val info = JSONObject()
                .put("name", "quickjs-smoke")
                .put("version", "1.0.0")
                .put("env", "mobile")

            val init = runtime.load(script, info.toString(), 8_000)
            assertTrue("init 失败: ${init.message}; logs=$logs", init.ready)
            assertTrue("脚本声明的 kw 源没有出现", init.sources?.has("kw") == true)

            val result = runtime.request(
                "kw",
                "musicUrl",
                JSONObject().put("type", "128k").put("musicInfo", JSONObject().put("name", "x")),
                8_000,
            )
            val url = result.getJSONObject("data").getString("url")

            // 脚本把 URL / structuredClone / AbortController / TextEncoder / lx.request 的结果
            // 全部编码进了这个直链。
            assertEquals("https://example.com/a.b:8080/true/1/3/pong/128k", url)
        } finally {
            runtime.close()
            server.close()
            serverThread.join(1_000)
        }
    }

    @Test
    fun initFailureIsReported() = runBlocking {        val core = readCoreAsset()
        val runtime = LxRuntime(
            engineFactory = { HarlonQuickJsEngine() },
            coreJs = core,
            onLog = { Log.d("LxCoreTest", it) },
        )
        try {
            val init = runtime.load(
                "/* @name broken @version 1.0.0 @author test */\nthrow new Error('boom from quickjs')",
                JSONObject().put("name", "broken").put("env", "mobile").toString(),
                8_000,
            )
            assertFalse("破损脚本不应 init 成功", init.ready)
            assertTrue("错误信息没有带回: ${init.message}", init.message?.contains("boom from quickjs") == true)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun userApiEngineLoadsAndResolves() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        UserApiEngine.init(ctx)

        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val serverThread = thread(isDaemon = true) { serve(server) }
        try {
            val script = SCRIPT.replace("__PORT__", port.toString())
            val meta = ScriptMetadata(id = "test-plugin", name = "quickjs-smoke", version = "1.0.0")
            val loaded = UserApiEngine.loadScript(script, meta, pluginId = "test-plugin")
            assertTrue("UserApiEngine.loadScript 失败: ${loaded.exceptionOrNull()?.message}", loaded.isSuccess)
            assertTrue("UserApiEngine 未就绪", UserApiEngine.isAvailable())
            assertEquals(listOf("kw"), UserApiEngine.declaredSources.value)

            val song = com.aesthetic.music.data.model.Song(
                id = "228908",
                title = "x",
                artist = "a",
                album = "b",
                platform = "kw",
            )
            val result = UserApiEngine.resolveMusicUrl("kw", song, "320k")
            assertTrue("resolveMusicUrl 失败: ${result.exceptionOrNull()?.message}", result.isSuccess)
            assertEquals("https://example.com/a.b:8080/true/1/3/pong/320k", result.getOrNull())
        } finally {
            UserApiEngine.unloadScript()
            // 测试动的是 App 的单例引擎；跑完把用户当前启用的插件恢复回去。
            runCatching { com.aesthetic.music.data.source.SourceManager.restoreActive() }
            server.close()
            serverThread.join(1_000)
        }
    }

    @Test
    fun realXinghaiScriptInitsInQuickJs() = runBlocking {
        val core = readCoreAsset()
        val testCtx = InstrumentationRegistry.getInstrumentation().context
        val script = testCtx.assets.open("xinghai-music-sourcev2.3.17.js")
            .bufferedReader().use { it.readText() }
        val runtime = LxRuntime(
            engineFactory = { HarlonQuickJsEngine() },
            coreJs = core,
            onLog = { Log.d("LxCoreTest", it) },
        )
        try {
            val info = JSONObject()
                .put("name", "星海音乐源")
                .put("version", "3.2.17")
                .put("author", "test")
                .put("env", "mobile")
            val init = runtime.load(script, info.toString(), 10_000)
            assertTrue("星海脚本 init 失败: ${init.message}", init.ready)

            val sources = init.sources
            assertTrue("星海脚本没有返回 sources", sources != null)
            if (sources == null) return@runBlocking
            for (source in listOf("kw", "kg", "tx", "wy", "mg")) {
                assertTrue("星海没有声明 $source", sources.has(source))
            }
            val kw = sources.getJSONObject("kw")
            assertEquals("music", kw.getString("type"))
            assertEquals("musicUrl", kw.getJSONArray("actions").getString(0))
            assertTrue("kw 没有可用音质", kw.getJSONArray("qualitys").length() > 0)
        } finally {
            runtime.close()
        }
    }

    private fun readCoreAsset(): String {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return ctx.assets.open("script/lx-core.js").bufferedReader().use { it.readText() }
    }

    private fun serve(server: ServerSocket) {
        while (!server.isClosed) {
            try {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    readLine(input) // request line
                    while (true) {
                        val line = readLine(input) ?: break
                        if (line.isEmpty()) break
                    }
                    val body = """{"echo":"pong"}"""
                    val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${body.toByteArray().size}\r\n" +
                        "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())
                    output.write(body.toByteArray())
                    output.flush()
                }
            } catch (_: Exception) {
                if (server.isClosed) return
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var b = input.read()
        if (b == -1) return null
        while (b != -1 && b != '\n'.code) {
            if (b != '\r'.code) sb.append(b.toChar())
            b = input.read()
        }
        return sb.toString()
    }

    companion object {
        private val SCRIPT = """
            const { EVENT_NAMES, on, send } = lx
            const u = new URL('https://a.b:8080/p?x=1')
            const c = new AbortController(); c.abort()
            const clone = structuredClone({ a: [1] })
            const te = new TextEncoder().encode('你').length
            on(EVENT_NAMES.request, ({ info }) => new Promise((resolve, reject) => {
              lx.request('http://127.0.0.1:__PORT__/echo', { timeout: 5000 }, (err, resp, body) => {
                if (err) return reject(err)
                resolve('https://example.com/' + u.host + '/' + c.signal.aborted + '/' + clone.a[0] + '/' + te + '/' + body.echo + '/' + info.type)
              })
            }))
            send(EVENT_NAMES.inited, {
              status: true,
              sources: { kw: { type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k'] } },
            })
        """.trimIndent()
    }
}
