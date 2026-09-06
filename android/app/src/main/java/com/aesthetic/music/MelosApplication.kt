package com.aesthetic.music

import android.app.Application
import com.aesthetic.music.data.settings.FavoritePlaylistStore
import com.aesthetic.music.data.settings.FavoriteStore
import com.aesthetic.music.data.settings.HistoryStore
import com.aesthetic.music.data.settings.PlayerSessionStore
import com.aesthetic.music.data.settings.PlaylistStore
import com.aesthetic.music.data.settings.ScriptCatalogStore
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.data.settings.UserStore
import com.aesthetic.music.data.userapi.UserApiEngine
import com.aesthetic.music.service.CrashHandler

/**
 * 全局应用入口 Application：
 * 统一在应用进程创建的第一时间完成全部持久化 Store、脚本引擎和全局崩溃日志的初始化。
 * 避免后台通过 PlaybackService / 广播接收器拉起时 MainActivity 未创建导致的空指针与状态缺失。
 */
class MelosApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 统一初始化全局持久化存储与底层服务
        CrashHandler.init(this)
        SettingsStore.init(this)
        FavoriteStore.init(this)
        FavoritePlaylistStore.init(this)
        HistoryStore.init(this)
        UserStore.init(this)
        SyncServerStore.init(this)
        ScriptCatalogStore.init(this)
        UserApiEngine.init(this)
        PlaylistStore.init(this)
        StatsStore.init(this)
        PlayerSessionStore.init(this)
    }

    companion object {
        lateinit var instance: MelosApplication
            private set
    }
}
