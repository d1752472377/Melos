package com.aesthetic.music.data.api.platform

import com.aesthetic.music.data.api.PlaylistItem

/**
 * 榜单平台目录（纯逻辑，无 IO，便于单测）。
 *
 * 为什么单独抽一个文件：四个平台的榜单接口形态差别很大——
 *  - 网易云：`/api/toplist/detail` 一次返回全部榜单，无分组；
 *  - QQ：musicu.fcg 的 `GetAll` 返回「巅峰榜 / 地区榜 / 特色榜 / 全球榜」四组，每组若干榜单；
 *  - 酷狗：`rank/list` 一页返回 55 个榜单，无分组；
 *  - 酷我：`bang/list` 是「分组 → 榜单」两层树，且榜单 id 用的是"新式"歌单 id。
 * 界面只关心「平台 tag + 该平台榜单列表」，所以把平台身份、榜单语义归类、排序这些纯函数集中在这里，
 * 网络层只负责把各平台 JSON 翻译成 [PlaylistItem]，UI 只负责画。
 */
object RankCatalog {

    const val NETEASE = "netease"
    const val QQ = "qq"
    const val KUGOU = "kugou"
    const val KUWO = "kuwo"

    /** 默认平台：与首页「排行榜」区块（新歌/热歌）保持同一数据源，切页不会有视觉跳变 */
    const val DEFAULT = NETEASE

    data class Platform(val id: String, val label: String)

    /** 顺序即界面 tag 的顺序，不再按「热度」动态排序：位置固定的 tab 才能形成肌肉记忆 */
    val platforms: List<Platform> = listOf(
        Platform(NETEASE, "网易云"),
        Platform(QQ, "QQ 音乐"),
        Platform(KUGOU, "酷狗音乐"),
        Platform(KUWO, "酷我音乐"),
    )

    /** 未知/空平台回落默认，避免脏数据（旧存档里的 platform）让界面出现空 tag */
    fun normalize(platform: String?): String {
        val p = platform?.trim()?.lowercase().orEmpty()
        return if (platforms.any { it.id == p }) p else DEFAULT
    }

    fun label(platform: String?): String =
        platforms.firstOrNull { it.id == normalize(platform) }?.label.orEmpty()

    /**
     * 榜单语义归类：hot / new / rising / original / other。
     *
     * 各平台榜单名不完全一致（"热歌榜" vs "TOP500" vs "飙升榜" vs "酷我热歌榜"），
     * 用关键词匹配而不是精确相等，这样新增榜单名也能自动归类。
     */
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

    /** 榜单排序权重：热歌 → 新歌 → 飙升 → 原创 → 其它（接口返回顺序各平台不保证，钉死免得每次进页面都跳） */
    fun kindOrder(kind: String?): Int = when (kind) {
        "hot" -> 0
        "new" -> 1
        "rising" -> 2
        "original" -> 3
        else -> 9
    }

    /** 稳定排序：同权重保留接口原顺序（sortedBy 是稳定排序） */
    fun sortRanks(list: List<PlaylistItem>): List<PlaylistItem> =
        list.sortedBy { kindOrder(it.kind ?: kindOf(it.name)) }

    /**
     * 去重（按 平台:榜单id）。接口偶发把同一个榜单返给两个分组（酷我是分组树，最容易出现），
     * 而界面用 `平台:id` 当 LazyColumn 的 key——重复 key 会直接抛异常白屏，
     * 所以这一层必须兜住，而不是指望接口永远干净。
     */
    fun dedupe(list: List<PlaylistItem>): List<PlaylistItem> =
        list.distinctBy { "${it.platform}:${it.id}" }

    /**
     * 单次拉取的榜单曲目上限（实测值，不是"想要的"值）。
     *
     *  - 酷我：pl.svc 一页 rn 最大 300（与歌单接口同一套实现）；
     *  - QQ：h5 的 toplist_cp 接口**单页最多返 50 首**（实测请求 song_num=100 仍只返 50），
     *    因此这里直接写 50，避免让人误以为能拿到 100 而去做"少了一半"的排查；
     *  - 酷狗：rank/song 单页 100，只取第一页（榜单看点在前列，没必要为第 2-5 页多打 4 次请求）；
     *  - 网易云：歌单/榜单详情接口支持更多，200 足够覆盖所有榜单且不拖慢首屏。
     */
    fun songLimit(platform: String): Int = when (normalize(platform)) {
        KUWO -> 300
        QQ -> 50
        KUGOU -> 100
        else -> 200
    }
}
