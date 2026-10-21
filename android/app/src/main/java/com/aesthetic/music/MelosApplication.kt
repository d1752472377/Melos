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
        RecentSearchStore.init(this)
        UserStore.init(this)
        SyncServerStore.init(this)
        com.aesthetic.music.data.sync.SyncStateStore.init(this)
        // 音源插件注册表与检测统计（取代旧版"唯一脚本"存储 ScriptCatalogStore）
        SourceManager.init(this)
        UserApiEngine.init(this)
        PlaylistStore.init(this)
        PlaylistCoverStore.init(this)
        StatsStore.init(this)
        PlayerSessionStore.init(this)
        // 「猜你喜欢」已推荐记忆：让「换一批」真的换一批（见 GuessHistoryStore 类注释）
        GuessHistoryStore.init(this)

        // 启动预热当前指定音源：沙箱编译放在后台，避免第一次播放时才付这份代价。
        // 注意 UserApiEngine 自身不再自动加载脚本（多插件下"加载哪个"由 SourceManager 决定）。
        appScope.launch { runCatching { SourceManager.preloadActive() } }
    }

    companion object {
        lateinit var instance: MelosApplication
            private set

        /** 应用级后台作用域：只用于启动预热这类"失败也无所谓"的任务 */
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
