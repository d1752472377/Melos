package com.aesthetic.music.player

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

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

object MetadataFilter {
    private val FILTER_KEYWORDS = listOf(
        "作词", "作曲", "编曲", "词：", "曲：", "制作人", "混音", "母带",
        "监制", "和声", "合声", "录音", "吉他", "钢琴", "贝斯", "鼓手",
        "弦乐", "统筹", "Program", "Lyrics", "Music by", "Produced by",
        "Mixed by", "Mastered by", "演唱", "出品", "发行"
    )

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

fun parseLrc(lrc: String?, title: String = "", artist: String = ""): List<LyricLine> {
    if (lrc.isNullOrBlank()) return emptyList()

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

fun parseAutoLyrics(content: String?, title: String = "", artist: String = ""): List<LyricLine> {
    if (content.isNullOrBlank()) return emptyList()
    val trimmed = content.trim()
    return when {
        trimmed.contains(Regex("""\[\d+,\d+]\(\d+,\d+""")) -> parseYrc(trimmed, title, artist)
        trimmed.contains(Regex("""\[\d+,\d+]<\d+,\d+""")) -> parseKrc(trimmed, title, artist)
        else -> parseLrc(trimmed, title, artist)
    }
}

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
