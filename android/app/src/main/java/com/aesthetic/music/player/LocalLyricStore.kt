package com.aesthetic.music.player

import android.content.Context
import com.aesthetic.music.data.model.Song
import java.io.File

object LocalLyricStore {

    fun lyricFile(context: Context, song: Song): File {
        val key = buildString {
            append(song.platform.ifBlank { "local" })
            append('_')
            append(song.id.ifBlank { song.title })
        }.replace(Regex("[^A-Za-z0-9_.\\-]"), "_")
        val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
        return File(dir, "$key.lrc")
    }

    fun saveLocalLyric(context: Context, song: Song, lrc: String?) {
        if (lrc.isNullOrBlank()) return
        runCatching { lyricFile(context, song).writeText(lrc) }
    }

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
