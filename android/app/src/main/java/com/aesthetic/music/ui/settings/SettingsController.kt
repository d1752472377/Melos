package com.aesthetic.music.ui.settings

import com.aesthetic.music.data.settings.BackgroundBlurMode
import com.aesthetic.music.data.settings.DownloadFailurePolicy
import com.aesthetic.music.data.settings.DownloadNaming
import com.aesthetic.music.data.settings.LyricBlurMode
import com.aesthetic.music.data.settings.LyricFontSize
import com.aesthetic.music.data.settings.PlayerTheme
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.StatusBarMode
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.data.settings.UserPrefs
import com.aesthetic.music.ui.theme.applyThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置项控制器（主题与外观 / 播放 / 歌词 / 下载）。
 *
 * 与 `LibraryController` 等控制器的差别：这里没有网络与列表数据，只有一组偏好，
 * 因此不考虑并发与失败回滚——写入是"内存态 + SharedPreferences 一次 edit"，
 * 写完立刻通过 [onApplied] 让播放器按新设置生效（不需要重启 App）。
 *
 * 注：原「媒体卡片」一组设置（收藏按钮 / 封面 / 信息行 / 进度 / 歌词 / 取色 / 实时活动）
 * 连同设置页一起删除，见下方对应注释。
 */
class SettingsController(
    /** 设置变更后的即时生效钩子（播放器参数等） */
    private val onApplied: (UserPrefs) -> Unit = {},
) {

    private val _prefs = MutableStateFlow(SettingsStore.readPrefs())
    val prefs: StateFlow<UserPrefs> = _prefs.asStateFlow()

    private val _themeMode = MutableStateFlow(SettingsStore.getThemeMode())
    val themeMode = _themeMode.asStateFlow()

    private val _quality = MutableStateFlow(SettingsStore.getQuality())
    val quality = _quality.asStateFlow()

    /** 主题模式：立即落盘并按当前系统深浅应用一次 */
    fun setThemeMode(mode: ThemeMode, systemDark: Boolean) {
        _themeMode.value = mode
        SettingsStore.setThemeMode(mode)
        applyThemeMode(mode, systemDark)
    }

    fun setQuality(level: QualityLevel) {
        _quality.value = level
        SettingsStore.setQuality(level)
    }

    private fun update(next: UserPrefs) {
        if (next == _prefs.value) return
        _prefs.value = next
        SettingsStore.writePrefs(next)
        onApplied(next)
    }

    // ===== 主题与外观 =====
    fun setStatusBarMode(mode: StatusBarMode) = update(_prefs.value.copy(statusBarMode = mode))

    // ===== 播放页外观 =====
    fun setPlayerTheme(theme: PlayerTheme) = update(_prefs.value.copy(playerTheme = theme))
    fun setLyricFontSize(size: LyricFontSize) = update(_prefs.value.copy(lyricFontSize = size))
    fun setLyricBlurMode(mode: LyricBlurMode) = update(_prefs.value.copy(lyricBlurMode = mode))
    fun setBackgroundBlurMode(mode: BackgroundBlurMode) =
        update(_prefs.value.copy(backgroundBlurMode = mode))

    // ===== 播放 =====
    fun setPreloadNext(enabled: Boolean) = update(_prefs.value.copy(preloadNext = enabled))
    fun setCrossFade(enabled: Boolean) = update(_prefs.value.copy(crossFade = enabled))
    fun setResumeAfterInterrupt(enabled: Boolean) =
        update(_prefs.value.copy(resumeAfterInterrupt = enabled))

    fun setMixWithOthers(enabled: Boolean) = update(_prefs.value.copy(mixWithOthers = enabled))

    // ===== 歌词 =====
    fun setLyricTranslation(enabled: Boolean) = update(_prefs.value.copy(lyricTranslation = enabled))
    fun setLyricRomanization(enabled: Boolean) =
        update(_prefs.value.copy(lyricRomanization = enabled))

    fun setLyricOnline(enabled: Boolean) = update(_prefs.value.copy(lyricOnline = enabled))
    fun setLyricLocal(enabled: Boolean) = update(_prefs.value.copy(lyricLocal = enabled))

    // ===== 媒体卡片 =====
    // 整组设置已删除：卡片的歌词行/进度/收藏按钮/取色在 Android 13+ 由系统媒体卡片按会话状态绘制，
    // 通知里的那一份多数机型不看，开关只在设置页里"看起来有用"。控制按钮改走 media3 官方通道后
    // 卡片内容固定为「封面 + 歌名 + 歌手 + 专辑·音质·音源」，没有需要用户调的东西。

    // ===== 下载与缓存 =====
    fun setCacheLimitBytes(bytes: Long) = update(_prefs.value.copy(cacheLimitBytes = bytes))

    fun setDownloadWhilePlaying(enabled: Boolean) =
        update(_prefs.value.copy(downloadWhilePlaying = enabled))

    fun setDownloadNaming(naming: DownloadNaming) = update(_prefs.value.copy(downloadNaming = naming))
    fun setDownloadCoverLyrics(enabled: Boolean) =
        update(_prefs.value.copy(downloadCoverLyrics = enabled))

    fun setDownloadFailurePolicy(policy: DownloadFailurePolicy) =
        update(_prefs.value.copy(downloadFailurePolicy = policy))

    fun setDownloadWifiOnly(enabled: Boolean) =
        update(_prefs.value.copy(downloadWifiOnly = enabled))

    // ===== 听歌统计 =====

    /**
     * 是否记录听歌数据。
     *
     * 关掉后除了写偏好，还会让 [com.aesthetic.music.data.settings.StatsStore] 立即失效
     * 它的开关缓存，否则最坏情况会多记 2 秒的数据（统计开关"关不干净"很容易被当成隐私问题）。
     */
    fun setStatsRecording(enabled: Boolean) {
        update(_prefs.value.copy(statsRecording = enabled))
        StatsStore.invalidateRecordingCache()
    }

    fun setStatsCloudSync(enabled: Boolean) =
        update(_prefs.value.copy(statsCloudSync = enabled))

    // ===== 音源策略（插件管理页） =====

    /** 失败换源：解析失败时自动换下一个音源 */
    fun setSourceFailover(enabled: Boolean) =
        update(_prefs.value.copy(sourceFailover = enabled))

    /** 智能切换：源被判定为失败/偏慢时不等失败直接换 */
    fun setSourceSmartSwitch(enabled: Boolean) =
        update(_prefs.value.copy(sourceSmartSwitch = enabled))

    /** 自动按照最快的进行使用：按实测速度排序取最快 */
    fun setSourceFastest(enabled: Boolean) =
        update(_prefs.value.copy(sourceFastest = enabled))
}
