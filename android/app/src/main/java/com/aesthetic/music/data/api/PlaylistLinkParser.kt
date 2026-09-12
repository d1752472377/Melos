package com.aesthetic.music.data.api

/** 从分享链接 / 分享文案中识别出的歌单引用 */
data class PlaylistLink(
    /** 平台代码：netease / qq / kugou / kuwo */
    val platform: String,
    val playlistId: String,
    /** 文案里识别到的歌单名（常见于"分享歌单《XXX》"），可能为空 */
    val titleHint: String? = null,
    /** 识别到的原始链接；纯 ID/纯文案导入时为空 */
    val rawUrl: String? = null,
) {
    val platformName: String get() = PlaylistLinkParser.platformName(platform)
}

/**
 * 歌单链接 / 分享文案解析（纯函数，可在 JVM 单测里跑）。
 *
 * 支持三种粘贴形式：
 * 1. 完整链接：`https://music.163.com/#/playlist?id=5202687076`
 * 2. 完整分享文案：`分享@Melos的歌单《深夜emo》https://y.qq.com/n/ryqq/playlist/8667696780 来自QQ音乐`
 * 3. 裸歌单 ID：`5202687076`（历史行为，按网易云处理）
 *
 * 识别不出所属平台的链接返回 null（调用方据此提示"暂不支持该平台"），
 * 而不是猜一个平台去请求——猜错会让用户看到"歌单不存在"这种误导性错误。
 */
object PlaylistLinkParser {

    const val P_NETEASE = "netease"
    const val P_QQ = "qq"
    const val P_KUGOU = "kugou"
    const val P_KUWO = "kuwo"

    /** UI 展示顺序 */
    val supportedPlatforms = listOf(P_NETEASE, P_QQ, P_KUGOU, P_KUWO)

    fun platformName(platform: String?): String = when (platform) {
        P_NETEASE -> "网易云音乐"
        P_QQ -> "QQ音乐"
        P_KUGOU -> "酷狗音乐"
        P_KUWO -> "酷我音乐"
        "local" -> "本地歌单"
        else -> "未知平台"
    }

    /**
     * 链接：排除中文标点、括号、引号与中日韩文字。
     *
     * 注意不能把 ASCII 的 `?` / `!` 也排掉——`/playlist?id=xxx` 里的 `?` 是链接的一部分；
     * 而分享文案里 URL 后面常常直接跟中文（如"…来自网易云音乐"），所以用 CJK 区段做右边界。
     */
    private val URL_REGEX = Regex(
        """https?://[^\s，,。；;、！？（）()【】\[\]<>“”"'《》\u4e00-\u9fff]+""",
        RegexOption.IGNORE_CASE,
    )

    private val QUOTE_TITLE_REGEX = Regex("""[《【]([^》】\r\n]{1,40})[》】]""")
    private val LABEL_TITLE_REGEX = Regex("""(?:歌单名|歌单名称|歌单)[\s:：]+([^\r\n]{1,40})""")

    /** 歌单 ID 兜底：5 位以上纯数字（旧版导入弹窗用的就是这个规则） */
    private val FALLBACK_ID_REGEX = Regex("""\d{5,}""")

    /**
     * 各平台 ID 的抓取顺序（先精确字段，再退化为"链接里最长的那串数字"）。
     * 酷狗的 specialid 可以是 3 位以上，其余平台歌单 ID 都是 5 位以上。
     */
    private val ID_PATTERNS: Map<String, List<Regex>> = mapOf(
        P_NETEASE to listOf(
            Regex("""[?&#/]id=(\d{5,})"""),
            Regex("""/playlist/(\d{5,})"""),
            Regex("""/toplist/(\d{5,})"""),
        ),
        P_QQ to listOf(
            Regex("""/playlist/(\d{5,})"""),
            Regex("""disstid=(\d{5,})"""),
            Regex("""[?&#]id=(\d{5,})"""),
        ),
        P_KUGOU to listOf(
            Regex("""/special/single/(\d{3,})"""),
            Regex("""specialid=(\d{3,})"""),
            Regex("""/special/(\d{3,})"""),
            Regex("""[?&#]id=(\d{3,})"""),
        ),
        P_KUWO to listOf(
            Regex("""/playlist_detail/(\d{3,})"""),
            Regex("""[?&#]pid=(\d{3,})"""),
            Regex("""/playlist/(\d{3,})"""),
        ),
    )

    /** 域名 → 平台（前缀更具体的放前面） */
    private val HOST_PLATFORM = listOf(
        "music.163.com" to P_NETEASE,
        "163.com" to P_NETEASE,
        "y.qq.com" to P_QQ,
        "qq.com" to P_QQ,
        "kugou.com" to P_KUGOU,
        "kuwo.cn" to P_KUWO,
    )

    /** 文案里出现的平台名 → 平台 */
    private val MENTION_PLATFORM = listOf(
        P_NETEASE to listOf("网易云音乐", "网易云", "netease"),
        P_QQ to listOf("qq音乐", "qqmusic"),
        P_KUGOU to listOf("酷狗音乐", "酷狗", "kugou"),
        P_KUWO to listOf("酷我音乐", "酷我", "kuwo"),
    )

    /** 文案里的第一个链接（短链展开用/调试用） */
    fun firstUrl(raw: String): String? = URL_REGEX.find(raw)?.value?.trimEnd(
        '.', ',', ';', ':', '!', '?', '、', '。', '，', '；', '！', '？', ')', '）', ']', '】',
    )

    fun parse(raw: String): PlaylistLink? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val url = firstUrl(text)
        val platform = url?.let { platformOfUrl(it) } ?: platformOfMention(text)
        // 有链接但认不出平台：明确不支持，而不是猜一个平台去请求
        if (url != null && platform == null) return null
        val p = platform ?: P_NETEASE

        val source = url ?: text
        val id = extractId(source, p) ?: (if (url != null) extractId(text, p) else null) ?: return null

        return PlaylistLink(
            platform = p,
            playlistId = id,
            titleHint = extractTitle(text, url),
            rawUrl = url,
        )
    }

    private fun platformOfUrl(url: String): String? {
        val host = try {
            java.net.URI(url).host
        } catch (_: Exception) {
            null
        }?.lowercase()
            ?: Regex("""^https?://([^/:?#]+)""", RegexOption.IGNORE_CASE)
                .find(url)?.groupValues?.get(1)?.lowercase()
            ?: return null
        return HOST_PLATFORM.firstOrNull { (domain, _) ->
            host == domain || host.endsWith(".$domain")
        }?.second
    }

    private fun platformOfMention(text: String): String? {
        val lower = text.lowercase()
        return MENTION_PLATFORM.firstOrNull { (_, words) -> words.any { lower.contains(it) } }?.first
    }

    private fun extractId(source: String, platform: String): String? {
        ID_PATTERNS[platform]?.forEach { regex ->
            regex.find(source)?.groupValues?.get(1)?.let { return it }
        }
        return FALLBACK_ID_REGEX.find(source)?.value
    }

    /** 文案里的歌单名：优先「《》」包裹，其次「歌单：xxx」这种标签写法 */
    private fun extractTitle(text: String, url: String?): String? {
        val withoutUrl = if (url.isNullOrBlank()) text else text.replace(url, " ")
        QUOTE_TITLE_REGEX.find(withoutUrl)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        LABEL_TITLE_REGEX.find(withoutUrl)?.groupValues?.get(1)
            ?.trim()?.trimEnd('，', ',', '。', '.', '！', '!')?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }
}
