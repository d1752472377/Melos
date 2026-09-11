package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * 私有同步服务器配置存储：
 * 保存用户填入的私有 Melos 后端地址与同步密钥，
 * 以及多端增量同步的版本号与事件游标。
 */
object SyncServerStore {

    private const val PREFS = "aesthetic_music_sync_server"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_SYNC_TOKEN = "sync_token"
    private const val KEY_LAST_SYNCED_AT = "last_synced_at"
    private const val KEY_LAST_SYNC_STATUS = "last_sync_status"
    private const val KEY_LAST_SYNC_VERSION = "last_sync_version"
    private const val KEY_LAST_EVENT_SINCE = "last_event_since"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun save(serverUrl: String, syncToken: String) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanToken = syncToken.trim()
        prefs?.edit()
            ?.putString(KEY_SERVER_URL, cleanUrl)
            // 加密失败时退回明文，保证同步功能可用
            ?.putString(KEY_SYNC_TOKEN, TokenCipher.encryptToStored(cleanToken) ?: cleanToken)
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

    /**
     * 读取同步密钥。
     *
     * 兼容历史明文值：读到明文时顺带就地升级为密文（一次性）。
     * 该方法会被 OkHttp 拦截器在**每个请求**上调用，因此
     * [TokenCipher] 内部对解密结果做了内存缓存，避免每次请求都走 Keystore。
     */
    fun getSyncToken(): String {
        val stored = prefs?.getString(KEY_SYNC_TOKEN, "")?.trim().orEmpty()
        if (stored.isBlank()) return ""
        val plain = TokenCipher.unwrap(stored) ?: return ""
        if (!TokenFormat.isEncrypted(stored)) {
            TokenCipher.encryptToStored(plain)?.let {
                prefs?.edit()?.putString(KEY_SYNC_TOKEN, it)?.apply()
            }
        }
        return plain
    }

    fun getLastSyncedAt(): Long {
        return prefs?.getLong(KEY_LAST_SYNCED_AT, 0L) ?: 0L
    }

    fun getLastSyncStatus(): String? {
        return prefs?.getString(KEY_LAST_SYNC_STATUS, null)
    }

    fun getLastSyncVersion(): Long {
        return prefs?.getLong(KEY_LAST_SYNC_VERSION, 0L) ?: 0L
    }

    fun setLastSyncVersion(version: Long) {
        prefs?.edit()?.putLong(KEY_LAST_SYNC_VERSION, version)?.apply()
    }

    fun getLastEventSince(): Long {
        return prefs?.getLong(KEY_LAST_EVENT_SINCE, 0L) ?: 0L
    }

    fun setLastEventSince(eventId: Long) {
        prefs?.edit()?.putLong(KEY_LAST_EVENT_SINCE, eventId)?.apply()
    }

    fun isConfigured(): Boolean {
        return getServerUrl().isNotBlank() && getSyncToken().isNotBlank()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
