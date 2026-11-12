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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.AppShell
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.account.AuthScreen
import com.aesthetic.music.ui.theme.AestheticMusicTheme
import com.aesthetic.music.ui.theme.applyThemeMode

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
    }

    private var jankStats: JankStats? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHighRefreshRate()
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        installJankMonitor()

        setContent {
            applyThemeMode(SettingsStore.getThemeMode(), isSystemInDarkTheme())
            AestheticMusicTheme {
                val viewModel: MainViewModel = viewModel()
                val isLoggedIn by viewModel.auth.isLoggedIn.collectAsState()

                if (!isLoggedIn) {
                    AuthScreen(auth = viewModel.auth)
                } else {
                    val playerManager = PlayerManager.get(this)
                    AppShell(viewModel = viewModel, playerManager = playerManager)
                }
            }
        }
    }

    private fun setHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }

            maxMode?.let { mode ->
                val params = window.attributes
                params.preferredDisplayModeId = mode.modeId
                params.preferredRefreshRate = mode.refreshRate
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

    private fun installJankMonitor() {
        try {
            jankStats = JankStats.createAndTrack(
                window,
                object : JankStats.OnFrameListener {
                    override fun onFrame(volatileFrameData: FrameData) {
                        if (volatileFrameData.isJank) {
                            Log.w(
                                "JankStats",
                                "janky frame ${volatileFrameData.frameDurationUiNanos / 1_000_000f}ms states=${volatileFrameData.states}",
                            )
                        }
                    }
                },
            )
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
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