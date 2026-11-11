package com.aesthetic.music.data.recognition

import android.content.Context
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.service.LogReporter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 一次识曲记录。
 *
 * [audioName] 是识别时录下的那段 8 kHz WAV 文件名（在 files/recognition 下），
 * 可以在识别历史里重放，也可以拿来做"重新识别"。音频只存在本机，不上传第二份。
 */
data class RecognitionRecord(
    val id: Long,
    val createdAt: Long,
    val durationMs: Long,
    val songs: List<RecognitionSong> = emptyList(),
    val audioName: String? = null,
    /** 这轮识别的来源，目前只有"听歌识曲"；保留字段便于以后区分 */
    val source: String = "listen",
) {
    val recognized: Boolean get() = songs.isNotEmpty()
}

/**
 * 识曲历史的本地存储：索引一个 JSON，录音是同一目录下的 WAV。
 *
 * 最多保留 [MAX_RECORDS] 条，超出时删掉最旧的记录和它的录音文件。
 */
object RecognitionStore {

    private const val TAG = "RecognitionStore"
    private const val DIR_NAME = "recognition"
    private const val INDEX_NAME = "history.json"
    private const val MAX_RECORDS = 100
    private const val SAMPLE_RATE = 8_000

    private val _records = MutableStateFlow<List<RecognitionRecord>>(emptyList())

    /** 最新在前 */
    val records: StateFlow<List<RecognitionRecord>> = _records.asStateFlow()

    private val lock = Any()
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            loaded = true
            _records.value = readIndex(context.applicationContext)
        }
    }

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun readIndex(context: Context): List<RecognitionRecord> = runCatching {
        val file = File(dir(context), INDEX_NAME)
        if (!file.exists()) return@runCatching emptyList()
        val arr = AppJson.gson.fromJson(file.readText(), Array<RecognitionRecord>::class.java)
        arr?.toList().orEmpty().sortedByDescending { it.createdAt }
    }.getOrElse { e ->
        LogReporter.w(TAG, "读取识曲历史失败", e)
        emptyList()
    }

    private fun persist(context: Context, list: List<RecognitionRecord>) {
        runCatching {
            File(dir(context), INDEX_NAME).writeText(AppJson.gson.toJson(list))
        }.onFailure { LogReporter.w(TAG, "写入识曲历史失败", it) }
    }

    /** 存一条记录并把录音落盘；录音写失败不影响识别结果，只是不能回放 */
    suspend fun add(
        context: Context,
        durationMs: Long,
        pcm8k: ByteArray,
        songs: List<RecognitionSong>,
    ): RecognitionRecord = withContext(Dispatchers.IO) {
        init(context)
        val ctx = context.applicationContext
        val now = System.currentTimeMillis()
        val audioName = "rec_$now.wav"
        val audioFile = File(dir(ctx), audioName)
        val savedAudio = runCatching {
            audioFile.writeBytes(toWav(pcm8k))
        }.onFailure {
            LogReporter.w(TAG, "写入识曲录音失败", it)
        }.isSuccess

        val record = RecognitionRecord(
            id = now,
            createdAt = now,
            durationMs = durationMs,
            songs = songs,
            audioName = if (savedAudio) audioName else null,
        )
        synchronized(lock) {
            val next = (listOf(record) + _records.value).take(MAX_RECORDS)
            _records.value = next
            persist(ctx, next)
            pruneAudio(ctx, next)
        }
        record
    }

    /** 用已有录音重新识别，结果写回同一条记录 */
    suspend fun updateSongs(context: Context, id: Long, songs: List<RecognitionSong>) {
        withContext(Dispatchers.IO) {
            init(context)
            val ctx = context.applicationContext
            synchronized(lock) {
                val next = _records.value.map { if (it.id == id) it.copy(songs = songs) else it }
                _records.value = next
                persist(ctx, next)
            }
        }
    }

    suspend fun delete(context: Context, record: RecognitionRecord) {
        withContext(Dispatchers.IO) {
            init(context)
            val ctx = context.applicationContext
            synchronized(lock) {
                val next = _records.value.filterNot { it.id == record.id }
                _records.value = next
                persist(ctx, next)
                record.audioName?.let { runCatching { File(dir(ctx), it).delete() } }
            }
        }
    }

    suspend fun clear(context: Context) {
        withContext(Dispatchers.IO) {
            init(context)
            val ctx = context.applicationContext
            synchronized(lock) {
                _records.value = emptyList()
                persist(ctx, emptyList())
                runCatching { dir(ctx).listFiles()?.forEach { it.delete() } }
            }
        }
    }

    fun audioFile(context: Context, record: RecognitionRecord): File? {
        val name = record.audioName ?: return null
        val file = File(dir(context.applicationContext), name)
        return file.takeIf { it.exists() && it.length() > 44 }
    }

    /** 读回 8 kHz PCM（去掉 44 字节 WAV 头），用于"重新识别" */
    suspend fun readPcm(context: Context, record: RecognitionRecord): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = audioFile(context, record) ?: return@runCatching null
                file.readBytes().let { if (it.size > 44) it.copyOfRange(44, it.size) else null }
            }.getOrNull()
        }

    private fun pruneAudio(context: Context, keep: List<RecognitionRecord>) {
        val keepNames = keep.mapNotNull { it.audioName }.toSet()
        runCatching {
            dir(context).listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".wav") && file.name !in keepNames) file.delete()
            }
        }
    }

    /** 8 kHz 单声道 PCM16 → 最小可用的 WAV，方便直接丢给 MediaPlayer 回放 */
    private fun toWav(pcm: ByteArray): ByteArray {
        val dataLen = pcm.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)                       // fmt chunk size
            putShort(1)                      // PCM
            putShort(1)                      // mono
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * 2)          // byte rate = rate * channels * bytes/sample
            putShort(2)                      // block align
            putShort(16)                     // bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataLen)
        }.array()
        return header + pcm
    }
}
