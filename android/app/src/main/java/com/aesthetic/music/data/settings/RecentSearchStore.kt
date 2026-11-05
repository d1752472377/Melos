package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * 搜索页「最近搜索」持久化：SharedPreferences 存 JSON 字符串数组。
 * 最近搜索是设备级数据，不跟随账号命名空间。
 */
object RecentSearchStore {

    private const val PREFS = "aesthetic_music_search"
    private const val KEY_WORDS = "recent_keywords"
    private const val MAX_ITEMS = 10

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = PrefsJson.prefs(context, PREFS)
    }

    fun load(): List<String> =
        PrefsJson.load(prefs, KEY_WORDS, jsonType<List<String>>(), namespaced = false) ?: emptyList()

    /** 保存完整列表（调用方负责去重/置顶/裁剪，这里只落盘前 MAX 条） */
    fun save(words: List<String>) =
        PrefsJson.save(prefs, KEY_WORDS, words.take(MAX_ITEMS), namespaced = false)

    fun clear() = PrefsJson.clear(prefs, KEY_WORDS, namespaced = false)
}
