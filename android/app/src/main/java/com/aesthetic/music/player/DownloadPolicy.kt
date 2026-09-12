package com.aesthetic.music.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.UserPrefs

/**
 * 下载/缓存策略闸门：把「边听边下」「仅 WiFi」两条设置收在一处判断。
 *
 * 所有缓存入口（播放时后台缓存、预加载下一首、手动下载）都必须过这道闸，
 * 否则总有一条路径能绕过用户的省流设置。
 */
object DownloadPolicy {

    /** 当前是否处于 WiFi / 以太网（不限流量的网络） */
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

    /**
     * 是否允许缓存。
     *
     * @param manual true = 用户主动点的下载（不受「边听边下」开关限制，但仍受「仅 WiFi」限制）
     */
    fun allowed(context: Context, prefs: UserPrefs = SettingsStore.readPrefs(), manual: Boolean): Boolean {
        if (!manual && !prefs.downloadWhilePlaying) return false
        if (prefs.downloadWifiOnly && !isUnmeteredNetwork(context)) return false
        return true
    }

    /** 被拦截时的原因文案（用于手动下载时给用户一个解释，而不是静默失败） */
    fun blockedReason(prefs: UserPrefs = SettingsStore.readPrefs()): String = when {
        prefs.downloadWifiOnly -> "已开启「仅 WiFi 下载」，当前不是 WiFi 网络"
        else -> "已关闭「边听边下」，不会自动缓存"
    }
}
