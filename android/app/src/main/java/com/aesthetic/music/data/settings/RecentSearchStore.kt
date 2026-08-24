package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 搜索页"最近搜索"持久化：SharedPreferences 存 JSON 字符串数组。
 * 与其它 Store 一致：单线程后台写入器，序列化与落盘移出主线程。
 */
object RecentSearchStore {

    private const val PREFS = "aesthetic_music_search"
    private const val KEY_WORDS = "recent_keywords"
    private const val MAX_ITEMS = 10

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun load(): List<String> = try {
        val raw = prefs?.getString(KEY_WORDS, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        gson.fromJson<List<String>>(raw, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** 保存完整列表（调用方负责去重/置顶/裁剪，这里只落盘前 MAX 条） */
    fun save(words: List<String>) {
        val snapshot = words.take(MAX_ITEMS)
        writer.execute {
            try {
                prefs?.edit()?.putString(KEY_WORDS, gson.toJson(snapshot))?.apply()
            } catch (_: Exception) {
            }
        }
    }

    fun clear() {
        try {
            prefs?.edit()?.remove(KEY_WORDS)?.apply()
        } catch (_: Exception) {
        }
    }
}
