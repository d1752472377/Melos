package com.aesthetic.music.data.settings

/**
 * 全局一次性提示通道（给没有 UI 上下文的底层用）。
 *
 * 场景：缓存/预加载发生在 [com.aesthetic.music.player.AudioCacheService] 与
 * `PlayerManager` 里，它们既不是 Composable 也拿不到 ViewModel，
 * 但"下载失败要提示我"这类策略必须能提示到用户。
 * MainViewModel 在创建时注册 sink（走全局 Snackbar），未注册时静默丢弃。
 */
object UserNotice {

    @Volatile
    private var sink: ((String) -> Unit)? = null

    fun register(sink: (String) -> Unit) {
        this.sink = sink
    }

    fun post(message: String) {
        if (message.isBlank()) return
        runCatching { sink?.invoke(message) }
    }
}
