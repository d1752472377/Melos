package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.api.UserInfo

object UserStore {

    private const val TAG = "UserStore"
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
                ?.putString(KEY_TOKEN, TokenCipher.encryptToStored(token) ?: token)
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
        val stored = prefs?.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val plain = TokenCipher.unwrap(stored)?.takeIf { it.isNotBlank() } ?: return null
        if (!TokenFormat.isEncrypted(stored)) {
            TokenCipher.encryptToStored(plain)?.let {
                prefs?.edit()?.putString(KEY_TOKEN, it)?.apply()
            }
        }
        plain
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

    private const val KEY_CUSTOM_AVATAR = "custom_avatar"
    private const val KEY_CUSTOM_BG = "custom_bg"
    private const val KEY_BG_BLUR = "bg_blur"
    private const val KEY_CUSTOM_NICKNAME = "custom_nickname"
    private const val KEY_CUSTOM_BIO = "custom_bio"
    private const val KEY_CUSTOM_GENDER = "custom_gender"
    private const val KEY_CUSTOM_BIRTHDAY = "custom_birthday"
    private const val KEY_CUSTOM_ID = "custom_id"

    fun logout() {
        try {
            prefs?.edit()?.remove(KEY_TOKEN)?.remove(KEY_USER)?.apply()
        } catch (_: Exception) {
        }
    }

    fun getAvatarPath(): String? = try {
        prefs?.getString(KEY_CUSTOM_AVATAR, null)?.takeIf { it.isNotBlank() }
            ?: user()?.avatar?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun setAvatarPath(path: String?) = bestEffort(TAG, "保存头像路径") {
            if (path == null) {
                prefs?.edit()?.remove(KEY_CUSTOM_AVATAR)?.apply()
            } else {
                prefs?.edit()?.putString(KEY_CUSTOM_AVATAR, path)?.apply()
            }
    }

    fun getBackgroundPath(): String? = try {
        prefs?.getString(KEY_CUSTOM_BG, null)?.takeIf { it.isNotBlank() }
            ?: user()?.background?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun setBackgroundPath(path: String?) = bestEffort(TAG, "保存背景图路径") {
            if (path == null) {
                prefs?.edit()?.remove(KEY_CUSTOM_BG)?.apply()
            } else {
                prefs?.edit()?.putString(KEY_CUSTOM_BG, path)?.apply()
            }
    }

    fun isBackgroundBlur(): Boolean = try {
        prefs?.getBoolean(KEY_BG_BLUR, false) ?: false
    } catch (_: Exception) {
        false
    }

    fun setBackgroundBlur(blur: Boolean) = bestEffort(TAG, "保存背景模糊开关") {
            prefs?.edit()?.putBoolean(KEY_BG_BLUR, blur)?.apply()
    }

    fun getNickname(): String = try {
        user()?.nickname?.takeIf { it.isNotBlank() }
            ?: prefs?.getString(KEY_CUSTOM_NICKNAME, null)?.takeIf { it.isNotBlank() }
            ?: user()?.username?.takeIf { it.isNotBlank() }
            ?: "Melos 音乐人"
    } catch (_: Exception) {
        "Melos 音乐人"
    }

    fun setNickname(nickname: String) = bestEffort(TAG, "保存昵称") {
            prefs?.edit()?.putString(KEY_CUSTOM_NICKNAME, nickname)?.apply()
    }

    fun getBio(): String = try {
        prefs?.getString(KEY_CUSTOM_BIO, "") ?: ""
    } catch (_: Exception) {
        ""
    }

    fun setBio(bio: String) = bestEffort(TAG, "保存个性签名") {
            prefs?.edit()?.putString(KEY_CUSTOM_BIO, bio)?.apply()
    }

    fun getUserId(): String = try {
        user()?.id?.takeIf { it > 0 }?.toString()
            ?: prefs?.getString(KEY_CUSTOM_ID, null)?.takeIf { it.isNotBlank() }
            ?: "10001"
    } catch (_: Exception) {
        "10001"
    }

    fun setUserId(id: String) = bestEffort(TAG, "保存用户 ID") {
            prefs?.edit()?.putString(KEY_CUSTOM_ID, id)?.apply()
    }

    fun restoreProfileDefaults(context: Context) = bestEffort(TAG, "恢复个人资料默认值") {
        val dir = java.io.File(context.filesDir, "profile")
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith(BG_FILE_PREFIX)) file.delete()
        }
        prefs?.edit()
            ?.remove(KEY_CUSTOM_BG)
            ?.remove(KEY_BG_BLUR)
            ?.remove(KEY_CUSTOM_NICKNAME)
            ?.remove(KEY_CUSTOM_BIO)
            ?.remove(KEY_CUSTOM_GENDER)
            ?.remove(KEY_CUSTOM_BIRTHDAY)
            ?.apply()
    }

    private const val BG_FILE_PREFIX = "background"
}
