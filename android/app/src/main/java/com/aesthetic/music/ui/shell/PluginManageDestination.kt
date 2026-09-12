package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.source.PluginManageScreen
import com.aesthetic.music.ui.source.SourceActions

/** 插件管理页（音源导入 / 检测 / 切换策略） */
@Composable
fun PluginManageDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    onToast: (String) -> Unit,
) {
    val plugins by viewModel.sourcePlugins.collectAsState()
    val probes by viewModel.sourceProbes.collectAsState()
    val targets by viewModel.sourceTargets.collectAsState()
    val detecting by viewModel.sourceDetecting.collectAsState()
    val detectProgress by viewModel.sourceDetectProgress.collectAsState()
    val lastDetectAt by viewModel.sourceLastDetectAt.collectAsState()
    val activeId by viewModel.activeSourceId.collectAsState()
    val loadedPluginId by viewModel.loadedSourceId.collectAsState()
    val engineReady by viewModel.sourceEngineReady.collectAsState()
    val engineError by viewModel.sourceEngineError.collectAsState()
    val userPrefs by viewModel.settings.prefs.collectAsState()

    // 进入页面时重读注册表：启动迁移、其它入口的导入都会让内存里的列表过期
    LaunchedEffect(Unit) { viewModel.refreshSources() }

    val actions = remember(viewModel) {
        SourceActions(
            importFromContent = viewModel::importSourceFromContent,
            importFromUrl = viewModel::importSourceFromUrl,
            setEnable = viewModel::setSourceEnable,
            setPlatform = viewModel::setSourcePlatform,
            activate = viewModel::activateSource,
            reload = viewModel::reloadActiveSource,
            unload = viewModel::unloadSource,
            remove = viewModel::removeSource,
            detectAll = viewModel::detectSources,
            detectOne = viewModel::detectSourceTarget,
            setSourceFailover = viewModel::setSourceFailover,
            setSourceSmartSwitch = viewModel::setSourceSmartSwitch,
            setSourceFastest = viewModel::setSourceFastest,
        )
    }

    PluginManageScreen(
        plugins = plugins,
        probes = probes,
        targets = targets,
        detecting = detecting,
        detectProgress = detectProgress,
        lastDetectAt = lastDetectAt,
        activeId = activeId,
        loadedPluginId = loadedPluginId,
        engineReady = engineReady,
        engineError = engineError,
        userPrefs = userPrefs,
        actions = actions,
        onToast = onToast,
        onBack = nav.popBack,
    )
}
