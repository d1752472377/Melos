package com.aesthetic.music.ui.sheet

object SheetTags {

    const val ALL = "全部"

    private val ALIASES = mapOf(
        "电音" to "电子",
        "原声" to "影视原声",
        "影视" to "影视原声",
        "纯音乐" to "轻音乐",
        "国风" to "古风",
    )

    fun normalize(cardTitle: String?, tags: List<String>): String {
        val fallback = tags.firstOrNull { it == ALL } ?: tags.firstOrNull() ?: ALL
        val name = cardTitle?.trim().orEmpty()
        if (name.isEmpty()) return fallback
        tags.firstOrNull { it == name }?.let { return it }
        val alias = ALIASES[name] ?: return fallback
        return tags.firstOrNull { it == alias } ?: fallback
    }
}
