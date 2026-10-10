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
        "Mixed by", "Mastered by", "演唱", "出品", "发行", "OP", "SP"
    )

    fun isMetadataLine(rawText: String, startMs: Long = 0L, title: String = "", artist: String = ""): Boolean {
        val clean = rawText.replace(Regex("""[（(\[【].*?[）)\]】]"""), "").trim()
        if (clean.isBlank()) return true
        for (kw in FILTER_KEYWORDS) {
            if (clean.startsWith(kw, ignoreCase = true)) return true
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
 * 二分查找高效合并罗马音歌词
 */
fun mergeRomanization(original: List<LyricLine>, romanization: List<LyricLine>?): List<LyricLine> {
    if (romanization.isNullOrEmpty()) return original
    val sortedRoma = romanization.sortedBy { it.timeMs }

    return original.map { orig ->
        var low = 0
        var high = sortedRoma.size - 1
        var bestIndex = -1
        var minDelta = Long.MAX_VALUE

        while (low <= high) {
            val mid = (low + high) ushr 1
            val romaLine = sortedRoma[mid]
            val delta = kotlin.math.abs(romaLine.timeMs - orig.timeMs)

            if (delta < minDelta) {
                minDelta = delta
                bestIndex = mid
            }

            when {
                romaLine.timeMs < orig.timeMs -> low = mid + 1
                romaLine.timeMs > orig.timeMs -> high = mid - 1
                else -> break
            }
        }

        val matchedRoma = if (bestIndex != -1 && minDelta <= 1200L) {
            sortedRoma[bestIndex].text
        } else null

        if (matchedRoma != null) orig.copy(romanization = matchedRoma) else orig
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
