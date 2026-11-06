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

/**
 * 设置页。
 *
 * 本页只保留「常用设置 / 播放与音效 / 关于」：
 * 「插件管理」「账号与同步」在个人页都有入口，重复入口已移除；
 * 「桌面悬浮胶囊」已整体删除。
 */
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
        // 设置项各自独立成页：主页只做入口，改设置不再需要一页滚到底
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


/**
 * 设置子页面（主题与外观 / 音质 / 播放 / 歌词 / 下载 / 缓存）的目的地。
 *
 * 这些页面集中在一个文件：它们的职责都是"绑定 [SettingsController] + 返回"，
 * 每个页面单独一个文件只会制造一份份同样的样板。页面本身在 ui/settings/ 下各自独立。
 */

/** 主题与外观 */
@Composable
fun SettingsAppearanceDestination(
    settings: SettingsController,
    nav: ShellNav,
    onPageBackground: (Color?, Boolean) -> Unit,
) {
    // 选「自动」时需要立刻按当前系统深浅应用一次，否则要等系统切换才生效
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

/** 音质 */
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

/** 播放设置 */
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

/** 歌词显示 */
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

/** 下载设置 */
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

/** 缓存管理（占用统计 + 分类清理 + 抽屉明细） */
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
    // 待确认的清理动作（null = 没有弹窗）
    var pendingClear by remember { mutableStateOf<CacheKind?>(null) }

    CacheManageScreen(
        breakdown = manage.breakdown,
        isScanning = manage.isScanning,
        // 用本次扫描得到的歌曲数（与总占用同源），避免与列表页的计数不一致
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
            // 用户调小上限后立即按新上限清理一次
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
