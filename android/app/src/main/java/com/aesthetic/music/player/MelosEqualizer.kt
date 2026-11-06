package com.aesthetic.music.player

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

data class EqualizerPreset(
    val id: String,
    val name: String,
    val gains: List<Float>,
)

data class EqualizerState(
    val isEnabled: Boolean = true,
    val currentPresetId: String = "flat",
    val preampGain: Float = 0f,
    val bandGains: List<Float> = List(10) { 0f },
)

object MelosEqualizer {

    private const val PREFS_NAME = "melos_equalizer_prefs"

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

    val processor = MelosEqAudioProcessor()

    private var prefs: SharedPreferences? = null

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
        pushToProcessor()
    }

    @Deprecated("Software EQ no longer needs an AudioSession")
    fun attachAudioSession(sessionId: Int) {
    }

    @Deprecated("Software EQ does not need deferred attachment")
    fun onPlaybackActive(active: Boolean) {
    }

    @Deprecated("Software EQ is released with the player's AudioSink")
    fun release() {
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = enabled)
        prefs?.edit()?.putBoolean("enabled", enabled)?.apply()
        pushToProcessor()
    }

    fun setPreampGain(gain: Float) {
        val clamped = gain.coerceIn(-12f, 12f)
        _state.value = _state.value.copy(preampGain = clamped)
        prefs?.edit()?.putFloat("preamp", clamped)?.apply()
        pushToProcessor()
    }

    fun applyPreset(presetId: String) {
        val preset = PRESETS.firstOrNull { it.id == presetId } ?: return
        val newGains = preset.gains
        _state.value = _state.value.copy(
            currentPresetId = presetId,
            bandGains = newGains
        )
        saveBands(newGains, presetId)
        pushToProcessor()
    }

    fun setBandGain(bandIndex: Int, gain: Float) {
        if (bandIndex !in 0 until 10) return
        val clamped = gain.coerceIn(-12f, 12f)
        val currentGains = _state.value.bandGains.toMutableList()
        currentGains[bandIndex] = clamped

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
        saveBands(currentGains, matchedPreset)
        pushToProcessor()
    }

    fun resetToFlat() {
        applyPreset("flat")
        setPreampGain(0f)
    }

    private fun saveBands(gains: List<Float>, presetId: String) {
        val editor = prefs?.edit() ?: return
        editor.putString("preset_id", presetId)
        for (i in gains.indices) {
            editor.putFloat("band_$i", gains[i])
        }
        editor.apply()
    }

    private fun pushToProcessor() {
        val s = _state.value
        processor.setParams(
            enabled = s.isEnabled,
            preampDb = s.preampGain,
            gainsDb = s.bandGains,
        )
    }

    fun calculateFrequencyGain(frequencyHz: Float, gains: List<Float>, preamp: Float): Float {
        var totalGain = preamp
        val logF = log10(frequencyHz.coerceIn(20f, 20000f))

        for (i in 0 until 10) {
            val centerF = FREQUENCIES[i].toFloat()
            val logCenter = log10(centerF)
            val gain = gains[i]
            val diff = logF - logCenter
            val weight = exp(-0.5 * (diff / 0.35).pow(2)).toFloat()
            totalGain += gain * weight
        }
        return totalGain.coerceIn(-18f, 18f)
    }
}
