package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.cache.CacheListScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 缓存列表页 */
@Composable
fun CacheListDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    cacheState: CacheState,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    // 进页刷新：播放产生的新缓存在这里要能立刻看到
    LaunchedEffect(Unit) { cacheState.refresh() }

    CacheListScreen(
        songs = cacheState.songs,
        onBack = {
            viewModel.updatePageBackground(null, false)
            nav.popBack()
        },
        onRemoveOne = { path ->
            scope.launch(Dispatchers.IO) {
                AudioCacheService.removeCacheByPath(context, path)
                withContext(Dispatchers.Main) { cacheState.refresh() }
            }
        },
        onClearAll = {
            scope.launch(Dispatchers.IO) {
                AudioCacheService.clearCache(context)
                withContext(Dispatchers.Main) { cacheState.refresh() }
            }
        },
    )
}
