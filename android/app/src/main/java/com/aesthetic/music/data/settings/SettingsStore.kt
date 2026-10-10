package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences

/** 主题模式（参考 App 的三档切换） */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 播放页主题（经典/黑胶唱机/Apple Music/唱片封套/流光 CD） */
enum class PlayerTheme(val value: String, val label: String) {
    CLASSIC("classic", "经典"),
    COVER("cover", "黑胶唱机"),
    APPLE("apple", "Apple Music"),
    RECORD_SHELF("record_shelf", "唱片封套"),
    LUMINOUS_CD("luminous_cd", "流光 CD");

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

/** 系统状态栏显示策略 */
enum class StatusBarMode(val value: String, val label: String, val desc: String) {
    ALWAYS("always", "显示", "始终显示系统状态栏"),
    PLAYER_ONLY("player", "仅播放页", "播放页沉浸时隐藏，其余页面保持显示"),
    HIDDEN("hidden", "全局隐藏", "所有页面都隐藏系统状态栏");

    companion object {
        fun fromValue(v: String?): StatusBarMode = entries.firstOrNull { it.value == v } ?: ALWAYS
    }
}

/** 下载/缓存文件命名规则 */
enum class DownloadNaming(val value: String, val label: String, val example: String) {
    TITLE_ARTIST("title_artist", "歌名 - 歌手", "夜曲 - 周杰伦.mp3"),
    TITLE("title", "仅歌名", "夜曲.mp3"),
    ARTIST_TITLE("artist_title", "歌手 - 歌名", "周杰伦 - 夜曲.mp3");

    companion object {
        fun fromValue(v: String?): DownloadNaming = entries.firstOrNull { it.value == v } ?: TITLE_ARTIST
    }
}

/** 缓存失败处理策略 */
enum class DownloadFailurePolicy(val value: String, val label: String, val desc: String) {
    RETRY("retry", "自动重试", "失败后自动重试 2 次"),
    SKIP("skip", "直接跳过", "失败即跳过，不再重试也不提示"),
    NOTIFY("notify", "提示我", "失败时提示，由我决定是否重试");

    companion object {
        fun fromValue(v: String?): DownloadFailurePolicy =
            entries.firstOrNull { it.value == v } ?: RETRY
    }
}

/** 歌词字体大小选项 */
enum class LyricFontSize(val value: String, val label: String, val spSize: Int) {
    STANDARD("standard", "标准", 22),
    LARGE("large", "较大", 26),
    EXTRA_LARGE("extra_large", "特大", 30);

    companion object {
        fun fromValue(v: String?): LyricFontSize = entries.firstOrNull { it.value == v } ?: STANDARD
    }
}

/** 歌词虚化/聚焦模糊模式 */
enum class LyricBlurMode(val value: String, val label: String, val maxBlurPx: Float) {
    OFF("off", "关闭 (清晰)", 0f),
    SUBTLE("subtle", "轻度景深", 2.5f),
    DEEP("deep", "柔和质感", 5f);

    companion object {
        fun fromValue(v: String?): LyricBlurMode = entries.firstOrNull { it.value == v } ?: OFF
    }
}

/** 播放页背景专辑图模糊度 */
enum class BackgroundBlurMode(val value: String, val label: String, val blurDp: Int) {
    LIGHT("light", "微弱", 20),
    MEDIUM("medium", "标准", 40),
    HEAVY("heavy", "浓郁", 65);

