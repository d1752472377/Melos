package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.model.Song

object HistoryStore {

    private const val PREFS = "aesthetic_music_history"
    private const val KEY_SONGS = "history_songs"
    private const val MAX_ITEMS = 100

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = PrefsJson.prefs(context, PREFS)
    }

    fun load(): List<Song> =
        PrefsJson.load(prefs, KEY_SONGS, jsonType<List<Song>>(), namespaced = true) ?: emptyList()

    fun save(songs: List<Song>) =
        PrefsJson.save(prefs, KEY_SONGS, songs.take(MAX_ITEMS), namespaced = true)

    fun clear() = PrefsJson.clear(prefs, KEY_SONGS, namespaced = true)
}
