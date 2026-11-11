package com.aesthetic.music.data.recognition

import android.util.Log
import com.aesthetic.music.data.AppJson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 识别结果里的一首歌。只保留展示与跳转搜索需要的字段，不携带任何播放地址。 */
data class RecognitionSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
)

/**
 * 听歌识曲（酷狗短音频指纹）。
 *
 * 流程：录音 → 降采样到 8 kHz 单声道 16bit PCM → POST 到酷狗听音网关 → 拿回歌曲列表。
 * 整个请求是匿名的，不带任何账号、Cookie 或设备标识。
 *
 * 接口来自酷狗客户端，非公开接口。识别不到分两种情况：服务正常但没匹配（返回空列表），
 * 和服务异常（抛异常），调用方要分辨显示。
 */
object RecognitionService {

    private const val TAG = "RecognitionService"

    /** 酷狗网关要求的音频长度下限（字节，即 3 秒 8 kHz 单声道 16bit）。 */
    const val MIN_PCM_BYTES = 48_000

    /** 上限：8 秒（16 kHz 采样率下也够用），对应 160000 字节。 */
    const val MAX_PCM_BYTES = 160_000

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val gson = AppJson.gson
    private val OCTET_STREAM = "application/octet-stream".toMediaType()

    /**
     * 上传一段 8 kHz 单声道 16bit 小端 PCM，返回匹配到的歌曲（最多 5 条）。
     *
     * @throws IOException 服务异常或参数不合法
     */
    suspend fun match(pcm: ByteArray): List<RecognitionSong> = withContext(Dispatchers.IO) {
        require(pcm.size in MIN_PCM_BYTES..MAX_PCM_BYTES && pcm.size % 2 == 0) {
            "音频长度不符合识别要求"
        }

        val params = linkedMapOf(
            "appid" to "1005",
            "area_code" to "1",
            "clienttime" to (System.currentTimeMillis() / 1000).toString(),
            "clientver" to "20489",
            "dfid" to "-",
            "fpid" to System.currentTimeMillis().toString(),
            "include_unpublish" to "1",
            "mid" to UUID.randomUUID().toString().replace("-", ""),
            "multi_result" to "1",
            "useid" to "0",
        )
        val ordered = params.entries.sortedBy { it.key }.joinToString("") { "${it.key}=${it.value}" }
        val query = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        val signature = sign(ordered, pcm)

        val request = Request.Builder()
            .url("https://gateway.kugou.com/fingerprint.service/v1/music_trackid_mulit?$query&signature=$signature")
            .header("Content-Type", "application/octet-stream")
            .header("User-Agent", "KuGou/11490 (Android)")
            .post(pcm.toRequestBody(OCTET_STREAM))
            .build()

        val body = httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("识别服务返回 HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        parse(body)
    }

    /** 签名 = md5(盐 + 按键名排序后的参数串 + PCM 原始字节 + 盐)。 */
    private fun sign(orderedParams: String, pcm: ByteArray): String {
        val salt = "OIlwieks28dk2k092lksi2UIkp".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("MD5")
        digest.update(salt)
        digest.update(orderedParams.toByteArray(Charsets.UTF_8))
        digest.update(pcm)
        digest.update(salt)
        return digest.digest().joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    private fun parse(body: String): List<RecognitionSong> {
        val root = try {
            gson.fromJson(body, JsonObject::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "识别响应解析失败: ${e.message}")
            null
        } ?: throw IOException("识别服务返回了无法解析的内容")

        // status=1 / error_code=0 才是正常应答；data 为空对象表示"没匹配到"。
        val status = root.get("status")?.asInt ?: 0
        val errorCode = root.get("error_code")?.asInt ?: 0
        if (status != 1 || errorCode != 0) throw IOException("识别服务返回异常")

        // 未匹配时 data 可能是空对象 `{}`，不能直接当数组用。
        val data = root.get("data")
        if (data == null || !data.isJsonArray) return emptyList()
        val arr = data.asJsonArray
        val seen = mutableSetOf<String>()
        val out = mutableListOf<RecognitionSong>()
        for (element in arr) {
            val o = element.asJsonObject ?: continue
            val id = (o.get("mixsongid")?.asString ?: o.get("songid")?.asString)?.takeIf {
                it.isNotBlank() && it.all(Char::isDigit) && it != "0"
            } ?: continue
            if (!seen.add(id)) continue
            val title = o.get("songname")?.asString?.trim().orEmpty()
            if (title.isBlank()) continue
            val artist = o.get("singername")?.asString?.trim().orEmpty()
            val album = o.getAsJsonArray("album")
                ?.firstOrNull()?.asJsonObject
                ?.get("albumname")?.asString?.trim().orEmpty()
            out += RecognitionSong(
                id = id,
                title = title.take(120),
                artist = artist.take(120),
                album = album.take(120),
            )
            if (out.size >= 5) break
        }
        return out
    }
}
