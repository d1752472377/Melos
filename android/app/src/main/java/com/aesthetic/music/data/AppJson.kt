package com.aesthetic.music.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * 全 App 共享的 Gson 单例：Retrofit 转换器与各本地存储（收藏/历史/歌单/会话/统计/用户）
 * 统一用它，保证序列化行为一致（此前每处各建 Gson，lenient 与否、日期格式都可能漂移）。
 *
 * 注：Gson 本身没有 Kotlin 感知能力（KotlinJsonAdapterFactory 是 Moshi 的 API，
 * 之前误引导致编译失败）。当前依赖树下无 moshi-kotlin，为不引入新依赖，
 * 空安全暂时依赖"后端字段齐全 + 本地存储写入即读"的事实约束；
 * 后续如需彻底根治 Kotlin 数据类默认值被 null 击穿的问题，应迁移到 Moshi。
 */
object AppJson {
    val gson: Gson = GsonBuilder()
        .setLenient()
        .create()
}
