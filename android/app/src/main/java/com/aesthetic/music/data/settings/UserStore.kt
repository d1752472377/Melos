package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.api.UserInfo

/**
 * 用户登录态持久化：token + 用户信息。
 * OkHttp 拦截器从这里读取 token，给用户接口请求加 Authorization。
 */
object UserStore {

    private const val PREFS = "aesthetic_music_user"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user"

    private var prefs: SharedPreferences? = null
    private val gson = AppJson.gson

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun save(token: String, user: UserInfo) {
        try {
            prefs?.edit()
                ?.putString(KEY_TOKEN, token)
                ?.putString(KEY_USER, gson.toJson(user))
                ?.apply()
        } catch (_: Exception) {
        }
    }

    fun updateUser(user: UserInfo) {
        try {
            prefs?.edit()?.putString(KEY_USER, gson.toJson(user))?.apply()
        } catch (_: Exception) {
        }
    }

    fun token(): String? = try {
        prefs?.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun user(): UserInfo? = try {
        val raw = prefs?.getString(KEY_USER, null) ?: return null
        gson.fromJson(raw, UserInfo::class.java)
    } catch (_: Exception) {
        null
    }

    fun isLoggedIn(): Boolean = token() != null && user() != null

    fun logout() {
        try {
            prefs?.edit()?.remove(KEY_TOKEN)?.remove(KEY_USER)?.apply()
        } catch (_: Exception) {
        }
    }
}
