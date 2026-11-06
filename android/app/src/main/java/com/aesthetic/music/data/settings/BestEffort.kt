package com.aesthetic.music.data.settings

import android.util.Log

internal inline fun bestEffort(tag: String, what: String, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        Log.w(tag, "$what 失败（已降级为忽略本次写入）", e)
    }
}
