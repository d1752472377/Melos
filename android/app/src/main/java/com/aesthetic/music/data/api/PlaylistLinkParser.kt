package com.aesthetic.music.data.api

data class PlaylistLink(
    val platform: String,
    val playlistId: String,
    val titleHint: String? = null,
    val rawUrl: String? = null,
) {
    val platformName: String get() = PlaylistLinkParser.platformName(platform)
}

object PlaylistLinkParser {

    const val P_NETEASE = "netease"
    const val P_QQ = "qq"
    const val P_KUGOU = "kugou"
    const val P_KUWO = "kuwo"

    val supportedPlatforms = listOf(P_NETEASE, P_QQ, P_KUGOU, P_KUWO)

    fun platformName(platform: String?): String = when (platform) {
        P_NETEASE -> "网易云音乐"
        P_QQ -> "QQ音乐"
        P_KUGOU -> "酷狗音乐"
        P_KUWO -> "酷我音乐"
        "local" -> "本地歌单"
        else -> "未知平台"
    }

    private val URL_REGEX = Regex(
        """https?://[^\s，,。；;、！？（）()【】\[\]<>“”"'《》\u4e00-\u9fff]+""",
        RegexOption.IGNORE_CASE,
    )

    private val QUOTE_TITLE_REGEX = Regex("""[《【]([^》】\r\n]{1,40})[》】]""")
    private val LABEL_TITLE_REGEX = Regex("""(?:歌单名|歌单名称|歌单)[\s:：]+([^\r\n]{1,40})""")

    private val FALLBACK_ID_REGEX = Regex("""\d{5,}""")

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

    private val HOST_PLATFORM = listOf(
        "music.163.com" to P_NETEASE,
        "163.com" to P_NETEASE,
        "y.qq.com" to P_QQ,
        "qq.com" to P_QQ,
        "kugou.com" to P_KUGOU,
        "kuwo.cn" to P_KUWO,
    )

    private val MENTION_PLATFORM = listOf(
        P_NETEASE to listOf("网易云音乐", "网易云", "netease"),
        P_QQ to listOf("qq音乐", "qqmusic"),
        P_KUGOU to listOf("酷狗音乐", "酷狗", "kugou"),
        P_KUWO to listOf("酷我音乐", "酷我", "kuwo"),
    )

    fun firstUrl(raw: String): String? = URL_REGEX.find(raw)?.value?.trimEnd(
        '.', ',', ';', ':', '!', '?', '、', '。', '，', '；', '！', '？', ')', '）', ']', '】',
    )

    fun parse(raw: String): PlaylistLink? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val url = firstUrl(text)
        val platform = url?.let { platformOfUrl(it) } ?: platformOfMention(text)
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
