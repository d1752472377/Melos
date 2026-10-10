package com.aesthetic.music.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * 均衡器预设模型
 */
data class EqualizerPreset(
    val id: String,
    val name: String,
    val gains: List<Float>, // 10 频段增益（单位 dB，-12f ~ +12f）
)

/**
 * 均衡器状态模型
 */
data class EqualizerState(
    val isEnabled: Boolean = true,
    val currentPresetId: String = "flat",
    val preampGain: Float = 0f, // 预衰减 (-12f ~ +12f dB)
    val bandGains: List<Float> = List(10) { 0f }, // 10 个频段的增益 (-12f ~ +12f dB)
)

/**
 * 全局均衡器管理器：
 * 1. 深度对齐 GZ Music 专业调音台设计；
 * 2. 支持 10 频段精细微调（31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz）；
 * 3. 支持预衰减（Preamp，避免大增益导致的数字削波破音）；
 * 4. 内置 8 种高品质大师调音预设 + 自定义记忆；
 * 5. 自动对接 Android 原生 Hardware Equalizer (android.media.audiofx.Equalizer)；
 * 6. 提供高精度三次平滑频响曲线算法，供 UI 实时绘制极光贝塞尔频响曲线。
 */
object MelosEqualizer {

    private const val TAG = "MelosEqualizer"
    private const val PREFS_NAME = "melos_equalizer_prefs"

    // 10 个标准倍频程中心频率
    val FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val FREQUENCY_LABELS = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")

    val PRESETS = listOf(
        EqualizerPreset("flat", "自然原声", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqualizerPreset("bass", "重低音增强", listOf(6.0f, 5.0f, 3.5f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqualizerPreset("vocal", "清澈人声", listOf(-1.0f, 0f, 1.0f, 3.0f, 4.0f, 3.0f, 1.5f, 0.5f, 0f, 0f)),
        EqualizerPreset("classical", "古典交响", listOf(4.5f, 3.5f, 2.0f, 1.0f, -1.0f, -1.0f, 0f, 2.5f, 3.5f, 4.5f)),
        EqualizerPreset("rock", "摇滚震撼", listOf(5.0f, 3.5f, -1.0f, -2.0f, 1.0f, 3.0f, 4.0f, 5.0f, 4.0f, 3.0f)),
        EqualizerPreset("pop", "流行悦耳", listOf(-1.0f, 1.0f, 3.0f, 4.0f, 2.0f, -1.0f, -1.0f, 0.5f, 1.5f, 2.5f)),
        EqualizerPreset("night", "夜间柔和", listOf(2.0f, 1.0f, 0f, -2.0f, -3.0f, -3.0f, -2.0f, -1.0f, 0f, 1.0f)),
        EqualizerPreset("electronic", "纯净电音", listOf(5.5f, 4.0f, 1.0f, 0f, -2.0f, 2.0f, 1.5f, 3.5f, 4.5f, 5.5f)),
    )

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var hardwareEqualizer: Equalizer? = null
    private var currentSessionId: Int = 0

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val enabled = sp.getBoolean("enabled", true)
        val presetId = sp.getString("preset_id", "flat") ?: "flat"
        val preamp = sp.getFloat("preamp", 0f)

        val gains = mutableListOf<Float>()
        for (i in 0 until 10) {
            val defaultVal = PRESETS.firstOrNull { it.id == presetId }?.gains?.getOrNull(i) ?: 0f
            gains.add(sp.getFloat("band_$i", defaultVal))
        }

        _state.value = EqualizerState(
            isEnabled = enabled,
            currentPresetId = presetId,
            preampGain = preamp,
            bandGains = gains
        )
    }

    /**
     * 绑定播放器的 AudioSessionId 并应用硬件生效
     */
    fun attachAudioSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == currentSessionId) return
        currentSessionId = sessionId

        try {
            hardwareEqualizer?.release()
            val eq = Equalizer(0, sessionId)
            eq.enabled = _state.value.isEnabled
            hardwareEqualizer = eq
            applyHardwareBands()
            Log.d(TAG, "Attached to audioSessionId: $sessionId (hardware bands=${eq.numberOfBands})")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to initialize hardware Equalizer: ${e.message}")
            hardwareEqualizer = null
        }
    }

