package com.aesthetic.music.data.settings

object TokenFormat {

    const val PREFIX = "v1:"

    fun wrap(payload: String): String = PREFIX + payload

    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    fun payloadOf(stored: String): String? =
        if (isEncrypted(stored)) stored.removePrefix(PREFIX).takeIf { it.isNotBlank() } else null
}
