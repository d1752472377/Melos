package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.service.LogReporter
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.concurrent.Executors

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
    } catch (e: Exception) {
        LogReporter.w("PrefsJson", "读取失败 key=$key", e)
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
            } catch (e: Exception) {
                LogReporter.w("PrefsJson", "保存失败 key=$key", e)
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
            } catch (e: Exception) {
                LogReporter.w("PrefsJson", "清除失败 key=$key", e)
            }
        }
    }

    fun runAsync(block: () -> Unit) {
        writer.execute(block)
    }
}

internal inline fun <reified T> jsonType(): Type = object : TypeToken<T>() {}.type
