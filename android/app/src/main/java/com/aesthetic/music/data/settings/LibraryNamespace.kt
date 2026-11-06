package com.aesthetic.music.data.settings

object LibraryNamespace {

    private const val GUEST = "guest"

    fun prefix(): String = when (val uid = currentUserId()) {
        null -> "$GUEST:"
        else -> "u$uid:"
    }

    fun currentUserId(): Long? =
        runCatching { UserStore.user()?.id }.getOrNull()?.takeIf { it > 0 }

    fun read(prefs: android.content.SharedPreferences?, key: String): String? {
        if (prefs == null) return prefsNullLegacy(key)
        val legacy = prefs.getString(key, null)
        if (legacy != null) {
            val p = prefix()
            if (prefs.getString("$p$key", null) == null) {
                prefs.edit().putString("$p$key", legacy).apply()
            }
            prefs.edit().remove(key).apply()
        }
        return prefs.getString(prefix() + key, null)
    }

    private fun prefsNullLegacy(key: String): String? = null

    fun write(prefs: android.content.SharedPreferences?, key: String, value: String) {
        prefs?.edit()?.putString(prefix() + key, value)?.apply()
    }

    fun clear(prefs: android.content.SharedPreferences?, key: String) {
        prefs?.edit()?.remove(prefix() + key)?.apply()
    }
}
