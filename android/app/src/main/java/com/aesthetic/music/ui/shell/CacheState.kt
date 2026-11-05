package com.aesthetic.music.ui.shell

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aesthetic.music.player.AudioCacheService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地缓存信息（Profile/Downloads/CacheList 共用），由 [rememberCacheState] 创建。
 */
@Stable
class CacheState {
    var songs by mutableStateOf<List<AudioCacheService.CachedSong>>(emptyList())
        internal set

    private var refresher: () -> Unit = {}

    /** 重新读取缓存信息（IO + 回主线程） */
    fun refresh() = refresher()

    internal fun bindRefresher(fn: () -> Unit) {
        refresher = fn
    }
}

/** 创建并绑定缓存信息读取逻辑，并在首次组合时自动拉取一次。 */
@Composable
fun rememberCacheState(context: Context, scope: CoroutineScope): CacheState {
    val state = remember(context) { CacheState() }
    state.bindRefresher {
        scope.launch(Dispatchers.IO) {
            val songs = AudioCacheService.listCached(context)
            withContext(Dispatchers.Main) {
                state.songs = songs
            }
        }
    }
    LaunchedEffect(state) { state.refresh() }
    return state
}
