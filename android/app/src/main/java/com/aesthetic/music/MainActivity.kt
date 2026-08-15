package com.aesthetic.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        FavoriteStore.init(this)
        HistoryStore.init(this)
        UserStore.init(this)
        PlaylistStore.init(this)
        StatsStore.init(this)
        PlayerSessionStore.init(this)
        CrashHandler.init(this)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            // 应用三档主题模式（跟随系统/浅色/深色）
            applyThemeMode(SettingsStore.getThemeMode(), isSystemInDarkTheme())
            AestheticMusicTheme {
                val viewModel: MainViewModel = viewModel()
                val playerManager = PlayerManager.get(this)
                AppShell(viewModel = viewModel, playerManager = playerManager)
            }
        }
    }

    /** Android 13+ 请求通知权限（否则系统媒体通知不显示） */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}
