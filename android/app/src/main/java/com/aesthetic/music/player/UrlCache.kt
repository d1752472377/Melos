package com.aesthetic.music.player

import java.util.LinkedHashMap

object UrlCache {

    private const val TTL_MS = 15 * 60_000L

    private const val MAX_ENTRIES = 40

    private data class Entry(val url: String, val atMs: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    /** 缓存键必须带音质：同一首歌不同音质是不同直链 */
    fun key(platform: String, songId: String, quality: String): String =
        "${platform.trim().lowercase()}:${songId.trim()}:$quality"

    @Synchronized
    fun get(key: String): String? {
        val e = entries[key] ?: return null
        if (System.currentTimeMillis() - e.atMs > TTL_MS) {
            entries.remove(key)
            return null
        }
        return e.url
    }

    @Synchronized
    fun put(key: String, url: String) {
        if (url.isBlank()) return
        entries[key] = Entry(url, System.currentTimeMillis())
    }

    @Synchronized
    fun remove(key: String) {
        entries.remove(key)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}