    companion object {
        fun fromValue(v: String?): BackgroundBlurMode = entries.firstOrNull { it.value == v } ?: MEDIUM
    }
}

/**
 * 新增设置项快照。
 *
 * 一次性读/写整组（而不是十几个分散的 key），避免"加了一项忘了读/写"这类漏配；
 * 写入是一次 `edit()` 批量提交，代价可忽略。
 */
data class UserPrefs(
    // ===== 主题与外观 =====
    val statusBarMode: StatusBarMode = StatusBarMode.ALWAYS,
    // ===== 播放 =====
    val preloadNext: Boolean = true,
    val crossFade: Boolean = false,
    val resumeAfterInterrupt: Boolean = true,
    val mixWithOthers: Boolean = false,
    // ===== 歌词 =====
    val lyricTranslation: Boolean = true,
    val lyricRomanization: Boolean = false,
    val lyricOnline: Boolean = true,
    val lyricLocal: Boolean = false,
    // ===== 下载与缓存 =====
    val downloadWhilePlaying: Boolean = true,
    val downloadNaming: DownloadNaming = DownloadNaming.TITLE_ARTIST,
    val downloadCoverLyrics: Boolean = true,
    val downloadFailurePolicy: DownloadFailurePolicy = DownloadFailurePolicy.RETRY,
    val downloadWifiOnly: Boolean = false,
    // ===== 听歌统计 =====
    /** 是否记录听歌数据（关闭后时长/次数/排行都不再累计，已有历史保留） */
    val statsRecording: Boolean = true,
    /** 是否把听歌事件随云同步上传（关闭后仅本地记录，不上传） */
    val statsCloudSync: Boolean = true,
    // ===== 音源策略（插件管理页） =====
    /** 失败换源：当前音源解析失败时自动尝试下一个音源 */
    val sourceFailover: Boolean = true,
    /** 智能切换：当前音源被判定为失败/偏慢时，不等失败直接改用更优音源 */
    val sourceSmartSwitch: Boolean = false,
    /** 自动按照最快的进行使用：按实测速度排序，永远优先用最快的音源 */
    val sourceFastest: Boolean = false,
)

/**
 * 设置持久化（SharedPreferences）：主题模式三档、音质等级、悬浮窗开关。
 * 所有读取容错：未初始化或异常时返回默认值，绝不抛异常（避免播放链路崩溃）。
 */
object SettingsStore {

    private const val TAG = "SettingsStore"
    private const val PREFS = "aesthetic_music_settings"
    /** 缓存上限默认值：2GB */
    const val DEFAULT_CACHE_LIMIT_BYTES: Long = 2L * 1024 * 1024 * 1024
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_QUALITY = "quality"
    private const val KEY_CACHE_LIMIT = "cache_limit_bytes"
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

