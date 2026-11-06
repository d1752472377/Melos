package com.aesthetic.music.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.UserPrefs

object DownloadPolicy {

    fun isUnmeteredNetwork(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (_: Exception) {
            // 判断不了就不拦（宁可多缓存一次，也不要因为权限/系统差异把播放缓存全禁掉）
            true
        }
    }

    fun allowed(context: Context, prefs: UserPrefs = SettingsStore.readPrefs(), manual: Boolean): Boolean =
        allowed(prefs, manual, unmetered = isUnmeteredNetwork(context))

    fun allowed(prefs: UserPrefs, manual: Boolean, unmetered: Boolean): Boolean {
        if (!manual && !prefs.downloadWhilePlaying) return false
        if (prefs.downloadWifiOnly && !unmetered) return false
        return true
    }

    fun blockedReason(prefs: UserPrefs = SettingsStore.readPrefs()): String = when {
        prefs.downloadWifiOnly -> "已开启「仅 WiFi 下载」，当前不是 WiFi 网络"
        else -> "已关闭「边听边下」，不会自动缓存"
    }
}
