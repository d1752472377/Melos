package com.aesthetic.music.player

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

object SongSidecarStore {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun coverFileFor(audioPath: String): File {
        val audio = File(audioPath)
        val stem = audio.name.substringBeforeLast('.')
        return File(audio.parentFile ?: audio, "$stem.jpg")
    }

    fun saveCover(context: Context, song: com.aesthetic.music.data.model.Song): File? {
        val url = song.artwork
        if (url.isNullOrBlank()) return null
        return try {
            val quality = com.aesthetic.music.data.settings.SettingsStore.getQuality().value
            val audioPath = AudioCacheService.getCachedPath(context, song.platform, song.id, quality)
                ?: return null
            val target = coverFileFor(audioPath)
            if (target.exists() && target.length() > 0) return target
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                val tmp = File(target.parentFile, target.name + ".tmp")
                body.byteStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (tmp.length() <= 0) {
                    tmp.delete()
                    return null
                }
                if (target.exists()) target.delete()
                if (tmp.renameTo(target)) target else null
            }
        } catch (e: Exception) {
            Log.d("SongSidecarStore", "封面保存失败: ${e.message}")
            null
        }
    }
}
