package com.aesthetic.music.player

import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.FavoriteStore

/**
 * 媒体卡片与 UI 之间的收藏状态桥梁。
 *
 * 通知栏媒体卡片由 [com.aesthetic.music.service.PlaybackService]（后台服务）构建，
 * 而收藏状态由 `LibraryController`（随 Activity 的 ViewModel）持有——两者不在同一层，
 * 因此这里放一组可注册的回调：
 * - App 在前台/后台存活（ViewModel 已创建）时，走内存态，收藏后 UI 立刻同步；
 * - ViewModel 已销毁（只剩服务在跑）时回落到 [FavoriteStore] 直接读写，
 *   下次 ViewModel 创建时 `hydrate()` 会从同一份存储回填，状态仍然一致。
 */
object MediaCardFavorites {

    @Volatile
    private var reader: ((Song) -> Boolean)? = null

    @Volatile
    private var toggler: ((Song) -> Boolean)? = null

    /** 由 MainViewModel 注册；返回切换后的收藏状态 */
    fun register(read: (Song) -> Boolean, toggle: (Song) -> Boolean) {
        reader = read
        toggler = toggle
    }

    fun isFavorite(song: Song?): Boolean {
        if (song == null) return false
        reader?.let { return runCatching { it(song) }.getOrDefault(false) }
        return runCatching { FavoriteStore.load().any { sameSong(it, song) } }.getOrDefault(false)
    }

    /** 切换收藏，返回切换后的状态（失败返回原状态，不抛异常打断通知栏操作） */
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
