package com.aesthetic.music.player

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 缓存歌曲的"副文件"：专辑封面与歌词。
 *
 * 对应设置项「下载与缓存 → 封面与歌词」。
 * - 封面：与音频同目录、同基础名的 `.jpg`（`<基础名>.jpg`，基础名 = 音频文件名去掉扩展名）；
 * - 歌词：由 [LocalLyricStore.saveLocalLyric] 写入 filesDir/lyrics（同时会尝试写同目录 .lrc）。
 *
 * 单独放一个对象的理由：AudioCacheService 只管音频字节流（它是纯下载器，没有仓库/歌词解析），
 * 封面与歌词是"带业务语义"的补齐动作，放在这里调用方（PlayerManager / 手动下载）更清楚。
 */
object SongSidecarStore {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** 封面文件位置（与音频同目录同基础名） */
    fun coverFileFor(audioPath: String): File {
        val audio = File(audioPath)
        val stem = audio.name.substringBeforeLast('.')
        return File(audio.parentFile ?: audio, "$stem.jpg")
    }

    /**
     * 下载并保存封面。已有封面或没有封面地址时直接跳过。
     * IO 阻塞调用，请在 IO 线程/协程里用。
     */
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
