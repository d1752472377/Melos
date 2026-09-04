package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * 私有同步服务器配置存储：
 * 替代旧的多用户登录系统。用户填入私有 Melos 后端地址与同步密钥后，
 * 即可实现双向云同步与云端音源脚本拉取。
 */
object SyncServerStore {

    private const val PREFS = "aesthetic_music_sync_server"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_SYNC_TOKEN = "sync_token"
    private const val KEY_LAST_SYNCED_AT = "last_synced_at"
    private const val KEY_LAST_SYNC_STATUS = "last_sync_status"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun save(serverUrl: String, syncToken: String) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        prefs?.edit()
            ?.putString(KEY_SERVER_URL, cleanUrl)
            ?.putString(KEY_SYNC_TOKEN, syncToken.trim())
            ?.apply()
    }

    fun updateSyncStatus(status: String, timeMs: Long = System.currentTimeMillis()) {
        prefs?.edit()
            ?.putString(KEY_LAST_SYNC_STATUS, status)
            ?.putLong(KEY_LAST_SYNCED_AT, timeMs)
            ?.apply()
    }

    fun getServerUrl(): String {
        return prefs?.getString(KEY_SERVER_URL, "")?.trim()?.trimEnd('/').orEmpty()
    }

    fun getSyncToken(): String {
        return prefs?.getString(KEY_SYNC_TOKEN, "")?.trim().orEmpty()
    }

    fun getLastSyncedAt(): Long {
        return prefs?.getLong(KEY_LAST_SYNCED_AT, 0L) ?: 0L
    }

    fun getLastSyncStatus(): String? {
        return prefs?.getString(KEY_LAST_SYNC_STATUS, null)
    }

    fun isConfigured(): Boolean {
        return getServerUrl().isNotBlank() && getSyncToken().isNotBlank()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