    fun release() {
        try {
            hardwareEqualizer?.release()
        } catch (_: Throwable) {}
        hardwareEqualizer = null
        currentSessionId = 0
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = enabled)
        prefs?.edit()?.putBoolean("enabled", enabled)?.apply()
        try {
            hardwareEqualizer?.enabled = enabled
        } catch (e: Throwable) {
            Log.w(TAG, "setEnabled failed: ${e.message}")
        }
    }

    fun setPreampGain(gain: Float) {
        val clamped = gain.coerceIn(-12f, 12f)
        _state.value = _state.value.copy(preampGain = clamped)
        prefs?.edit()?.putFloat("preamp", clamped)?.apply()
    }

    fun applyPreset(presetId: String) {
        val preset = PRESETS.firstOrNull { it.id == presetId } ?: return
        val newGains = preset.gains
        _state.value = _state.value.copy(
            currentPresetId = presetId,
            bandGains = newGains
        )
        saveBains(newGains, presetId)
        applyHardwareBands()
    }

    fun setBandGain(bandIndex: Int, gain: Float) {
        if (bandIndex !in 0 until 10) return
        val clamped = gain.coerceIn(-12f, 12f)
        val currentGains = _state.value.bandGains.toMutableList()
        currentGains[bandIndex] = clamped

        // 检查是否匹配已有预设
        var matchedPreset = "custom"
        for (preset in PRESETS) {
            if (preset.gains.zip(currentGains).all { (a, b) -> kotlin.math.abs(a - b) < 0.1f }) {
                matchedPreset = preset.id
                break
            }
        }

        _state.value = _state.value.copy(
            currentPresetId = matchedPreset,
            bandGains = currentGains
        )
        saveBains(currentGains, matchedPreset)
        applyHardwareBands()
    }

    fun resetToFlat() {
        applyPreset("flat")
        setPreampGain(0f)
    }

    private fun saveBains(gains: List<Float>, presetId: String) {
        val editor = prefs?.edit() ?: return
        editor.putString("preset_id", presetId)
        for (i in gains.indices) {
            editor.putFloat("band_$i", gains[i])
        }
        editor.apply()
    }

    private fun applyHardwareBands() {
        val eq = hardwareEqualizer ?: return
        if (!_state.value.isEnabled) return

        try {
            val numBands = eq.numberOfBands.toInt()
            val minMB = eq.bandLevelRange[0] // 通常 -1500 (-15dB)
            val maxMB = eq.bandLevelRange[1] // 通常 +1500 (+15dB)

            for (hwBand in 0 until numBands) {
                val hwFreq = eq.getCenterFreq(hwBand.toShort()) / 1000 // 转为 Hz
                // 找到与硬件频点最接近的软件频段
                val closestIdx = FREQUENCIES.indices.minByOrNull { kotlin.math.abs(FREQUENCIES[it] - hwFreq) } ?: 0
                val gainDb = _state.value.bandGains[closestIdx]
                val levelMB = (gainDb * 100).toInt().coerceIn(minMB.toInt(), maxMB.toInt()).toShort()
                eq.setBandLevel(hwBand.toShort(), levelMB)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "applyHardwareBands error: ${e.message}")
        }
    }

    /**
     * 计算平滑连续频响响应曲线：
     * 输入对数频率 f (20Hz ~ 20000Hz)，计算由 10 个频段与预衰减合成的输出增益 (dB)。
     * 用于在 UI Canvas 上绘制专业发光频响曲线。
     */
    fun calculateFrequencyGain(frequencyHz: Float, gains: List<Float>, preamp: Float): Float {
        var totalGain = preamp
        val logF = log10(frequencyHz.coerceIn(20f, 20000f))

        for (i in 0 until 10) {
            val centerF = FREQUENCIES[i].toFloat()
            val logCenter = log10(centerF)
            val gain = gains[i]
            // 高斯钟形滤波频响权重衰减 (Q ≈ 1.4)
            val diff = logF - logCenter
            val weight = exp(-0.5 * (diff / 0.35).pow(2)).toFloat()
            totalGain += gain * weight
        }
        return totalGain.coerceIn(-18f, 18f)
    }
}
