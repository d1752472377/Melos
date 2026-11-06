package com.aesthetic.music.data.api.platform

object NewSongRegions {

    data class Region(
        val id: String,
        val label: String,
        val areaId: Int,
        val english: String,
    )

    val all: List<Region> = listOf(
        Region(id = "huayu", label = "华语", areaId = 7, english = "MANDOPOP"),
        Region(id = "oumei", label = "欧美", areaId = 96, english = "WESTERN"),
        Region(id = "riyu", label = "日语", areaId = 8, english = "J-POP"),
        Region(id = "hanguo", label = "韩语", areaId = 16, english = "K-POP"),
    )

    const val HOME_CARD_COUNT = 3

    fun byId(id: String): Region? = all.firstOrNull { it.id == id }

    fun pick(count: Int = HOME_CARD_COUNT, dayKey: Long): List<Region> {
        if (all.isEmpty()) return emptyList()
        val take = count.coerceIn(1, all.size)
        val start = (((dayKey % all.size) + all.size) % all.size).toInt()
        return (0 until take).map { all[(start + it) % all.size] }
    }

    fun dayKeyOf(nowMs: Long): Long = nowMs / 86_400_000L
}
