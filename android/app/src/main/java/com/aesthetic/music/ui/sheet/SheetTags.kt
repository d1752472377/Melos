package com.aesthetic.music.ui.sheet

/**
 * 首页「分类歌单」卡片名 → 歌单广场标签的归一化规则。
 *
 * 为什么需要它：两个列表来源完全不同，名字对不上是常态——
 *  - 首页卡片名来自 `PlatformMusicService.getCategories()`（流行 / 电子 / 民谣 / 摇滚，其中"电子"在卡片上显示为"电音"）；
 *  - 歌单广场标签来自 `PlatformMusicService.defaultSheetTags`（全部 / 华语 / 欧美 / … / 流行 / 电子 / 民谣 / 摇滚 / …）。
 *
 * 如果不归一化就直接把卡片名当标签用：标签抽屉里高亮不到任何一项、请求用的 cat 也不是歌单广场认识的分类，
 * 用户看到的就是"进了一个不存在的分类、什么都没有"。
 */
object SheetTags {

    /** 缺省标签：歌单广场的第一项，也是"不筛选"的语义 */
    const val ALL = "全部"

    /**
     * 常见别名：卡片叫法 → 歌单广场标签。左值只放"确实指同一个分类"的说法，
     * 有疑问的一律不映射（宁可退回"全部"，也不要请求一个平台不认识的分类）。
     */
    private val ALIASES = mapOf(
        "电音" to "电子",
        "原声" to "影视原声",
        "影视" to "影视原声",
        "纯音乐" to "轻音乐",
        "国风" to "古风",
    )

    /**
     * 归一化到 [tags] 中真实存在的标签；无法对应时退回 [ALL]（不存在则第一个标签）。
     *
     * 这样"首页点卡片 → 广场"永远落在一个**存在的**分类上，最坏情况也只是回到全部歌单，
     * 不会出现空分类。
     */
    fun normalize(cardTitle: String?, tags: List<String>): String {
        val fallback = tags.firstOrNull { it == ALL } ?: tags.firstOrNull() ?: ALL
        val name = cardTitle?.trim().orEmpty()
        if (name.isEmpty()) return fallback
        tags.firstOrNull { it == name }?.let { return it }
        val alias = ALIASES[name] ?: return fallback
        return tags.firstOrNull { it == alias } ?: fallback
    }
}
