package com.aesthetic.music.player

/** 单字歌词时间戳 */
data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/**
 * 单行歌词（带时间戳；translated 非空时显示原文+翻译两行；支持逐字、罗马音与声部标记）
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translated: String? = null,
    val romanization: String? = null,
    val durationMs: Long = 0L,
    val words: List<LyricWord> = emptyList(),
    val isDuet: Boolean = false,
    val isBackground: Boolean = false,
) {
    val isWordByWord: Boolean get() = words.isNotEmpty()
}

/**
 * 歌词制作人与版权元数据智能过滤工具库
 * 参考瓜子音乐 jk4.java 逻辑，过滤非演唱行（如作词/作曲/出品等）
 */
object MetadataFilter {
    private val FILTER_KEYWORDS = listOf(
        "作词", "作曲", "编曲", "词：", "曲：", "制作人", "混音", "母带",
        "监制", "和声", "合声", "录音", "吉他", "钢琴", "贝斯", "鼓手",
        "弦乐", "统筹", "Program", "Lyrics", "Music by", "Produced by",
        "Mixed by", "Mastered by", "演唱", "出品", "发行"
    )

    /**
     * 只在「带冒号」时才算元数据的短标签（OP / SP 是版权方缩写）。
     *
     * 为什么单独拎出来：这两个只有 2 个字母，用 `startsWith` 匹配会把
     * 任何以 "Op…" / "Sp…" 开头的英文歌词行（Open / Spend / Speaking…）整行吃掉。
     */
    private val COLON_ONLY_KEYWORDS = listOf("OP", "SP", "词", "曲")

