package com.aesthetic.music.player

import android.content.Context
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.FavoriteStore
import java.io.File

/**
 * 媒体卡片与 UI 之间的桥。
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

    // ==================== 本地歌词（下载与缓存 → 封面与歌词 / 歌词来源） ====================

    /**
     * 本地歌词目录：`filesDir/lyrics/<platform>_<id>.lrc`。
     * 与音频缓存文件同生命周期地保存歌词副文件，离线也能看词。
     */
    fun lyricFile(context: Context, song: Song): File {
        val key = buildString {
            append(song.platform.ifBlank { "local" })
            append('_')
            append(song.id.ifBlank { song.title })
        }.replace(Regex("[^A-Za-z0-9_.\\-]"), "_")
        val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
        return File(dir, "$key.lrc")
    }

    /** 写入本地歌词（内容为空则忽略） */
    fun saveLocalLyric(context: Context, song: Song, lrc: String?) {
        if (lrc.isNullOrBlank()) return
        runCatching { lyricFile(context, song).writeText(lrc) }
    }

    /**
     * 读取本地歌词：
     * 1. 与缓存音频同目录同名的 `.lrc` 副文件（用户自己放进来的也能生效）
     * 2. App 保存的歌词目录
     */
    fun loadLocalLyric(context: Context, song: Song, audioPath: String? = null): String? {
        if (!audioPath.isNullOrBlank()) {
            val sidecar = File(audioPath.substringBeforeLast('.') + ".lrc")
            if (sidecar.exists()) {
                return runCatching { sidecar.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        }
        val f = lyricFile(context, song)
        return if (f.exists()) runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() } else null
    }
}
