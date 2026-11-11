package com.aesthetic.music.data.recognition

/**
 * 录音降采样器：把任意采样率的单声道音频经低通滤波降到 8 kHz PCM16。
 *
 * 为什么必须先低通再抽点：直接每隔 N 个点取一个会产生频谱混叠，音乐里 3~8 kHz 的能量
 * 会折叠到低频，指纹特征跟着变形，识别就失败。这里用 127 抽头的窗函数 sinc 低通
 * （截止 3.8 kHz），和酷狗客户端的处理方式一致。
 */
class AudioResampler(
    private val sourceRate: Int,
    seconds: Int = 10,
) {

    init {
        require(sourceRate in TARGET_RATE..384_000) { "不支持的采样率: $sourceRate" }
    }

    private val coefficients = DoubleArray(TAPS)
    private val history = DoubleArray(TAPS)
    private var position = 0
    private var frames = 0L

    /** 输出缓冲，有效长度见 [count]。 */
    val samples = ShortArray(TARGET_RATE * seconds)
    var count = 0
        private set

    init {
        val cutoff = 3800.0 / sourceRate
        var sum = 0.0
        for (i in 0 until TAPS) {
            val x = i - (TAPS - 1) / 2.0
            val sinc = if (x == 0.0) 2 * cutoff else Math.sin(2 * Math.PI * cutoff * x) / (Math.PI * x)
            // Blackman 窗，压低旁瓣
            val window = 0.42 - 0.5 * Math.cos(2 * Math.PI * i / (TAPS - 1)) +
                    0.08 * Math.cos(4 * Math.PI * i / (TAPS - 1))
            coefficients[i] = sinc * window
            sum += coefficients[i]
        }
        for (i in 0 until TAPS) coefficients[i] /= sum
    }

    val isFull: Boolean get() = count >= samples.size

    /** 喂一个归一化到 [-1, 1] 的采样点。缓冲满了之后自动忽略后续输入。 */
    fun add(value: Double) {
        if (count >= samples.size) return
        // 非有限值统一记成 0：NaT/Infinity 直接参与滤波会污染整段输出
        val safe = if (value.isFinite()) value else 0.0
        history[position] = safe
        frames++
        // 每来 sourceRate/TARGET_RATE 个输入点才产出一个输出点
        if (frames * TARGET_RATE / sourceRate > count) {
            val filtered = if (sourceRate == TARGET_RATE) {
                safe
            } else {
                var acc = 0.0
                for (i in 0 until TAPS) {
                    acc += history[(position - i + TAPS) % TAPS] * coefficients[i]
                }
                acc
            }
            samples[count++] = (filtered.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        position = (position + 1) % TAPS
    }

    /** 有效样本的均方根（0~1），用来判断是不是一段静音。 */
    fun rms(): Double {
        if (count == 0) return 0.0
        var energy = 0.0
        for (i in 0 until count) {
            val v = samples[i] / 32768.0
            energy += v * v
        }
        return Math.sqrt(energy / count)
    }

    /** 把小端 PCM16 字节写出来，长度按有效样本数截断。 */
    fun toPcmBytes(): ByteArray {
        val out = ByteArray(count * 2)
        for (i in 0 until count) {
            val v = samples[i].toInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    private companion object {
        const val TARGET_RATE = 8_000
        const val TAPS = 127
    }
}
