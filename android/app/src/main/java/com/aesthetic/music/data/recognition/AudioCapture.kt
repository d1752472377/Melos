package com.aesthetic.music.data.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 前台短录音：录约 [seconds] 秒，实时降采样成 8 kHz PCM16 并留在内存里。
 *
 * 三条刻意的约束：
 * 1. 不落盘 —— 音频只在内存中处理，识别完即丢弃；
 * 2. 只用 VOICE_RECOGNITION（失败再退到 MIC）—— 前者不做通话降噪与自动增益，
 *    音乐频谱不会被削平；
 * 3. 采集用 44.1/48/16 kHz 的常规采样率，再在本地低通降到 8 kHz，而不是直接要 8 kHz：
 *    部分设备的 8 kHz 通道会走语音处理链路，音质不适合做指纹。
 */
object AudioCapture {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * 录制一段音频，返回 8 kHz 单声道 PCM16（小端）。
     *
     * @throws IllegalStateException 麦克风不可用 / 超时 / 权限缺失
     */
    @SuppressLint("MissingPermission")
    suspend fun record(
        context: Context,
        seconds: Int = 10,
        onProgress: (elapsedMs: Long, totalMs: Long) -> Unit = { _, _ -> },
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) throw IllegalStateException("需要麦克风权限")

        val audio = openMicrophone()
        try {
            val resampler = AudioResampler(audio.sampleRate, seconds)
            val chunk = ShortArray(2048)
            val totalMs = seconds * 1000L
            val started = SystemClock.elapsedRealtime()
            val deadline = started + totalMs + 6000L
            var lastReportMs = 0L

            while (!resampler.isFull) {
                if (!coroutineContext.isActive) throw IllegalStateException("已取消")
                if (SystemClock.elapsedRealtime() > deadline) throw IllegalStateException("录音超时")
                val read = audio.read(chunk, 0, chunk.size, AudioRecord.READ_NON_BLOCKING)
                when {
                    read < 0 -> throw IllegalStateException("麦克风读取失败")
                    read == 0 -> Thread.sleep(15)
                    else -> {
                        for (i in 0 until read) resampler.add(chunk[i] / 32768.0)
                        // 回调节流到 100ms 一次：每次读块就回调会让界面每秒重组二十多次
                        val elapsed = SystemClock.elapsedRealtime() - started
                        if (elapsed - lastReportMs >= 100) {
                            lastReportMs = elapsed
                            onProgress(elapsed.coerceAtMost(totalMs), totalMs)
                        }
                    }
                }
            }
            onProgress(totalMs, totalMs)
            resampler.toPcmBytes()
        } finally {
            try {
                audio.stop()
            } catch (_: Exception) {
            }
            audio.release()
        }
    }

    private fun openMicrophone(): AudioRecord {
        // 先 VOICE_RECOGNITION（不做降噪增益），再退到 MIC
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        val rates = intArrayOf(44100, 48000, 16000)
        var lastError: Exception? = null
        for (source in sources) {
            for (rate in rates) {
                var candidate: AudioRecord? = null
                try {
                    val minBuffer = AudioRecord.getMinBufferSize(
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                    if (minBuffer <= 0) continue
                    candidate = AudioRecord(
                        source,
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minBuffer, rate / 2),
                    )
                    if (candidate.state != AudioRecord.STATE_INITIALIZED) {
                        throw IllegalStateException("麦克风不可用")
                    }
                    candidate.startRecording()
                    if (candidate.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        throw IllegalStateException("麦克风未能启动")
                    }
                    return candidate
                } catch (e: Exception) {
                    lastError = e
                    try {
                        candidate?.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        throw IllegalStateException(lastError?.message ?: "没有可用的麦克风配置")
    }
}
