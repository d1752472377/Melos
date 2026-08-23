package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences

/** 主题模式（参考 App 的三档切换） */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 播放页主题（对应参考 App 的播放页主题选择：经典/封面卡片/Apple Music） */
enum class PlayerTheme(val value: String, val label: String) {
    CLASSIC("classic", "经典"),
    COVER("cover", "封面卡片"),
    APPLE("apple", "Apple Music");

    companion object {
        fun fromValue(v: String?): PlayerTheme = entries.firstOrNull { it.value == v } ?: CLASSIC
    }
}

/** 音质等级（对应后端 quality 参数） */
enum class QualityLevel(val value: String, val label: String) {
    STANDARD("standard", "标准 128k"),
    HIGH("high", "高品质 320k"),
    LOSSLESS("lossless", "无损 FLAC");

    companion object {
        fun fromValue(v: String?): QualityLevel = entries.firstOrNull { it.value == v } ?: LOSSLESS
    }
}

/**
 * 设置持久化（SharedPreferences）：主题模式三档、音质等级、悬浮窗开关。
 * 所有读取容错：未初始化或异常时返回默认值，绝不抛异常（避免播放链路崩溃）。
 */
object SettingsStore {

    private const val PREFS = "aesthetic_music_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_QUALITY = "quality"
    private const val KEY_FLOATING_CAPSULE = "floating_capsule_enabled"
    private const val KEY_PLAYER_THEME = "player_theme"
    private const val KEY_PLAYBACK_SPEED = "playback_speed"
    private const val KEY_PREFERRED_BASE = "preferred_base_url"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    private fun p(): SharedPreferences? = prefs

    // ===== 主题模式 =====
    fun getThemeMode(): ThemeMode = try {
        val v = p()?.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        ThemeMode.entries.firstOrNull { it.name == v } ?: ThemeMode.SYSTEM
    } catch (_: Exception) {
        ThemeMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) {
        try { p()?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply() } catch (_: Exception) {}
    }

    // ===== 音质 =====
    fun getQuality(): QualityLevel = try {
        QualityLevel.fromValue(p()?.getString(KEY_QUALITY, null))
    } catch (_: Exception) {
        QualityLevel.LOSSLESS
    }

    fun setQuality(level: QualityLevel) {
        try { p()?.edit()?.putString(KEY_QUALITY, level.value)?.apply() } catch (_: Exception) {}
    }

    // ===== 悬浮窗开关 =====
    fun isFloatingCapsuleEnabled(): Boolean = try {
        p()?.getBoolean(KEY_FLOATING_CAPSULE, false) ?: false
    } catch (_: Exception) {
        false
    }

    fun setFloatingCapsuleEnabled(enabled: Boolean) {
        try { p()?.edit()?.putBoolean(KEY_FLOATING_CAPSULE, enabled)?.apply() } catch (_: Exception) {}
    }

    // ===== 播放页主题 =====
    fun getPlayerTheme(): PlayerTheme = try {
        PlayerTheme.fromValue(p()?.getString(KEY_PLAYER_THEME, null))
    } catch (_: Exception) {
        PlayerTheme.CLASSIC
    }

    fun setPlayerTheme(theme: PlayerTheme) {
        try { p()?.edit()?.putString(KEY_PLAYER_THEME, theme.value)?.apply() } catch (_: Exception) {}
    }

    // ===== 倍速播放（0.5x - 2.0x）=====
    fun getPlaybackSpeed(): Float = try {
        (p()?.getFloat(KEY_PLAYBACK_SPEED, 1f) ?: 1f).coerceIn(0.5f, 2f)
    } catch (_: Exception) {
        1f
    }

    fun setPlaybackSpeed(speed: Float) {
        try {
            p()?.edit()?.putFloat(KEY_PLAYBACK_SPEED, speed.coerceIn(0.5f, 2f))?.apply()
        } catch (_: Exception) {
        }
    }

    // ===== 后端节点（分布式故障转移）=====
    /** 上次成功连通的后端地址，下次启动优先使用 */
    fun getPreferredBaseUrl(): String? = try {
        p()?.getString(KEY_PREFERRED_BASE, null)
    } catch (_: Exception) {
        null
    }

    fun setPreferredBaseUrl(url: String) {
        try { p()?.edit()?.putString(KEY_PREFERRED_BASE, url)?.apply() } catch (_: Exception) {}
    }
}
