package com.aesthetic.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 软件 10 段 peaking biquad 均衡器（Media3 AudioProcessor）。
 *
 * 从硬件 `android.media.audiofx.Equalizer` 迁移而来：硬件效果器在部分设备上
 * 首次/切歌挂载会产生无法消除的起播爆音，因此在 PCM 层自行处理。
 *
 * ## 线程模型
 * UI 线程只写 [targetParams]（一个不可变快照的 @Volatile 引用），音频线程在
 * `queueInput` 里读取引用后自行计算，不加锁、不阻塞。
 *
 * ## 平滑
 * 增益、preamp、dry/wet 都以约 30ms 的时长按块线性逼近目标值；
 * biquad 系数每块重算一次，避免拖动滑条/开关 EQ 时出现咔哒声。
 *
 * ## 支持格式
 * 只处理 PCM_16BIT；其他编码（FLOAT 等）原样透传（仍消耗输入、输出相同字节）。
 * 全 0 增益且 preamp 为 0 时同样逐字节原样拷贝，不做任何浮点运算。
 */
class MelosEqAudioProcessor : BaseAudioProcessor() {

    private data class Params(
        val enabled: Boolean,
        val preampDb: Float,
        val gainsDb: FloatArray,
    )

    @Volatile
    private var targetParams = Params(
        enabled = true,
        preampDb = 0f,
        gainsDb = FloatArray(BANDS),
    )

    private companion object {
        const val BANDS = 10
        const val SMOOTH_SECONDS = 0.030f
        const val SMOOTH_BLOCK_FRAMES = 32
        const val Q = 1.4f
        const val MAX_DB = 24f
        val SET_FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    }

    private var sampleRate = 0
    private var channelCount = 0
    private var passthroughEncoding = false

    /** filters[channel][band] */
    private var filters: Array<Array<Biquad>> = emptyArray()
    private val smoothedGains = FloatArray(BANDS)
    private var smoothedPreampDb = 0f
    private var smoothedWet = 0f
    private var coefficientsDirty = true

