package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.music.player.AudioCacheService
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.ui.cache.CacheListScreen
import com.aesthetic.music.ui.cache.DownloadsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CacheListDestination(
    cacheState: CacheState,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { cacheState.refresh() }

    CacheListScreen(
        songs = cacheState.songs,
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

@Composable
fun DownloadsDestination(
    nav: ShellNav,
    actions: ShellActions,
    playerManager: PlayerManager,
    cacheState: CacheState,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val playerState by playerManager.state.collectAsState()
    DownloadsScreen(
        songs = cacheState.songs,
        currentSong = playerState.currentSong,
        onBack = nav.popBack,
        onPlaySong = { song, list -> actions.playSong(song, list) },
        onRemoveOne = { path ->
            scope.launch(Dispatchers.IO) {
                AudioCacheService.removeCacheByPath(context, path)
                withContext(Dispatchers.Main) { cacheState.refresh() }
            }
        },
    )
}
