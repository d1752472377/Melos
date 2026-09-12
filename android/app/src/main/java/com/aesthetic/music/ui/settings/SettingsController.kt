package com.aesthetic.music.ui.settings

import com.aesthetic.music.data.settings.DownloadFailurePolicy
import com.aesthetic.music.data.settings.DownloadNaming
import com.aesthetic.music.data.settings.MediaCardLyricPosition
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.StatusBarMode
import com.aesthetic.music.data.settings.StatsStore
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


    fun setMediaCardShowLyric(enabled: Boolean) =
        update(_prefs.value.copy(mediaCardShowLyric = enabled))

    fun setMediaCardLyricPosition(position: MediaCardLyricPosition) =
        update(_prefs.value.copy(mediaCardLyricPosition = position))

    fun setMediaCardCover(enabled: Boolean) = update(_prefs.value.copy(mediaCardCover = enabled))
    fun setMediaCardAlbum(enabled: Boolean) = update(_prefs.value.copy(mediaCardAlbum = enabled))
    fun setMediaCardProgress(enabled: Boolean) = update(_prefs.value.copy(mediaCardProgress = enabled))
    fun setMediaCardColorized(enabled: Boolean) =
        update(_prefs.value.copy(mediaCardColorized = enabled))

    fun setMediaCardLiveUpdate(enabled: Boolean) =
        update(_prefs.value.copy(mediaCardLiveUpdate = enabled))

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
