package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.concurrent.Executors

/**
 * SharedPreferences + Gson 的公共读写函数。
 *
 * 只抽“取 prefs / 读 JSON / 异步写 JSON / 清键”这几件事；
 * 各 Store 仍保留自己的键、容量与数据语义，不引入泛型 Store 类。
 */
internal object PrefsJson {

    /** 全局单线程写入器：序列化与落盘不占用调用线程，且保证提交顺序 */
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "prefs-json").apply { isDaemon = true }
    }

    fun prefs(context: Context, name: String): SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun <T> load(
        prefs: SharedPreferences?,
        key: String,
        type: Type,
        namespaced: Boolean,
    ): T? = try {
        val raw = if (namespaced) {
            LibraryNamespace.read(prefs, key)
        } else {
            prefs?.getString(key, null)
        }
        raw?.let { AppJson.gson.fromJson<T>(it, type) }
    } catch (_: Exception) {
        null
    }

    fun save(
        prefs: SharedPreferences?,
        key: String,
        value: Any,
        namespaced: Boolean,
        async: Boolean = true,
    ) {
        val block: () -> Unit = {
            try {
                val json = AppJson.gson.toJson(value)
                if (namespaced) {
                    LibraryNamespace.write(prefs, key, json)
                } else {
                    prefs?.edit()?.putString(key, json)?.apply()
                }
            } catch (_: Exception) {
                // 本地持久化失败不应打断播放/界面
            }
            Unit
        }
        if (async) writer.execute(block) else block()
    }

    fun clear(prefs: SharedPreferences?, key: String, namespaced: Boolean) {
        if (namespaced) {
            LibraryNamespace.clear(prefs, key)
        } else {
            try {
                prefs?.edit()?.remove(key)?.apply()
            } catch (_: Exception) {
            }
        }
    }

    fun runAsync(block: () -> Unit) {
        writer.execute(block)
    }
}

/** Gson 的 TypeToken 便捷入口：jsonType<List<Song>>() */
internal inline fun <reified T> jsonType(): Type = object : TypeToken<T>() {}.type
