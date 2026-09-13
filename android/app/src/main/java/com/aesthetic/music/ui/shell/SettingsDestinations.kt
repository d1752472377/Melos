package com.aesthetic.music.ui.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.settings.AppearanceSettingsScreen
import com.aesthetic.music.ui.settings.CacheClearConfirmDialog
import com.aesthetic.music.ui.settings.CacheManageScreen
import com.aesthetic.music.ui.settings.DownloadSettingsScreen
import com.aesthetic.music.ui.settings.LyricSettingsScreen
import com.aesthetic.music.ui.settings.SettingsScreen
import com.aesthetic.music.ui.settings.PlaybackSettingsScreen
import com.aesthetic.music.ui.settings.QualitySettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * 设置子页面（主题与外观 / 音质 / 播放 / 歌词 / 下载 / 缓存 / 音源插件）的目的地。
 *
 * 这些页面集中在一个文件：它们的职责都是"绑定 MainViewModel 的 SettingsController + 返回"，
 * 每个页面单独一个文件只会制造一份份同样的样板。页面本身在 ui/settings/ 下各自独立。
 * （原「媒体卡片」页已删除，卡片没有可配置项。） */

/** 主题与外观 */
@Composable
fun SettingsAppearanceDestination(viewModel: MainViewModel, nav: ShellNav) {
    // 选「自动」时需要立刻按当前系统深浅应用一次，否则要等系统切换才生效
    val systemDark = isSystemInDarkTheme()
    val themeMode by viewModel.themeMode.collectAsState()
    val userPrefs by viewModel.settings.prefs.collectAsState()
    AppearanceSettingsScreen(
        themeMode = themeMode,
        statusBarMode = userPrefs.statusBarMode,
        onBack = {
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onThemeModeChange = { mode -> viewModel.setThemeMode(mode, systemDark) },
        onStatusBarModeChange = viewModel.settings::setStatusBarMode,
    )
}

/** 音质 */
@Composable
fun SettingsQualityDestination(viewModel: MainViewModel, nav: ShellNav) {
    val quality by viewModel.quality.collectAsState()
    QualitySettingsScreen(
        quality = quality,
        onBack = {
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onQualityChange = viewModel::setQuality,
    )
}

/** 播放设置 */
@Composable
fun SettingsPlaybackDestination(viewModel: MainViewModel, nav: ShellNav) {
    val prefs by viewModel.settings.prefs.collectAsState()
    PlaybackSettingsScreen(
        prefs = prefs,
        onBack = {
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onPreloadNextChange = viewModel.settings::setPreloadNext,
        onCrossFadeChange = viewModel.settings::setCrossFade,
        onResumeAfterInterruptChange = viewModel.settings::setResumeAfterInterrupt,
        onMixWithOthersChange = viewModel.settings::setMixWithOthers,
    )
}

/** 歌词显示 */
@Composable
fun SettingsLyricsDestination(viewModel: MainViewModel, nav: ShellNav) {
    val prefs by viewModel.settings.prefs.collectAsState()
    LyricSettingsScreen(
        prefs = prefs,
        onBack = {
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onTranslationChange = viewModel.settings::setLyricTranslation,
        onRomanizationChange = viewModel.settings::setLyricRomanization,
        onOnlineChange = viewModel.settings::setLyricOnline,
        onLocalChange = viewModel.settings::setLyricLocal,
    )
}

/** 下载设置 */
@Composable
fun SettingsDownloadDestination(viewModel: MainViewModel, nav: ShellNav) {
    val prefs by viewModel.settings.prefs.collectAsState()
    DownloadSettingsScreen(
        prefs = prefs,
        onBack = {
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onWhilePlayingChange = viewModel.settings::setDownloadWhilePlaying,
        onNamingChange = viewModel.settings::setDownloadNaming,
        onCoverLyricsChange = viewModel.settings::setDownloadCoverLyrics,
        onFailurePolicyChange = viewModel.settings::setDownloadFailurePolicy,
        onWifiOnlyChange = viewModel.settings::setDownloadWifiOnly,
    )
}

/** 缓存管理（占用统计 + 分类清理 + 抽屉明细） */
@Composable
fun SettingsCacheDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    cacheState: CacheState,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
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
            viewModel.updatePageBackground(null, false)
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
        onCacheLimitChange = { bytes ->
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
                viewModel.showToast("已清理：$title")
                cacheState.refresh()
            },
            onDismiss = { pendingClear = null },
        )
    }
}
