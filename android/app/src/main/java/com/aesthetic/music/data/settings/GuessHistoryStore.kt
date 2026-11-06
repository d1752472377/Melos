package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences

object GuessHistoryStore {

    private const val PREFS = "aesthetic_music_guess"
    private const val KEY_RECOMMENDED = "recommended_keys"
    private const val MAX_KEYS = 300

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = PrefsJson.prefs(context, PREFS)
    }

    fun load(): Set<String> {
        val list = PrefsJson.load<List<String>>(
            prefs,
            KEY_RECOMMENDED,
            jsonType<List<String>>(),
            namespaced = false,
        ).orEmpty()
        return list.filter { it.isNotBlank() }.toSet()
    }

    fun record(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val merged = LinkedHashSet(load())
        merged.addAll(keys.filter { it.isNotBlank() })
        val trimmed = if (merged.size > MAX_KEYS) {
            merged.toList().takeLast(MAX_KEYS)
        } else {
            merged.toList()
        }
        PrefsJson.save(prefs, KEY_RECOMMENDED, trimmed, namespaced = false)
    }

    fun clear() = PrefsJson.clear(prefs, KEY_RECOMMENDED, namespaced = false)
}
