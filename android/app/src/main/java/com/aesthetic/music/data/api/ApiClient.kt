package com.aesthetic.music.data.api

import android.os.Handler
import android.os.Looper
import com.aesthetic.music.BuildConfig
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.data.settings.UserStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    const val DEFAULT_PLACEHOLDER_URL = "http://127.0.0.1:8787/"

    @Volatile
    private var onAuthFailed: (() -> Unit)? = null

    fun setOnAuthFailed(callback: (() -> Unit)?) {
        onAuthFailed = callback
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                var request = chain.request()

                // 1. 动态重定向至用户配置的私有云服务器（保留子路径前缀，兼容反向代理与多级路由）
                val configuredServerUrl = SyncServerStore.getServerUrl()
                if (configuredServerUrl.isNotBlank()) {
                    val targetHttpUrl = configuredServerUrl.toHttpUrlOrNull()
                    if (targetHttpUrl != null) {
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
                        request = request.newBuilder().url(newUrl).build()
                    }
                } else {
                    val path = request.url.encodedPath
                    if (path.startsWith("/api/sync") || path.startsWith("/api/auth") || path.startsWith("/api/me") || path.startsWith("/api/app")) {
                        throw IOException("未配置服务器地址，请先在「账号与同步」中填写后端地址")
                    }
                }

                val token = UserStore.token().orEmpty()

                val reqBuilder = request.newBuilder()
                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                val newRequest = reqBuilder.build()

                val response = chain.proceed(newRequest)

                if (response.code == 401 && token.isNotBlank() && SyncServerStore.isConfigured()) {
                    Handler(Looper.getMainLooper()).post { onAuthFailed?.invoke() }
                }

                response
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()
    }

    val api: MusicApi by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_PLACEHOLDER_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(AppJson.gson))
            .build()
            .create(MusicApi::class.java)
    }
}
