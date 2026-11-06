package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.model.Song

object FavoriteStore {

    private const val PREFS = "aesthetic_music_favorites"
    private const val KEY_SONGS = "favorite_songs"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = PrefsJson.prefs(context, PREFS)
    }

    fun load(): List<Song> =
        PrefsJson.load(prefs, KEY_SONGS, jsonType<List<Song>>(), namespaced = true) ?: emptyList()

    fun save(songs: List<Song>) =
        PrefsJson.save(prefs, KEY_SONGS, songs, namespaced = true)

    fun clear() = PrefsJson.clear(prefs, KEY_SONGS, namespaced = true)
}
