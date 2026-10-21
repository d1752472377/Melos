package com.aesthetic.music.data.api.platform

/**
 * 首页「新歌首发」按语种/地区取新歌的区域目录（纯逻辑，无 IO，便于单测）。
 *
 * ## 为什么要有这个文件
 * 首页原来那一版「新碟首发」是拿**歌单**当专辑画（`NewAlbumSection`），
 * 点进去只能看到歌单详情，跟「新碟」没有任何关系。现在的语义改成
 * **不同地区的新歌榜**，一屏三张卡，每张卡是一个语种/地区的新歌列表。
 *
 * ## areaId 是真值，不是猜的
 * 数据源是网易云「新歌速递」的按地区接口
 * `GET https://music.163.com/api/discovery/new/songs?areaId=<areaId>`。
 * 逐个实测过（2026-02，取回 100 条/区）：
 *
 * | areaId | 区域 | 实测样本 |
 * |---|---|---|
 * | 0  | 全部 | 混排（いよわ / EasyPop / 姚晓棠 / 鯨） |
 * | 7  | 华语 | 前路有光[张杰]、爱情小孩[张国荣] |
 * | 96 | 欧美 | expectations[Olivia Rodrigo]、Housewife[Tate McRae] |
 * | 8  | 日语 | Raven Sky[Ado]、咲き誇れ[YOASOBI] |
 * | 16 | 韩语 | new trick[ROSÉ]、이 별로부터[IU] |
 *
 * 其余 id（1~6、9~15、17~20、97~100）实测均返回空 `data`，所以这里只登记有数据的四个。
 *
 * ## 关于「粤语」
 * 该接口没有粤语分区（粤语新歌被并入华语区），所以候选区域里没有单独的粤语榜——
 * 编一个出来只会拿华语歌冒充粤语歌，不如如实少一个。
 *
 * ## 为什么每天轮换
 * 候选有 4 个区域但首页只放 3 张卡（一行放 4 张会挤到 12sp 以下）。
 * 固定丢掉某一个区域，那个区域的用户永远看不到自己的语种；
 * 按「当天日期」轮换起始下标，四天一轮回，每天的三张卡是确定的（同一天多次进首页不变）。
 */
object NewSongRegions {

    /**
     * @param id        稳定标识（仅本工程内部使用）
     * @param label     中文名（卡片主标题，如「华语新歌」由调用方拼接）
     * @param areaId    新歌速递接口的地区参数（见类注释的实测表）
     * @param english   英文小标（卡片副行的风格化文字）
     */
    data class Region(
        val id: String,
        val label: String,
        val areaId: Int,
        val english: String,
    )

    /** 顺序即轮换顺序：华语 → 欧美 → 日语 → 韩语 */
    val all: List<Region> = listOf(
        Region(id = "huayu", label = "华语", areaId = 7, english = "MANDOPOP"),
        Region(id = "oumei", label = "欧美", areaId = 96, english = "WESTERN"),
        Region(id = "riyu", label = "日语", areaId = 8, english = "J-POP"),
        Region(id = "hanguo", label = "韩语", areaId = 16, english = "K-POP"),
    )

    /** 首页一行放三张卡（与旧「新碟首发」的三卡布局一致） */
    const val HOME_CARD_COUNT = 3

    fun byId(id: String): Region? = all.firstOrNull { it.id == id }

    /**
     * 取当天的 [count] 个区域：以 [dayKey]（建议传「当天是第几天」，见 [dayKeyOf]）为起点环形取。
     *
     * @param dayKey 任意单调递增的天序号；同一 [dayKey] 必得同一组区域
     */
    fun pick(count: Int = HOME_CARD_COUNT, dayKey: Long): List<Region> {
        if (all.isEmpty()) return emptyList()
        val take = count.coerceIn(1, all.size)
        val start = (((dayKey % all.size) + all.size) % all.size).toInt()
        return (0 until take).map { all[(start + it) % all.size] }
    }

    /** 把时间戳换算成「第几天」（UTC 天序号），保证同一天内结果稳定 */
    fun dayKeyOf(nowMs: Long): Long = nowMs / 86_400_000L
}