    fun setParams(enabled: Boolean, preampDb: Float, gainsDb: List<Float>) {
        val gains = FloatArray(BANDS) { i -> gainsDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f) }
        targetParams = Params(
            enabled = enabled,
            preampDb = preampDb.coerceIn(-12f, 12f),
            gainsDb = gains,
        )
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            passthroughEncoding = true
            filters = emptyArray()
            sampleRate = inputAudioFormat.sampleRate
            channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
            return inputAudioFormat
        }

        passthroughEncoding = false
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        filters = Array(channelCount) { Array(BANDS) { Biquad() } }
        snapToTarget()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetFilterState()
        coefficientsDirty = true
    }

    override fun onReset() {
        filters = emptyArray()
        sampleRate = 0
        channelCount = 0
        passthroughEncoding = false
        smoothedGains.fill(0f)
        smoothedPreampDb = 0f
        smoothedWet = 0f
        coefficientsDirty = true
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remainingBytes = inputBuffer.remaining()
        if (remainingBytes == 0) return

        val outputBuffer = replaceOutputBuffer(remainingBytes)
        val params = targetParams

        if (passthroughEncoding || filters.isEmpty() || channelCount <= 0) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val frameBytes = 2 * channelCount
        val totalFrames = remainingBytes / frameBytes
        val inputShorts = inputBuffer.asShortBuffer()
        val outputShorts = outputBuffer.asShortBuffer()

        var frame = 0
        while (frame < totalFrames) {
            val blockFrames = min(SMOOTH_BLOCK_FRAMES, totalFrames - frame)
            advanceSmoothing(params, blockFrames)
            val fullyFlat = isFlatAndSettled()
            if (fullyFlat) {
                for (i in 0 until blockFrames * channelCount) {
                    outputShorts.put(inputShorts.get())
                }
            } else {
                val wet = smoothedWet
                val preamp = 10.0.pow(smoothedPreampDb / 20.0).toFloat()
                for (f in 0 until blockFrames) {
                    for (ch in 0 until channelCount) {
                        val dry = inputShorts.get() / 32768f
                        var wetSample = dry
                        val channelFilters = filters[ch]
                        for (band in 0 until BANDS) {
                            wetSample = channelFilters[band].process(wetSample)
                        }
                        wetSample *= preamp
                        val mixed = dry + (wetSample - dry) * wet
                        val clipped = mixed.coerceIn(-1f, 1f)
                        outputShorts.put((clipped * 32767f).roundToInt().toShort())
                    }
                }
            }
            frame += blockFrames
        }

        val processedBytes = totalFrames * frameBytes
        inputBuffer.position(inputBuffer.position() + processedBytes)
        outputBuffer.position(outputBuffer.position() + processedBytes)
        // 不足一帧的尾部字节原样搬走，保证输出长度与输入完全一致。
        if (remainingBytes > processedBytes) {
            outputBuffer.put(inputBuffer)
        }
        outputBuffer.flip()
    }

    private fun snapToTarget() {
        val p = targetParams
        p.gainsDb.copyInto(smoothedGains)
        smoothedPreampDb = p.preampDb
        smoothedWet = if (p.enabled) 1f else 0f
        coefficientsDirty = true
        updateCoefficients()
    }

    private fun advanceSmoothing(p: Params, frames: Int) {
        val seconds = frames.toFloat() / sampleRate
        val gainStep = MAX_DB * seconds / SMOOTH_SECONDS
        var gainsChanged = false
        for (i in 0 until BANDS) {
            val previous = smoothedGains[i]
            smoothedGains[i] = approach(previous, p.gainsDb[i], gainStep)
            if (smoothedGains[i] != previous) gainsChanged = true
        }
        smoothedPreampDb = approach(smoothedPreampDb, p.preampDb, gainStep)
        val wetTarget = if (p.enabled) 1f else 0f
        val wetStep = seconds / SMOOTH_SECONDS
        smoothedWet = approach(smoothedWet, wetTarget, wetStep)
        if (gainsChanged) coefficientsDirty = true
        if (coefficientsDirty) updateCoefficients()
    }

    private fun approach(current: Float, target: Float, maxStep: Float): Float {
        val diff = target - current
        return when {
            abs(diff) <= maxStep -> target
            diff > 0f -> current + maxStep
            else -> current - maxStep
        }
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return
        for (ch in filters.indices) {
            for (band in 0 until BANDS) {
                filters[ch][band].setPeaking(
                    sampleRate = sampleRate,
                    frequencyHz = SET_FREQUENCIES[band].toFloat(),
                    gainDb = smoothedGains[band],
                    q = Q,
                )
            }
        }
        coefficientsDirty = false
    }

    private fun resetFilterState() {
        for (channel in filters) {
            for (filter in channel) filter.reset()
        }
    }

    private fun isFlatAndSettled(): Boolean {
        val flatGains = smoothedGains.none { abs(it) > 0.001f }
        val flatPreamp = abs(smoothedPreampDb) <= 0.001f
        // 关闭且已经淡到 0，或所有增益/preamp 都为 0：完全旁路，保证透传。
        if (smoothedWet <= 0.001f) return true
        if (flatGains && flatPreamp) return true
        return false
    }

    private class Biquad {
        private var b0 = 1f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun setPeaking(sampleRate: Int, frequencyHz: Float, gainDb: Float, q: Float) {
            // 频点高于当前采样率的可用上限时直接做恒等变换，避免多个高频段
            // 被压到同一个接近 Nyquist 的频点上叠加。
            if (frequencyHz >= sampleRate * 0.45f) {
                b0 = 1f
                b1 = 0f
                b2 = 0f
                a1 = 0f
                a2 = 0f
                return
            }
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * Math.PI * frequencyHz / sampleRate
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha / a
            b0 = ((1.0 + alpha * a) / a0).toFloat()
            b1 = (-2.0 * cosW / a0).toFloat()
            b2 = ((1.0 - alpha * a) / a0).toFloat()
            a1 = (-2.0 * cosW / a0).toFloat()
            a2 = ((1.0 - alpha / a) / a0).toFloat()
        }

        fun process(input: Float): Float {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }
    }
}
