package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences

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

    fun save(words: List<String>) =
        PrefsJson.save(prefs, KEY_WORDS, words.take(MAX_ITEMS), namespaced = false)

    fun clear() = PrefsJson.clear(prefs, KEY_WORDS, namespaced = false)
}
