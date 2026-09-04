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

    /** 默认本地回环占位地址（Retrofit 初始化需要合法 URL，运行时由拦截器动态重定向至用户配置地址） */
    const val DEFAULT_PLACEHOLDER_URL = "http://127.0.0.1:3000/"

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

                // 1. 动态重定向至用户配置的私有云服务器
                val configuredServerUrl = SyncServerStore.getServerUrl()
                if (configuredServerUrl.isNotBlank()) {
                    val targetHttpUrl = configuredServerUrl.toHttpUrlOrNull()
                    if (targetHttpUrl != null) {
                        val newUrl = request.url.newBuilder()
                            .scheme(targetHttpUrl.scheme)
                            .host(targetHttpUrl.host)
                            .port(targetHttpUrl.port)
                            .build()
                        request = request.newBuilder().url(newUrl).build()
                    }
                } else {
                    // 若未配置私有服务器且试图请求后端云接口，直接拦截并提示
                    val path = request.url.encodedPath
                    if (path.startsWith("/api/sync") || path.startsWith("/api/scripts")) {
                        throw IOException("未配置私有服务器地址，请先在「设置」中填入后端地址与密钥")
                    }
                }

                // 2. 自动附加私有云同步密钥
                val syncToken = SyncServerStore.getSyncToken()
                val reqBuilder = request.newBuilder()
                if (syncToken.isNotBlank()) {
                    reqBuilder.header("X-Sync-Token", syncToken)
                    reqBuilder.header("Authorization", "Bearer $syncToken")
                } else {
                    val userToken = UserStore.token()
                    if (userToken != null) {
                        reqBuilder.header("Authorization", "Bearer $userToken")
                    }
                }
                val newRequest = reqBuilder.build()

                val response = chain.proceed(newRequest)

                if (response.code == 401 && SyncServerStore.isConfigured()) {
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