    fun isMetadataLine(rawText: String, startMs: Long = 0L, title: String = "", artist: String = ""): Boolean {
        val clean = rawText.replace(Regex("""[（(\[【].*?[）)\]】]"""), "").trim()
        if (clean.isBlank()) return true
        for (kw in FILTER_KEYWORDS) {
            if (clean.startsWith(kw, ignoreCase = true)) return true
        }
        for (kw in COLON_ONLY_KEYWORDS) {
            val withColon = clean.startsWith("$kw:", ignoreCase = true) ||
                    clean.startsWith("$kw：", ignoreCase = true) ||
                    clean.equals(kw, ignoreCase = true)
            if (withColon) return true
        }
        if (startMs <= 1000L && title.length >= 2 && artist.length >= 2) {
            if (clean.contains(title, ignoreCase = true) && clean.contains(artist, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}

/**
 * 网易云 YRC 逐字歌词解析器
 * 行级：[startMs,durationMs]
 * 词级：(offsetMs,durationMs,0)文字
 */
fun parseYrc(yrc: String?, title: String = "", artist: String = ""): List<LyricLine> {
    if (yrc.isNullOrBlank()) return emptyList()
    val lineRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
    val wordRegex = Regex("""\((\d+),(\d+)(?:,\d+)?\)([^()\[\]]*)""")
    val lines = mutableListOf<LyricLine>()

    for (raw in yrc.lineSequence()) {
        val trimmed = raw.trim()
        val lineMatch = lineRegex.find(trimmed) ?: continue
        val lineStartMs = lineMatch.groupValues[1].toLongOrNull() ?: continue
        val lineDurationMs = lineMatch.groupValues[2].toLongOrNull() ?: 0L
        val lineBody = lineMatch.groupValues[3]

        if (MetadataFilter.isMetadataLine(lineBody, lineStartMs, title, artist)) {
            continue
        }

        val words = mutableListOf<LyricWord>()
        for (wordMatch in wordRegex.findAll(lineBody)) {
            val wordStart = wordMatch.groupValues[1].toLongOrNull() ?: continue
            val wordDur = wordMatch.groupValues[2].toLongOrNull() ?: continue
            val wordText = wordMatch.groupValues[3]
            if (wordText.isNotEmpty()) {
                words.add(LyricWord(startMs = wordStart, endMs = wordStart + wordDur, text = wordText))
            }
        }

        val fullText = if (words.isNotEmpty()) {
            words.joinToString("") { it.text }.trim()
        } else {
            lineBody.replace(Regex("""\(\d+,\d+(?:,\d+)?\)"""), "").trim()
        }

        if (fullText.isNotEmpty()) {
            lines.add(
                LyricLine(
                    timeMs = lineStartMs,
                    durationMs = lineDurationMs,
                    text = fullText,
                    words = words
                )
            )
        }
    }
    return lines.sortedBy { it.timeMs }
}

/**
 * 酷狗 KRC 逐字歌词解析器
 * 行级：[startMs,durationMs]
 * 词级：<offsetMs,durationMs,0>文字
 */
fun parseKrc(krc: String?, title: String = "", artist: String = ""): List<LyricLine> {
    if (krc.isNullOrBlank()) return emptyList()
    val lineRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
    val wordRegex = Regex("""<(\d+),(\d+),\d+>([^<]*)""")
    val lines = mutableListOf<LyricLine>()

    for (raw in krc.lineSequence()) {
        val trimmed = raw.trim()
        val lineMatch = lineRegex.find(trimmed) ?: continue
        val lineStartMs = lineMatch.groupValues[1].toLongOrNull() ?: continue
        val lineDurationMs = lineMatch.groupValues[2].toLongOrNull() ?: 0L
        val lineBody = lineMatch.groupValues[3]

        if (MetadataFilter.isMetadataLine(lineBody, lineStartMs, title, artist)) {
            continue
        }

        val words = mutableListOf<LyricWord>()
        for (wm in wordRegex.findAll(lineBody)) {
            val wordOffset = wm.groupValues[1].toLongOrNull() ?: continue
            val wordDur = wm.groupValues[2].toLongOrNull() ?: continue
            val wordText = wm.groupValues[3]
            if (wordText.isNotEmpty()) {
                val start = lineStartMs + wordOffset
                words.add(LyricWord(startMs = start, endMs = start + wordDur, text = wordText))
            }
        }

        val fullText = if (words.isNotEmpty()) {
            words.joinToString("") { it.text }.trim()
        } else {
            lineBody.replace(Regex("""<\d+,\d+,\d+>"""), "").trim()
        }

        if (fullText.isNotEmpty()) {
            lines.add(
                LyricLine(
                    timeMs = lineStartMs,
                    durationMs = lineDurationMs,
                    text = fullText,
                    words = words
                )
            )
        }
    }
    return lines.sortedBy { it.timeMs }
}

/**
 * 解析 LRC 歌词为带时间戳的歌词行。
 * 支持 "[mm:ss.xx]文本" 或 "[mm:ss]文本"，支持一行多个时间标签与 offset 元标签。
 */
fun parseLrc(lrc: String?, title: String = "", artist: String = ""): List<LyricLine> {
    if (lrc.isNullOrBlank()) return emptyList()

    // 若包含 YRC / KRC 标志，直接按逐字解析
    if (lrc.contains(Regex("""\[\d+,\d+]\(\d+,\d+"""))) {
        val yrcParsed = parseYrc(lrc, title, artist)
        if (yrcParsed.isNotEmpty()) return yrcParsed
    }
    if (lrc.contains(Regex("""\[\d+,\d+]<\d+,\d+"""))) {
        val krcParsed = parseKrc(lrc, title, artist)
        if (krcParsed.isNotEmpty()) return krcParsed
    }

    val offsetMatch = Regex("""\[offset:\s*(-?\d+)\s*\]""").find(lrc)
    val offsetMs = offsetMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
    val body = if (offsetMatch != null) lrc.replace(offsetMatch.value, "") else lrc
    val regex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    val lines = mutableListOf<LyricLine>()

    for (raw in body.lineSequence()) {
        val matches = regex.findAll(raw).toList()
        if (matches.isEmpty()) continue
        val text = raw.replace(regex, "").trim()
        if (text.isEmpty()) continue

        for (m in matches) {
            val min = m.groupValues[1].toLongOrNull() ?: continue
            val sec = m.groupValues[2].toLongOrNull() ?: continue
            val fracStr = m.groupValues[3]
            val frac = when (fracStr.length) {
                0 -> 0L
                1 -> fracStr.toLongOrNull()?.times(100) ?: 0L
                2 -> fracStr.toLongOrNull()?.times(10) ?: 0L
                else -> fracStr.take(3).toLongOrNull() ?: 0L
            }
            val time = (min * 60_000 + sec * 1000 + frac + offsetMs).coerceAtLeast(0)

            if (!MetadataFilter.isMetadataLine(text, time, title, artist)) {
                lines.add(LyricLine(timeMs = time, text = text))
            }
        }
    }
    return lines.sortedBy { it.timeMs }
}

/**
 * 自动识别格式并解析歌词
 */
fun parseAutoLyrics(content: String?, title: String = "", artist: String = ""): List<LyricLine> {
    if (content.isNullOrBlank()) return emptyList()
    val trimmed = content.trim()
    return when {
        trimmed.contains(Regex("""\[\d+,\d+]\(\d+,\d+""")) -> parseYrc(trimmed, title, artist)
        trimmed.contains(Regex("""\[\d+,\d+]<\d+,\d+""")) -> parseKrc(trimmed, title, artist)
        else -> parseLrc(trimmed, title, artist)
    }
}

/**
 * 二分查找高效合并原文与翻译歌词
 * 时间差 1.2 秒内的翻译行对齐拼接到原文（显示为两行）
 */
fun mergeTranslation(original: List<LyricLine>, translation: List<LyricLine>?): List<LyricLine> {
    if (translation.isNullOrEmpty()) return original
    val sortedTrans = translation.sortedBy { it.timeMs }

    return original.map { orig ->
        var low = 0
        var high = sortedTrans.size - 1
        var bestIndex = -1
        var minDelta = Long.MAX_VALUE

        while (low <= high) {
            val mid = (low + high) ushr 1
            val transLine = sortedTrans[mid]
            val delta = kotlin.math.abs(transLine.timeMs - orig.timeMs)

            if (delta < minDelta) {
                minDelta = delta
                bestIndex = mid
            }

            when {
                transLine.timeMs < orig.timeMs -> low = mid + 1
                transLine.timeMs > orig.timeMs -> high = mid - 1
                else -> break
            }
        }

        val matchedTrans = if (bestIndex != -1 && minDelta <= 1200L) {
            sortedTrans[bestIndex].text
        } else null

        if (matchedTrans != null) orig.copy(translated = matchedTrans) else orig
    }
}


/**
 * 二分查找根据当前播放位置找到当前高亮歌词下标，O(log N) 复杂度
 */
fun currentLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty() || positionMs < lines.first().timeMs) return -1
    if (positionMs >= lines.last().timeMs) return lines.size - 1

    var low = 0
    var high = lines.size - 1
    var result = -1

    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].timeMs <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

// ============================================================
// 逐字（karaoke）能力
// ============================================================

/**
 * 把逐字歌词（YRC）的**字级时间**贴到已有的 [LyricLine] 上。
 *
 * 为什么不是直接拿 YRC 当歌词：YRC 的文本是按字拼接的，标点会被拆到单独的字里，
 * 有时还会把「说 」这类尾随空格留在字里；而同一首歌的 LRC 文本是完整标点句。
 * 播放层要的是「LRC 的文本 + YRC 的时间」，所以这里按**行起始时间**把两者对齐：
 * 时间差 ≤ [toleranceMs] 视为同一行，直接把 words 搬过去。
 *
 * ## 没有字时间的歌一律按「逐句」处理（重要）
 * 早期版本为了「让每首歌都能逐字」，给纯 LRC 补过一套按字数估算的假字时间。
 * 结果反而更糟：估算的吐字速度与真实演唱对不上（一句 10 个词两秒唱完，染色才走到一半），
 * 用户看到的是「歌词和声音在句子内对不齐」。现在只认**真实返回的逐字数据**：
 * YRC 有就逐字，没有就老实逐句——对得齐比看着花哨重要。
 *
 * @param lines 已解析好的 LRC 行（文本/翻译都在这上面）
 * @param yrc   网易云 YRC 原文；为空时原样返回
 */
fun attachWordTimings(
    lines: List<LyricLine>,
    yrc: String?,
    title: String = "",
    artist: String = "",
    toleranceMs: Long = 450L,
): List<LyricLine> {
    if (lines.isEmpty() || yrc.isNullOrBlank()) return lines
    val wordLines = parseYrc(yrc, title, artist).filter { it.words.isNotEmpty() }
    if (wordLines.isEmpty()) return lines

    return lines.map { line ->
        val match = wordLines.minByOrNull { kotlin.math.abs(it.timeMs - line.timeMs) }
        val delta = match?.let { kotlin.math.abs(it.timeMs - line.timeMs) } ?: Long.MAX_VALUE
        if (match != null && delta <= toleranceMs) {
            line.copy(
                words = match.words,
                durationMs = match.durationMs.takeIf { it > 0 } ?: line.durationMs,
            )
        } else {
            line
        }
    }
}

/**
 * 逐字染色的「已唱长度」——返回 0f..1f 的**宽度占比**。
 *
 * 为什么按「字数」而不是「时间」算占比：染色是按屏幕宽度从左往右擦过去的，
 * 用时间占比会在字宽不等时出现「字还没唱到、颜色已经盖过去」的错位。
 *
 * 只有 [LyricLine.words] 非空（即真有逐字数据）时才可能返回 > 0；
 * 逐句歌词没有字时间，界面走普通文本渲染，不会调用到这里。
 */
fun karaokeSweepFraction(line: LyricLine, positionMs: Long): Float {
    val words = line.words
    if (words.isEmpty()) return 0f
    val totalChars = words.sumOf { it.text.length }.coerceAtLeast(1)
    var sungChars = 0f
    for (word in words) {
        when {
            positionMs >= word.endMs -> sungChars += word.text.length
            positionMs <= word.startMs -> break
            else -> {
                val span = (word.endMs - word.startMs).coerceAtLeast(1L)
                val p = ((positionMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
                sungChars += word.text.length * p
                break
            }
        }
    }
    return (sungChars / totalChars).coerceIn(0f, 1f)
}
