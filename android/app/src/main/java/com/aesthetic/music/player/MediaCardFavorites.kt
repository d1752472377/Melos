package com.aesthetic.music.player

import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.FavoriteStore

object MediaCardFavorites {

    @Volatile
    private var reader: ((Song) -> Boolean)? = null

    @Volatile
    private var toggler: ((Song) -> Boolean)? = null

    fun register(read: (Song) -> Boolean, toggle: (Song) -> Boolean) {
        reader = read
        toggler = toggle
    }

    fun isFavorite(song: Song?): Boolean {
        if (song == null) return false
        reader?.let { return runCatching { it(song) }.getOrDefault(false) }
        return runCatching { FavoriteStore.load().any { sameSong(it, song) } }.getOrDefault(false)
    }

    fun toggle(song: Song?): Boolean {
        if (song == null) return false
        toggler?.let { return runCatching { it(song) }.getOrDefault(false) }
        return runCatching {
            val list = FavoriteStore.load().toMutableList()
            val idx = list.indexOfFirst { sameSong(it, song) }
            val nowFavorite: Boolean
            if (idx >= 0) {
                list.removeAt(idx)
                nowFavorite = false
            } else {
                list.add(0, song)
                nowFavorite = true
            }
            FavoriteStore.save(list)
            nowFavorite
        }.getOrDefault(false)
    }

    private fun sameSong(a: Song, b: Song): Boolean =
        a.id == b.id && (a.platform == b.platform || a.platform.isNullOrBlank() || b.platform.isNullOrBlank())
}
