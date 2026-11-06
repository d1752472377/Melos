package com.aesthetic.music.player

import com.aesthetic.music.data.settings.UserPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载/缓存闸门的纯策略单测。
 *
 * 覆盖用户最容易踩的两条设置：
 *  · 关闭「边听边下」后，后台播放/预加载不能再偷偷缓存；
 *  · 开启「仅 WiFi」后，蜂窝网络下手动下载也要被拦住。
 */
class DownloadPolicyTest {

    @Test
    fun `关闭边听边下时 自动缓存被拦截`() {
        val prefs = UserPrefs(downloadWhilePlaying = false, downloadWifiOnly = false)
        assertFalse(DownloadPolicy.allowed(prefs, manual = false, unmetered = true))
    }

    @Test
    fun `关闭边听边下不影响用户主动下载`() {
        val prefs = UserPrefs(downloadWhilePlaying = false, downloadWifiOnly = false)
        assertTrue(DownloadPolicy.allowed(prefs, manual = true, unmetered = false))
    }

    @Test
    fun `仅 WiFi 时 蜂窝网络下自动缓存与手动下载都被拦截`() {
        val prefs = UserPrefs(downloadWhilePlaying = true, downloadWifiOnly = true)
        assertFalse(DownloadPolicy.allowed(prefs, manual = false, unmetered = false))
        assertFalse(DownloadPolicy.allowed(prefs, manual = true, unmetered = false))
    }

    @Test
    fun `仅 WiFi 时 不限流量网络允许缓存`() {
        val prefs = UserPrefs(downloadWhilePlaying = true, downloadWifiOnly = true)
        assertTrue(DownloadPolicy.allowed(prefs, manual = false, unmetered = true))
        assertTrue(DownloadPolicy.allowed(prefs, manual = true, unmetered = true))
    }
}
