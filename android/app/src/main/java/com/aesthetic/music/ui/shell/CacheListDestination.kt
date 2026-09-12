package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.cache.CacheListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 缓存列表页 */
@Composable
fun CacheListDestination(
    viewModel: MainViewModel,
    nav: ShellNav,
    cacheState: CacheState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
