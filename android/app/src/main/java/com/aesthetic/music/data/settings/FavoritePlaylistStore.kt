package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.api.PlaylistItem

object FavoritePlaylistStore {

    private const val PREFS = "aesthetic_music_fav_playlists"
    private const val KEY_PLAYLISTS = "favorite_playlists"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = PrefsJson.prefs(context, PREFS)
    }

    fun load(): List<PlaylistItem> =
        PrefsJson.load(prefs, KEY_PLAYLISTS, jsonType<List<PlaylistItem>>(), namespaced = true)
            ?: emptyList()

    fun save(playlists: List<PlaylistItem>) =
        PrefsJson.save(prefs, KEY_PLAYLISTS, playlists, namespaced = true)

    fun clear() = PrefsJson.clear(prefs, KEY_PLAYLISTS, namespaced = true)
}
