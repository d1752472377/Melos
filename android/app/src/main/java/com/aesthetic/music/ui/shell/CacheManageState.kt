package com.aesthetic.music.ui.shell

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aesthetic.music.data.cache.CacheCenter
import com.aesthetic.music.service.LogReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CacheManageState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var breakdown by mutableStateOf<CacheCenter.Breakdown?>(null)
        private set
    var isScanning by mutableStateOf(false)
        private set
    var cachedSongCount by mutableStateOf(0)
        private set
    var showDetail by mutableStateOf(false)
        private set

    fun refresh() {
        if (isScanning) return
        isScanning = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { CacheCenter.scan(context) }
            breakdown = result
            cachedSongCount = result.songCount
            isScanning = false
        }
    }

    fun openDetail() {
        showDetail = true
        refresh()
    }

    fun closeDetail() {
        showDetail = false
    }

    fun clear(kind: CacheKind) {
        LogReporter.breadcrumb("Cache", "clear kind=$kind")
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    when (kind) {
                        CacheKind.MUSIC -> CacheCenter.clearMusic(context)
                        CacheKind.IMAGES -> CacheCenter.clearImages(context)
                        CacheKind.TEMP -> CacheCenter.clearTemp(context)
                        CacheKind.ALL -> CacheCenter.clearAll(context)
                    }
                }
            } catch (e: Exception) {
                LogReporter.w("Cache", "清理缓存失败 kind=$kind", e)
            }
            breakdown = withContext(Dispatchers.IO) { CacheCenter.scan(context) }
            cachedSongCount = breakdown?.songCount ?: 0
        }
    }
}

enum class CacheKind { MUSIC, IMAGES, TEMP, ALL }

@Composable
fun rememberCacheManageState(context: Context, scope: CoroutineScope): CacheManageState {
    val state = remember { CacheManageState(context.applicationContext, scope) }
    LaunchedEffect(Unit) { state.refresh() }
    return state
}
