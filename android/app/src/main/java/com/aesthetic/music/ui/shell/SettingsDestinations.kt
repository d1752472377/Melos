package com.aesthetic.music.ui.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.settings.AppearanceSettingsScreen
import com.aesthetic.music.ui.settings.CacheClearConfirmDialog
import com.aesthetic.music.ui.settings.CacheManageScreen
import com.aesthetic.music.ui.settings.DownloadSettingsScreen
import com.aesthetic.music.ui.settings.LyricSettingsScreen
import com.aesthetic.music.ui.settings.PlaybackSettingsScreen
import com.aesthetic.music.ui.settings.QualitySettingsScreen
import com.aesthetic.music.ui.settings.SettingsController
import com.aesthetic.music.ui.settings.SettingsPage
import com.aesthetic.music.ui.settings.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsDestination(
    settings: SettingsController,
    isCheckingUpdate: Boolean,
    onCheckUpdate: (onResult: (String) -> Unit) -> Unit,
    nav: ShellNav,
) {
    val themeMode by settings.themeMode.collectAsState()
    val qualityLevel by settings.quality.collectAsState()
    val userPrefs by settings.prefs.collectAsState()

    SettingsScreen(
        themeMode = themeMode,
        quality = qualityLevel,
        userPrefs = userPrefs,
        onBack = nav.popBack,
        onOpenPage = { page ->
            nav.navigate(
                when (page) {
                    SettingsPage.APPEARANCE -> ScreenRoute.SettingsAppearance
                    SettingsPage.QUALITY -> ScreenRoute.SettingsQuality
                    SettingsPage.PLAYBACK -> ScreenRoute.SettingsPlayback
                    SettingsPage.LYRICS -> ScreenRoute.SettingsLyrics
                    SettingsPage.DOWNLOAD -> ScreenRoute.SettingsDownload
                    SettingsPage.CACHE -> ScreenRoute.SettingsCache
                }.route
            )
        },
        onCheckUpdate = onCheckUpdate,
        isCheckingUpdate = isCheckingUpdate,
    )
}

@Composable
fun SettingsAppearanceDestination(
    settings: SettingsController,
    nav: ShellNav,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val themeMode by settings.themeMode.collectAsState()
    val userPrefs by settings.prefs.collectAsState()
    AppearanceSettingsScreen(
        themeMode = themeMode,
        statusBarMode = userPrefs.statusBarMode,
        onBack = {
            onPageBackground(null, false)
            nav.popBack()
        },
        onThemeModeChange = { mode -> settings.setThemeMode(mode, systemDark) },
        onStatusBarModeChange = settings::setStatusBarMode,
    )
}

@Composable
fun SettingsQualityDestination(
    settings: SettingsController,
    nav: ShellNav,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    val quality by settings.quality.collectAsState()
    QualitySettingsScreen(
        quality = quality,
        onBack = {
            onPageBackground(null, false)
            nav.popBack()
        },
        onQualityChange = settings::setQuality,
    )
}

@Composable
fun SettingsPlaybackDestination(
    settings: SettingsController,
    nav: ShellNav,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    val prefs by settings.prefs.collectAsState()
    PlaybackSettingsScreen(
        prefs = prefs,
        onBack = {
            onPageBackground(null, false)
            nav.popBack()
        },
        onPreloadNextChange = settings::setPreloadNext,
        onCrossFadeChange = settings::setCrossFade,
        onResumeAfterInterruptChange = settings::setResumeAfterInterrupt,
        onMixWithOthersChange = settings::setMixWithOthers,
    )
}

@Composable
fun SettingsLyricsDestination(
    settings: SettingsController,
    nav: ShellNav,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    val prefs by settings.prefs.collectAsState()
    LyricSettingsScreen(
        prefs = prefs,
        onBack = {
            onPageBackground(null, false)
            nav.popBack()
        },
        onTranslationChange = settings::setLyricTranslation,
        onRomanizationChange = settings::setLyricRomanization,
        onOnlineChange = settings::setLyricOnline,
        onLocalChange = settings::setLyricLocal,
    )
}

@Composable
fun SettingsDownloadDestination(
    settings: SettingsController,
    nav: ShellNav,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    val prefs by settings.prefs.collectAsState()
    DownloadSettingsScreen(
        prefs = prefs,
        onBack = {
            onPageBackground(null, false)
            nav.popBack()
        },
        onWhilePlayingChange = settings::setDownloadWhilePlaying,
        onNamingChange = settings::setDownloadNaming,
        onCoverLyricsChange = settings::setDownloadCoverLyrics,
        onFailurePolicyChange = settings::setDownloadFailurePolicy,
        onWifiOnlyChange = settings::setDownloadWifiOnly,
    )
}

@Composable
fun SettingsCacheDestination(
    settings: SettingsController,
    nav: ShellNav,
    cacheState: CacheState,
    scope: CoroutineScope,
    onPageBackground: (Color?, Boolean) -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val prefs by settings.prefs.collectAsState()
    val manage = rememberCacheManageState(context, scope)
    var pendingClear by remember { mutableStateOf<CacheKind?>(null) }

    CacheManageScreen(
        breakdown = manage.breakdown,
        isScanning = manage.isScanning,
        cachedSongCount = manage.cachedSongCount,
        showDetail = manage.showDetail,
        onBack = {
            onPageBackground(null, false)
            nav.popBack()
        },
        onOpenDetail = manage::openDetail,
        onCloseDetail = manage::closeDetail,
        onRefresh = manage::refresh,
        onClearMusic = { pendingClear = CacheKind.MUSIC },
        onClearImages = { pendingClear = CacheKind.IMAGES },
        onClearTemp = { pendingClear = CacheKind.TEMP },
        onClearAll = { pendingClear = CacheKind.ALL },
        onOpenCachedSongs = { nav.navigate(ScreenRoute.CacheList.route) },
        cacheLimitBytes = prefs.cacheLimitBytes,
        onCacheLimitChange = { bytes ->
            settings.setCacheLimitBytes(bytes)
            scope.launch(Dispatchers.IO) {
                com.aesthetic.music.player.AudioCacheService.enforceCacheLimit(context)
            }
        },
    )

    val kind = pendingClear
    if (kind != null) {
        val (title, message) = when (kind) {
            CacheKind.MUSIC -> "清理下载音乐" to "将删除全部边听边下产生的音频文件（含索引记录）。清理后再次播放需要重新联网获取。"
            CacheKind.IMAGES -> "清理图片缓存" to "将删除专辑封面等图片缓存。清理后浏览时会重新下载，不影响本地文件。"
            CacheKind.TEMP -> "清理临时文件" to "将删除下载分片与其它临时数据。此操作不影响已缓存的歌曲。"
            CacheKind.ALL -> "清理全部缓存" to "将清空下载音乐、图片缓存与临时文件，共 ${manage.breakdown?.total?.let { com.aesthetic.music.data.cache.CacheCenter.formatSize(it) } ?: "0 B"}。"
        }
        CacheClearConfirmDialog(
            title = title,
            message = message,
            onConfirm = {
                val target = kind
                pendingClear = null
                manage.clear(target)
                onToast("已清理：$title")
                cacheState.refresh()
            },
            onDismiss = { pendingClear = null },
        )
    }
}
