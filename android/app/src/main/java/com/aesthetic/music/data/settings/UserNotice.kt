package com.aesthetic.music.data.settings

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
