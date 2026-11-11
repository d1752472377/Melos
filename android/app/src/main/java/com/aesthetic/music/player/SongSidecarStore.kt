package com.aesthetic.music.player

import android.content.Context
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.service.LogReporter
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object SongSidecarStore {

    private const val TAG = "SongSidecarStore"

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

    /**
     * 把封面存到音频文件旁边（`<同名>.jpg`）。
     *
     * 这里是阻塞的 OkHttp 请求 + 文件写入，必须跑在 IO 线程：之前直接在
     * Dispatchers.Main 的协程里调用，OkHttp 建连时会抛
     * `NetworkOnMainThreadException`——它的 message 恰好是 null，日志里只剩
     * “封面保存失败: null”，看不出任何原因，而且每次下载完封面都失败。
     */
    suspend fun saveCover(context: Context, song: com.aesthetic.music.data.model.Song): File? =
        withContext(Dispatchers.IO) {
            val url = song.artwork
            if (url.isNullOrBlank()) return@withContext null

            val audioPath = try {
                AudioCacheService.getCachedPath(
                    context,
                    song.platform,
                    song.id,
                    SettingsStore.getQuality().value,
                )
            } catch (e: Exception) {
                LogReporter.w(TAG, "取音频缓存路径失败 platform=${song.platform} id=${song.id}", e)
                null
            } ?: return@withContext null

            val target = coverFileFor(audioPath)
            if (target.exists() && target.length() > 0) return@withContext target

            // 音频文件所在目录理论上一定存在；拿不到目录时不要拿 null 去构造 File（会 NPE）
            val dir = target.parentFile
            if (dir == null || (!dir.exists() && !dir.mkdirs())) {
                LogReporter.w(TAG, "封面目录不可用 audio=$audioPath dir=${dir?.absolutePath}")
                return@withContext null
            }

            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        LogReporter.w(TAG, "封面下载失败 HTTP ${resp.code} url=$url")
                        return@withContext null
                    }
                    val body = resp.body ?: run {
                        LogReporter.w(TAG, "封面响应体为空 url=$url")
                        return@withContext null
                    }
                    val tmp = File(dir, target.name + ".tmp")
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (tmp.length() <= 0L) {
                        tmp.delete()
                        LogReporter.w(TAG, "封面下载内容为空 url=$url")
                        return@withContext null
                    }
                    if (target.exists()) target.delete()
                    if (tmp.renameTo(target)) {
                        target
                    } else {
                        tmp.delete()
                        LogReporter.w(TAG, "封面文件重命名失败 target=${target.absolutePath}")
                        null
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 带上完整堆栈：NetworkOnMainThreadException 这类异常的 message 是 null
                LogReporter.w(TAG, "封面保存失败 url=$url target=${target.absolutePath}", e)
                null
            }
        }
}
