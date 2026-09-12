package com.aesthetic.music.player

import android.content.Context
import com.aesthetic.music.data.model.Song
import java.io.File

/**
 * 本地歌词的读写（离线看词）。
 *
 * ## 为什么现在只有歌词
 * 这个文件原名 `MediaCardFavorites.kt`：媒体卡片上有"收藏 ♡"按钮时，卡片由后台服务构建、
 * 收藏状态却由随 Activity 的 ViewModel 持有，于是这里放了一组"服务 ↔ UI"的回调桥，
 * 让卡片上的收藏与 App 内的 ♡ 同步。
 *
 * 本次改动删掉了卡片上的收藏按钮（实测卡片控制整体不可用，只保留官方支持的播放控制），
 * 这个桥就没有任何调用方了。**死代码留着比删掉更危险**：下一个人会以为"卡片收藏"还活着。
 * 因此收藏相关的 `register` / `isFavorite` / `toggle` 一并删除；
 * 歌词副文件读写是真实在用的功能，保留并独立成 `LocalLyricStore`。
 *
 * 如果以后重新加卡片收藏按钮，**不要**直接恢复旧实现（自家广播已被证明不好用）：
 * 应该走 media3 的自定义命令（`MediaSession.Callback.onCustomCommand` +
 * `MediaNotification.ActionFactory.createCustomAction`），让命令沿会话通道派发。
 */
object LocalLyricStore {

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
