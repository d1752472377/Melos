package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.aesthetic.music.ui.settings.SettingsController
import com.aesthetic.music.ui.source.PluginManageScreen
import com.aesthetic.music.ui.source.SourceActions
import com.aesthetic.music.ui.source.SourceController

/** 插件管理页（音源导入 / 检测 / 切换策略） */
@Composable
fun PluginManageDestination(
    sources: SourceController,
    settings: SettingsController,
    nav: ShellNav,
    onToast: (String) -> Unit,
) {
    val plugins by sources.plugins.collectAsState()
    val probes by sources.probes.collectAsState()
    val detecting by sources.detecting.collectAsState()
    val activeId by sources.activeId.collectAsState()
    val loadedPluginId by sources.loadedPluginId.collectAsState()
    val engineReady by sources.engineReady.collectAsState()
    val engineError by sources.engineError.collectAsState()
    val pluginDetect by sources.pluginDetect.collectAsState()
    val userPrefs by settings.prefs.collectAsState()

    // 进入页面时重读注册表：启动迁移、其它入口的导入都会让内存里的列表过期
    LaunchedEffect(Unit) { sources.refresh() }

    val actions = remember(sources, settings) {
        SourceActions(
            importFromContent = sources::importFromContent,
            importFromUri = sources::importFromUri,
            importFromUrl = sources::importFromUrl,
            setEnable = sources::setEnable,
            setPlatform = sources::setTargetPlatform,
            activate = sources::activate,
            reload = sources::reloadActive,
            unload = sources::unload,
            remove = sources::remove,
            refresh = sources::refresh,
            detectAll = sources::detectAll,
            detectOne = sources::detectOne,
            startPluginDetect = sources::startPluginDetect,
            clearPluginDetect = sources::clearPluginDetect,
            setSourceFailover = settings::setSourceFailover,
            setSourceSmartSwitch = settings::setSourceSmartSwitch,
            setSourceFastest = settings::setSourceFastest,
        )
    }

    PluginManageScreen(
        plugins = plugins,
        probes = probes,
        detecting = detecting,
        activeId = activeId,
        loadedPluginId = loadedPluginId,
        engineReady = engineReady,
        engineError = engineError,
        pluginDetect = pluginDetect,
        userPrefs = userPrefs,
        actions = actions,
        onToast = onToast,
        onBack = nav.popBack,
    )
}
