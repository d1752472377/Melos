package com.aesthetic.music.data.api.platform

import com.aesthetic.music.data.api.PlaylistItem

object RankCatalog {

    const val NETEASE = "netease"
    const val QQ = "qq"
    const val KUGOU = "kugou"
    const val KUWO = "kuwo"

    const val DEFAULT = NETEASE

    data class Platform(val id: String, val label: String)

    val platforms: List<Platform> = listOf(
        Platform(NETEASE, "网易云"),
        Platform(QQ, "QQ 音乐"),
        Platform(KUGOU, "酷狗音乐"),
        Platform(KUWO, "酷我音乐"),
    )

    fun normalize(platform: String?): String {
        val p = platform?.trim()?.lowercase().orEmpty()
        return if (platforms.any { it.id == p }) p else DEFAULT
    }

    fun label(platform: String?): String =
        platforms.firstOrNull { it.id == normalize(platform) }?.label.orEmpty()

    fun kindOf(name: String?): String {
        val n = name.orEmpty()
        return when {
            n.contains("热歌") || n.contains("TOP500") || n.contains("hot", ignoreCase = true) -> "hot"
            n.contains("新歌") || n.contains("new", ignoreCase = true) -> "new"
            n.contains("飙升") || n.contains("rising", ignoreCase = true) -> "rising"
            n.contains("原创") || n.contains("original", ignoreCase = true) -> "original"
            else -> "other"
        }
    }

    fun kindOrder(kind: String?): Int = when (kind) {
        "hot" -> 0
        "new" -> 1
        "rising" -> 2
        "original" -> 3
        else -> 9
    }

    fun sortRanks(list: List<PlaylistItem>): List<PlaylistItem> =
        list.sortedBy { kindOrder(it.kind ?: kindOf(it.name)) }

    fun dedupe(list: List<PlaylistItem>): List<PlaylistItem> =
        list.distinctBy { "${it.platform}:${it.id}" }

    fun songLimit(platform: String): Int = when (normalize(platform)) {
        KUWO -> 300
        QQ -> 50
        KUGOU -> 100
        else -> 200
    }
}
