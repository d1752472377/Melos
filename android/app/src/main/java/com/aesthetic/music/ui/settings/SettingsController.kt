package com.aesthetic.music.ui.settings

import com.aesthetic.music.data.settings.DownloadFailurePolicy
import com.aesthetic.music.data.settings.DownloadNaming
import com.aesthetic.music.data.settings.MediaCardButtonPosition
import com.aesthetic.music.data.settings.MediaCardLyricPosition
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.StatusBarMode
import com.aesthetic.music.data.settings.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置项控制器（主题与外观 / 播放 / 歌词 / 媒体卡片 / 下载）。
 *
 * 与 `LibraryController` 等控制器的差别：这里没有网络与列表数据，只有一组偏好，
 * 因此不考虑并发与失败回滚——写入是"内存态 + SharedPreferences 一次 edit"，
 * 写完立刻通过 [onApplied] 让播放器与媒体卡片按新设置生效（不需要重启 App）。
 */
class SettingsController(
    /** 设置变更后的即时生效钩子（播放器参数、通知卡片刷新） */
    private val onApplied: (UserPrefs) -> Unit = {},
) {

    private val _prefs = MutableStateFlow(SettingsStore.readPrefs())
    val prefs: StateFlow<UserPrefs> = _prefs.asStateFlow()

    private fun update(next: UserPrefs) {
        if (next == _prefs.value) return
        _prefs.value = next
        SettingsStore.writePrefs(next)
        onApplied(next)
    }

    // ===== 主题与外观 =====
    fun setStatusBarMode(mode: StatusBarMode) = update(_prefs.value.copy(statusBarMode = mode))

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
    fun setMediaCardFavorite(enabled: Boolean) =
        update(_prefs.value.copy(mediaCardFavorite = enabled))

    fun setMediaCardButtonPosition(position: MediaCardButtonPosition) =
        update(_prefs.value.copy(mediaCardButtonPosition = position))

    fun setMediaCardShowLyric(enabled: Boolean) =
        update(_prefs.value.copy(mediaCardShowLyric = enabled))

    fun setMediaCardLyricPosition(position: MediaCardLyricPosition) =
        update(_prefs.value.copy(mediaCardLyricPosition = position))

    // ===== 下载与缓存 =====
    fun setDownloadWhilePlaying(enabled: Boolean) =
        update(_prefs.value.copy(downloadWhilePlaying = enabled))

    fun setDownloadNaming(naming: DownloadNaming) = update(_prefs.value.copy(downloadNaming = naming))
    fun setDownloadCoverLyrics(enabled: Boolean) =
        update(_prefs.value.copy(downloadCoverLyrics = enabled))

    fun setDownloadFailurePolicy(policy: DownloadFailurePolicy) =
        update(_prefs.value.copy(downloadFailurePolicy = policy))

    fun setDownloadWifiOnly(enabled: Boolean) =
        update(_prefs.value.copy(downloadWifiOnly = enabled))
}
