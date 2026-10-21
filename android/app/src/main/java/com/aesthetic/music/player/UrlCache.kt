package com.aesthetic.music.player

import java.util.LinkedHashMap

/**
 * 已解析的播放直链缓存（**息屏连续播放的关键**）。
 *
 * ## 为什么需要它
 * 本 App 是「一次只往 ExoPlayer 里 set 一首歌」，每首歌的直链都要先调一次解析接口
 * （网易云/QQ/酷狗/酷我直连，或 QuickJS 音源插件）。切歌发生在 `onSongComplete` 里，
 * 也就是**屏幕通常是黑的时候**：
 *
 *  - 亮屏时，App 是前台进程，解析接口几秒内返回，用户感觉是"自动连播"；
 *  - 息屏后，App 进入后台。Doze / 各家 ROM 的省电策略会对后台网络限速甚至直接掐断，
 *    一次解析失败 → 跳下一首 → 又失败……连续 3 次就整条播放链停住。
 *    用户看到的现象正是「息屏放一两首就自动停了」。
 *
 * 预取下一首的**直链**（不是音频，只是一个几百字节的 JSON）几乎没有代价，
 * 却能把"切歌时必须联网"变成"切歌时通常不用联网"——这是让息屏连播稳住的核心。
 *
 * ## 为什么带 TTL
 * 直链是**带签名、会过期**的（各平台 10 分钟到数小时不等）。过期直链会让播放器
 * 403/404，比没有缓存更糟。所以这里给 15 分钟的保守有效期：足够覆盖一首歌的时长，
 * 又不会久到过期。命中失败时播放器会走 `onPlayerError` 重新解析。
 *
 * ## 为什么用 LRU 上限 40
 * 一条直链约 200~600 字节，40 条不过几十 KB；容量按"队列长度"给的，
 * 不会因为用户塞了 500 首的队列就无限涨。
 */
object UrlCache {

    /** 直链有效期（保守值：一首歌最长也不会超过它） */
    private const val TTL_MS = 15 * 60_000L

    /** 最多缓存多少条 */
    private const val MAX_ENTRIES = 40

    private data class Entry(val url: String, val atMs: Long)

    /** accessOrder = true：按访问顺序淘汰，最近用过的留下 */
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

    /** 仅供调试/单测：当前缓存条数 */
    @Synchronized
    fun size(): Int = entries.size
}