    fun setThemeMode(mode: ThemeMode) = bestEffort(TAG, "保存主题模式") {
        p()?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    // ===== 音质 =====
    fun getQuality(): QualityLevel = try {
        QualityLevel.fromValue(p()?.getString(KEY_QUALITY, null))
    } catch (_: Exception) {
        QualityLevel.LOSSLESS
    }

    fun setQuality(level: QualityLevel) = bestEffort(TAG, "保存音质等级") {
        p()?.edit()?.putString(KEY_QUALITY, level.value)?.apply()
    }

    // ===== 缓存上限（字节）：播放/下载缓存总量超过它就按最旧淘汰，默认 2GB =====
    fun getCacheLimitBytes(): Long = try {
        p()?.getLong(KEY_CACHE_LIMIT, DEFAULT_CACHE_LIMIT_BYTES) ?: DEFAULT_CACHE_LIMIT_BYTES
    } catch (_: Exception) {
        DEFAULT_CACHE_LIMIT_BYTES
    }

    fun setCacheLimitBytes(bytes: Long) = bestEffort(TAG, "保存缓存上限") {
        p()?.edit()?.putLong(KEY_CACHE_LIMIT, bytes)?.apply()
    }

    // ===== 播放页主题 =====
    fun getPlayerTheme(): PlayerTheme = try {
        PlayerTheme.fromValue(p()?.getString(KEY_PLAYER_THEME, null))
    } catch (_: Exception) {
        PlayerTheme.CLASSIC
    }

    fun setPlayerTheme(theme: PlayerTheme) = bestEffort(TAG, "保存播放页主题") {
        p()?.edit()?.putString(KEY_PLAYER_THEME, theme.value)?.apply()
    }

    // ===== 倍速播放（0.5x - 2.0x）=====
    fun getPlaybackSpeed(): Float = try {
        (p()?.getFloat(KEY_PLAYBACK_SPEED, 1f) ?: 1f).coerceIn(0.5f, 2f)
    } catch (_: Exception) {
        1f
    }

    fun setPlaybackSpeed(speed: Float) = bestEffort(TAG, "保存播放倍速") {
        p()?.edit()?.putFloat(KEY_PLAYBACK_SPEED, speed.coerceIn(0.5f, 2f))?.apply()
    }

    // ===== 歌词与视觉个性化 =====
    private const val KEY_LYRIC_FONT_SIZE = "lyric_font_size"
    private const val KEY_LYRIC_BLUR_MODE = "lyric_blur_mode"
    private const val KEY_BACKGROUND_BLUR_MODE = "background_blur_mode"

    fun getLyricFontSize(): LyricFontSize = try {
        LyricFontSize.fromValue(p()?.getString(KEY_LYRIC_FONT_SIZE, null))
    } catch (_: Exception) {
        LyricFontSize.STANDARD
    }

    fun setLyricFontSize(size: LyricFontSize) = bestEffort(TAG, "保存歌词字号") {
        p()?.edit()?.putString(KEY_LYRIC_FONT_SIZE, size.value)?.apply()
    }

    fun getLyricBlurMode(): LyricBlurMode = try {
        LyricBlurMode.fromValue(p()?.getString(KEY_LYRIC_BLUR_MODE, null))
    } catch (_: Exception) {
        LyricBlurMode.OFF // 默认清晰无模糊
    }

    fun setLyricBlurMode(mode: LyricBlurMode) = bestEffort(TAG, "保存歌词模糊模式") {
        p()?.edit()?.putString(KEY_LYRIC_BLUR_MODE, mode.value)?.apply()
    }

    fun getBackgroundBlurMode(): BackgroundBlurMode = try {
        BackgroundBlurMode.fromValue(p()?.getString(KEY_BACKGROUND_BLUR_MODE, null))
    } catch (_: Exception) {
        BackgroundBlurMode.MEDIUM
    }

    fun setBackgroundBlurMode(mode: BackgroundBlurMode) = bestEffort(TAG, "保存背景模糊度") {
        p()?.edit()?.putString(KEY_BACKGROUND_BLUR_MODE, mode.value)?.apply()
    }

    // ==================== 设置页（主题与外观 / 播放 / 歌词 / 下载） ====================

    private const val KEY_STATUS_BAR = "status_bar_mode"
    private const val KEY_PRELOAD_NEXT = "play_preload_next"
    private const val KEY_CROSS_FADE = "play_cross_fade"
    private const val KEY_RESUME_AFTER_INTERRUPT = "play_resume_after_interrupt"
    private const val KEY_MIX_WITH_OTHERS = "play_mix_with_others"
    private const val KEY_LYRIC_TRANSLATION = "lyric_translation"
    private const val KEY_LYRIC_ROMANIZATION = "lyric_romanization"
    private const val KEY_LYRIC_ONLINE = "lyric_online"
    private const val KEY_LYRIC_LOCAL = "lyric_local"
    // 媒体卡片的 8 个 key 已随「设置 → 媒体卡片」整页删除（卡片不再有可配置项）：
    // 旧版本写进 SharedPreferences 的值会被忽略，不影响任何逻辑，也不需要清理。
    private const val KEY_DL_WHILE_PLAYING = "download_while_playing"
    private const val KEY_DL_NAMING = "download_naming"
    private const val KEY_DL_COVER_LYRICS = "download_cover_lyrics"
    private const val KEY_DL_FAILURE = "download_failure_policy"
    private const val KEY_DL_WIFI_ONLY = "download_wifi_only"
    private const val KEY_STATS_RECORDING = "stats_recording"
    private const val KEY_STATS_CLOUD = "stats_cloud_sync"
    private const val KEY_SOURCE_FAILOVER = "source_failover"
    private const val KEY_SOURCE_SMART = "source_smart_switch"
    private const val KEY_SOURCE_FASTEST = "source_fastest"

    private fun getBool(key: String, def: Boolean): Boolean = try {
        p()?.getBoolean(key, def) ?: def
    } catch (_: Exception) {
        def
    }

    private fun setBool(key: String, value: Boolean) = bestEffort(TAG, "保存开关 $key") {
        p()?.edit()?.putBoolean(key, value)?.apply()
    }

    private fun getStr(key: String): String? = try {
        p()?.getString(key, null)
    } catch (_: Exception) {
        null
    }

    private fun setStr(key: String, value: String) = bestEffort(TAG, "保存选项 $key") {
        p()?.edit()?.putString(key, value)?.apply()
    }

    /** 读取新增设置项（任一读取异常都回落默认值，绝不抛异常打断设置页） */
    fun readPrefs(): UserPrefs {
        val d = UserPrefs()
        return try {
            UserPrefs(
                statusBarMode = StatusBarMode.fromValue(getStr(KEY_STATUS_BAR)),
                preloadNext = getBool(KEY_PRELOAD_NEXT, d.preloadNext),
                crossFade = getBool(KEY_CROSS_FADE, d.crossFade),
                resumeAfterInterrupt = getBool(KEY_RESUME_AFTER_INTERRUPT, d.resumeAfterInterrupt),
                mixWithOthers = getBool(KEY_MIX_WITH_OTHERS, d.mixWithOthers),
                lyricTranslation = getBool(KEY_LYRIC_TRANSLATION, d.lyricTranslation),
                lyricRomanization = getBool(KEY_LYRIC_ROMANIZATION, d.lyricRomanization),
                lyricOnline = getBool(KEY_LYRIC_ONLINE, d.lyricOnline),
                lyricLocal = getBool(KEY_LYRIC_LOCAL, d.lyricLocal),
                downloadWhilePlaying = getBool(KEY_DL_WHILE_PLAYING, d.downloadWhilePlaying),
                downloadNaming = DownloadNaming.fromValue(getStr(KEY_DL_NAMING)),
                downloadCoverLyrics = getBool(KEY_DL_COVER_LYRICS, d.downloadCoverLyrics),
                downloadFailurePolicy = DownloadFailurePolicy.fromValue(getStr(KEY_DL_FAILURE)),
                downloadWifiOnly = getBool(KEY_DL_WIFI_ONLY, d.downloadWifiOnly),
                statsRecording = getBool(KEY_STATS_RECORDING, d.statsRecording),
                statsCloudSync = getBool(KEY_STATS_CLOUD, d.statsCloudSync),
                sourceFailover = getBool(KEY_SOURCE_FAILOVER, d.sourceFailover),
                sourceSmartSwitch = getBool(KEY_SOURCE_SMART, d.sourceSmartSwitch),
                sourceFastest = getBool(KEY_SOURCE_FASTEST, d.sourceFastest),
            )
        } catch (_: Exception) {
            d
        }
    }

    /** 批量写入新增设置项 */
    fun writePrefs(prefs: UserPrefs) = bestEffort(TAG, "保存设置项") {
        p()?.edit()?.apply {
            putString(KEY_STATUS_BAR, prefs.statusBarMode.value)
            putBoolean(KEY_PRELOAD_NEXT, prefs.preloadNext)
            putBoolean(KEY_CROSS_FADE, prefs.crossFade)
            putBoolean(KEY_RESUME_AFTER_INTERRUPT, prefs.resumeAfterInterrupt)
            putBoolean(KEY_MIX_WITH_OTHERS, prefs.mixWithOthers)
            putBoolean(KEY_LYRIC_TRANSLATION, prefs.lyricTranslation)
            putBoolean(KEY_LYRIC_ROMANIZATION, prefs.lyricRomanization)
            putBoolean(KEY_LYRIC_ONLINE, prefs.lyricOnline)
            putBoolean(KEY_LYRIC_LOCAL, prefs.lyricLocal)
            putBoolean(KEY_DL_WHILE_PLAYING, prefs.downloadWhilePlaying)
            putString(KEY_DL_NAMING, prefs.downloadNaming.value)
            putBoolean(KEY_DL_COVER_LYRICS, prefs.downloadCoverLyrics)
            putString(KEY_DL_FAILURE, prefs.downloadFailurePolicy.value)
            putBoolean(KEY_DL_WIFI_ONLY, prefs.downloadWifiOnly)
            putBoolean(KEY_STATS_RECORDING, prefs.statsRecording)
            putBoolean(KEY_STATS_CLOUD, prefs.statsCloudSync)
            putBoolean(KEY_SOURCE_FAILOVER, prefs.sourceFailover)
            putBoolean(KEY_SOURCE_SMART, prefs.sourceSmartSwitch)
            putBoolean(KEY_SOURCE_FASTEST, prefs.sourceFastest)
        }?.apply()
    }
}
