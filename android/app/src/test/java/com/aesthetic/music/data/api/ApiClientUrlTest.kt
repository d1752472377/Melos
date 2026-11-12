package com.aesthetic.music.data.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证 ApiClient 拦截器中的 URL 重定向与子路径拼接算法。
 */
class ApiClientUrlTest {

    private fun rewriteUrl(placeholderUrl: String, configuredServerUrl: String): String {
        val request = Request.Builder().url(placeholderUrl).build()
        val targetHttpUrl = configuredServerUrl.toHttpUrl()
        val serverBasePath = targetHttpUrl.encodedPath.trimEnd('/')
        val reqPath = request.url.encodedPath
        val finalPath = if (serverBasePath.isNotEmpty() && serverBasePath != "/") {
            serverBasePath + if (reqPath.startsWith("/")) reqPath else "/$reqPath"
        } else {
            reqPath
        }
        val newUrl = request.url.newBuilder()
            .scheme(targetHttpUrl.scheme)
            .host(targetHttpUrl.host)
            .port(targetHttpUrl.port)
            .encodedPath(finalPath)
            .build()
        return newUrl.toString()
    }

    @Test
    fun `常规无子路径服务器正确重定向`() {
        val result = rewriteUrl(
            placeholderUrl = "http://127.0.0.1:8787/api/sync/pull?since=10",
            configuredServerUrl = "http://music.example.com"
        )
        assertEquals("http://music.example.com/api/sync/pull?since=10", result)
    }

    @Test
    fun `带端口的服务器正确重定向`() {
        val result = rewriteUrl(
            placeholderUrl = "http://127.0.0.1:8787/api/health",
            configuredServerUrl = "http://192.168.1.100:8787"
        )
        assertEquals("http://192.168.1.100:8787/api/health", result)
    }

    @Test
    fun `单级子路径反代服务器正确保留前缀`() {
        val result = rewriteUrl(
            placeholderUrl = "http://127.0.0.1:8787/api/sync/pull",
            configuredServerUrl = "http://nas.lan:8080/melos"
        )
        assertEquals("http://nas.lan:8080/melos/api/sync/pull", result)
    }

    @Test
    fun `多级子路径且带尾部斜杠正确保留前缀`() {
        val result = rewriteUrl(
            placeholderUrl = "http://127.0.0.1:8787/api/app/version/latest",
            configuredServerUrl = "https://example.com/proxy/subpath/"
        )
        assertEquals("https://example.com/proxy/subpath/api/app/version/latest", result)
    }
}
