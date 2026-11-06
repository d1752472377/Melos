package com.aesthetic.music

import android.app.Application
import com.aesthetic.music.data.settings.FavoritePlaylistStore
import com.aesthetic.music.data.settings.FavoriteStore
import com.aesthetic.music.data.settings.GuessHistoryStore
import com.aesthetic.music.data.settings.HistoryStore
import com.aesthetic.music.data.settings.PlayerSessionStore
import com.aesthetic.music.data.settings.PlaylistCoverStore
import com.aesthetic.music.data.settings.PlaylistStore
import com.aesthetic.music.data.settings.RecentSearchStore
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.data.settings.UserStore
import com.aesthetic.music.data.source.SourceManager
import com.aesthetic.music.data.userapi.UserApiEngine
import com.aesthetic.music.service.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MelosApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        CrashHandler.init(this)
        com.aesthetic.music.service.LogReporter.init(this)
        SettingsStore.init(this)
        FavoriteStore.init(this)
        FavoritePlaylistStore.init(this)
        HistoryStore.init(this)
        RecentSearchStore.init(this)
        UserStore.init(this)
        SyncServerStore.init(this)
        com.aesthetic.music.data.sync.SyncStateStore.init(this)
        SourceManager.init(this)
        UserApiEngine.init(this)
        PlaylistStore.init(this)
        PlaylistCoverStore.init(this)
        StatsStore.init(this)
        PlayerSessionStore.init(this)
        GuessHistoryStore.init(this)

        // 注意 UserApiEngine 自身不再自动加载脚本（多插件下"加载哪个"由 SourceManager 决定）。
        appScope.launch { runCatching { SourceManager.preloadActive() } }
    }

    companion object {
        lateinit var instance: MelosApplication
            private set

        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
