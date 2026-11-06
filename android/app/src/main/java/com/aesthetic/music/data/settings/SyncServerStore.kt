package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences

object SyncServerStore {

    private const val PREFS = "aesthetic_music_sync_server"
    private const val KEY_SERVER_URL = "server_url"

    private const val LEGACY_KEY_SYNC_TOKEN = "sync_token"
    private const val LEGACY_KEY_LAST_SYNCED_AT = "last_synced_at"
    private const val LEGACY_KEY_LAST_SYNC_STATUS = "last_sync_status"
    private const val LEGACY_KEY_LAST_SYNC_VERSION = "last_sync_version"
    private const val LEGACY_KEY_LAST_EVENT_SINCE = "last_event_since"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs?.edit()
            ?.remove(LEGACY_KEY_SYNC_TOKEN)
            ?.remove(LEGACY_KEY_LAST_SYNCED_AT)
            ?.remove(LEGACY_KEY_LAST_SYNC_STATUS)
            ?.remove(LEGACY_KEY_LAST_SYNC_VERSION)
            ?.remove(LEGACY_KEY_LAST_EVENT_SINCE)
            ?.apply()
    }

    const val DEFAULT_SERVER_URL = "http://39.96.39.134"

    fun saveServerUrl(serverUrl: String) {
        prefs?.edit()?.putString(KEY_SERVER_URL, normalize(serverUrl))?.apply()
    }

    fun getServerUrl(): String {
        val configured = prefs?.getString(KEY_SERVER_URL, "")?.let { normalize(it) }.orEmpty()
        return if (configured.isNotBlank()) configured else DEFAULT_SERVER_URL
    }

    fun isConfigured(): Boolean = true

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    private fun normalize(url: String): String = url.trim().trimEnd('/')
}
