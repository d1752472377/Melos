package com.aesthetic.music

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import com.aesthetic.music.data.settings.FavoritePlaylistStore
import com.aesthetic.music.data.settings.FavoriteStore
import com.aesthetic.music.data.settings.HistoryStore
import com.aesthetic.music.data.settings.PlayerSessionStore
import com.aesthetic.music.data.settings.PlaylistStore
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.UserStore
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.service.CrashHandler
import com.aesthetic.music.ui.AppShell
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.theme.AestheticMusicTheme
import com.aesthetic.music.ui.theme.applyThemeMode

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // 用户拒绝只是收不到系统媒体通知
    }

    /** JankStats 句柄：onResume 开启采集、onPause 关闭 */
    private var jankStats: JankStats? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHighRefreshRate()

        SettingsStore.init(this)
        FavoriteStore.init(this)
        FavoritePlaylistStore.init(this)
        HistoryStore.init(this)
        UserStore.init(this)
        com.aesthetic.music.data.settings.SyncServerStore.init(this)
        com.aesthetic.music.data.settings.ScriptCatalogStore.init(this)
        com.aesthetic.music.data.userapi.UserApiEngine.init(this)
        PlaylistStore.init(this)
        StatsStore.init(this)
        PlayerSessionStore.init(this)
        CrashHandler.init(this)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        installJankMonitor()

        setContent {
            applyThemeMode(SettingsStore.getThemeMode(), isSystemInDarkTheme())
            AestheticMusicTheme {
                val viewModel: MainViewModel = viewModel()
                val playerManager = PlayerManager.get(this)
                AppShell(viewModel = viewModel, playerManager = playerManager)
            }
        }
    }

    /** 强制开启系统最高刷新率模式 */
    private fun setHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }

            maxMode?.let { mode ->
                val params = window.attributes
                params.preferredDisplayModeId = mode.modeId
                params.preferredRefreshRate = mode.refreshRate // 优先请求最高刷新率 (如 120Hz)
                window.attributes = params
            }
        } else {
            @Suppress("DEPRECATION")
            val modes = window.windowManager.defaultDisplay.supportedModes
            val maxMode = modes.maxByOrNull { it.refreshRate }
            maxMode?.let { mode ->
                val params = window.attributes
                params.preferredDisplayModeId = mode.modeId
                window.attributes = params
            }
        }
    }

    /**
     * 集成 JankStats：采集真实帧耗时，掉帧（超出预估刷新周期）写入 logcat（tag=JankStats），
     * 用于量化优化前后的卡顿变化。监听回调在后台线程执行，Log 调用线程安全。
     */
    private fun installJankMonitor() {
        try {
            jankStats = JankStats.createAndTrack(
                window,
                object : JankStats.OnFrameListener {
                    override fun onFrame(frameData: FrameData) {
                        if (frameData.isJank) {
                            Log.w(
                                "JankStats",
                                "janky frame ${frameData.frameDurationUiNanos / 1_000_000f}ms states=${frameData.states}",
                            )
                        }
                    }
                },
            )
        } catch (_: Exception) {
            // 监控失败不影响任何功能
        }
    }

    override fun onResume() {
        super.onResume()
        // 从其它应用/系统设置返回后，部分 ROM 会重置首选显示模式：
        // 每次回前台重新应用一次最高刷新率，防止滑动中悄悄降频
        setHighRefreshRate()
        jankStats?.isTrackingEnabled = true
    }

    override fun onPause() {
        jankStats?.isTrackingEnabled = false
        super.onPause()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}