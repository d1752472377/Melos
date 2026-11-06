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

class SettingsController(
    private val onApplied: (UserPrefs) -> Unit = {},
) {

    private val _prefs = MutableStateFlow(SettingsStore.readPrefs())
    val prefs: StateFlow<UserPrefs> = _prefs.asStateFlow()

    private val _themeMode = MutableStateFlow(SettingsStore.getThemeMode())
    val themeMode = _themeMode.asStateFlow()

    private val _quality = MutableStateFlow(SettingsStore.getQuality())
    val quality = _quality.asStateFlow()

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

    fun setStatusBarMode(mode: StatusBarMode) = update(_prefs.value.copy(statusBarMode = mode))

    fun setPlayerTheme(theme: PlayerTheme) = update(_prefs.value.copy(playerTheme = theme))
    fun setLyricFontSize(size: LyricFontSize) = update(_prefs.value.copy(lyricFontSize = size))
    fun setLyricBlurMode(mode: LyricBlurMode) = update(_prefs.value.copy(lyricBlurMode = mode))
    fun setBackgroundBlurMode(mode: BackgroundBlurMode) =
        update(_prefs.value.copy(backgroundBlurMode = mode))

    fun setPreloadNext(enabled: Boolean) = update(_prefs.value.copy(preloadNext = enabled))
    fun setCrossFade(enabled: Boolean) = update(_prefs.value.copy(crossFade = enabled))
    fun setResumeAfterInterrupt(enabled: Boolean) =
        update(_prefs.value.copy(resumeAfterInterrupt = enabled))

    fun setMixWithOthers(enabled: Boolean) = update(_prefs.value.copy(mixWithOthers = enabled))

    fun setLyricTranslation(enabled: Boolean) = update(_prefs.value.copy(lyricTranslation = enabled))
    fun setLyricRomanization(enabled: Boolean) =
        update(_prefs.value.copy(lyricRomanization = enabled))

    fun setLyricOnline(enabled: Boolean) = update(_prefs.value.copy(lyricOnline = enabled))
    fun setLyricLocal(enabled: Boolean) = update(_prefs.value.copy(lyricLocal = enabled))

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

    fun setStatsRecording(enabled: Boolean) {
        update(_prefs.value.copy(statsRecording = enabled))
        StatsStore.invalidateRecordingCache()
    }

    fun setStatsCloudSync(enabled: Boolean) =
        update(_prefs.value.copy(statsCloudSync = enabled))

    fun setSourceFailover(enabled: Boolean) =
        update(_prefs.value.copy(sourceFailover = enabled))

    fun setSourceSmartSwitch(enabled: Boolean) =
        update(_prefs.value.copy(sourceSmartSwitch = enabled))

    fun setSourceFastest(enabled: Boolean) =
        update(_prefs.value.copy(sourceFastest = enabled))
}
