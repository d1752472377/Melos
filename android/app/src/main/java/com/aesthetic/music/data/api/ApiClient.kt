package com.aesthetic.music.data.api

import com.aesthetic.music.data.settings.UserStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    /**
     * 后端地址：Vercel Serverless（函数在 hkg1，数据库 Turso 云库）。
     * 自定义域名 music.dovislab.com 用于绕开 *.vercel.app 在大陆的 DNS 污染。
     */
    const val BASE_URL = "https://music.dovislab.com/"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                // 用户接口自动附带登录态；其他接口无感
                val token = UserStore.token()
                val newRequest = if (token != null && request.url.encodedPath.startsWith("/api/user")) {
                    request.newBuilder().header("Authorization", "Bearer $token").build()
                } else {
                    request
                }
                chain.proceed(newRequest)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    val api: MusicApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicApi::class.java)
    }
}
