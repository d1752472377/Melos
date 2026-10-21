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

// ============================================================
// 逐字（karaoke）能力：真实字时间 + 无字时间时的近似
// ============================================================

/** 一句歌词最长按多少毫秒铺满（防止两句之间隔了 20 秒时「最后一个字唱了 20 秒」） */
private const val MAX_LINE_SPAN_MS = 9_000L

/**
 * 把逐字歌词（YRC/KRC）的**字级时间**贴到已有的 [LyricLine] 上。
 *
 * 为什么不是直接拿 YRC 当歌词：YRC 的文本是按字拼接的，标点会被拆到单独的字里，
 * 有时还会把「说 」这类尾随空格留在字里；而同一首歌的 LRC 文本是完整标点句。
 * 播放层要的是「LRC 的文本 + YRC 的时间」，所以这里按**行起始时间**把两者对齐：
 * 时间差 ≤ [WORD_MATCH_TOLERANCE_MS] 视为同一行，直接把 words 搬过去。
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
 * 给「只有行时间、没有字时间」的歌词补一套**近似**字时间，让逐字染色在任何歌上都能用。
 *
 * 做法（与主流播放器对纯 LRC 的处理一致）：把这一行按「唱这一行所用的时间」均分给每个字。
 *  - 时长优先取 [LyricLine.durationMs]（YRC 给的），否则用「下一行起始 − 本行起始」，
 *    并夹在 [MIN_LINE_SPAN_MS] ~ [MAX_LINE_SPAN_MS] 之间，避免整段间奏被当成一个字的时长；
 *  - 分词规则：CJK 逐字；连续的拉丁字母/数字算一个词（保住英文单词不被拆散、也不破坏字距）。
 *
 * 这是**近似**，不是真值：真字时间只有 YRC/KRC 才有。但它让「逐字」这个交互在
 * 非网易云音源、以及没有逐字歌词的老歌上同样成立，而不是整页退化成「逐句」。
 */
fun withPseudoWords(lines: List<LyricLine>): List<LyricLine> {
    if (lines.isEmpty()) return lines
    return lines.mapIndexed { index, line ->
        if (line.words.isNotEmpty()) return@mapIndexed line
        val tokens = tokenizeForKaraoke(line.text)
        if (tokens.isEmpty()) return@mapIndexed line

        // 关键：**按字数估时长，而不是按「到下一句的间隔」**
        //
        // 早期实现用 nextStart - timeMs 当这一行的时长，结果就是用户报的现象：
        // 一句 10 个词两秒唱完，而到下一句还有 4 秒，染色走到一半这句就换了。
        // 现在按「每个字/词大约唱多久」累加：中文 ~170ms/字、英文 ~120ms + 20ms×字母数，
        // 再把结果夹在 [MIN_LINE_SPAN_MS, MAX_LINE_SPAN_MS] 且不超过行间隔。
        val estimated = tokens.sumOf { it.ms }.coerceAtLeast(MIN_LINE_SPAN_MS)
        val nextStart = lines.getOrNull(index + 1)?.timeMs
        val gap = if (nextStart != null && nextStart > line.timeMs) nextStart - line.timeMs else Long.MAX_VALUE
        val explicit = line.durationMs.takeIf { it > 0 }
        val span = (explicit ?: minOf(estimated, gap))
            .coerceIn(MIN_LINE_SPAN_MS, MAX_LINE_SPAN_MS)

        // 按最终 span 等比缩放每个字的时长：这样「估算」与「被夹紧后的 span」永远一致，
        // 不会出现「字铺到下一句去了、最后一个字只剩 1ms」的错位
        val scale = span.toFloat() / estimated.toFloat()
        var cursor = line.timeMs
        val words = ArrayList<LyricWord>(tokens.size)
        tokens.forEachIndexed { i, token ->
            val isLast = i == tokens.lastIndex
            val spanForToken = if (isLast) {
                (line.timeMs + span - cursor).coerceAtLeast(1L)
            } else {
                (token.ms * scale).toLong().coerceAtLeast(1L)
            }
            words += LyricWord(
                startMs = cursor,
                endMs = cursor + spanForToken,
                text = token.text,
            )
            cursor += spanForToken
        }
        line.copy(words = words, durationMs = span)
    }
}

/** 一行最短按多少毫秒铺满（一行只唱一瞬间时，字与字之间仍要有可感知的推进） */
private const val MIN_LINE_SPAN_MS = 600L

private data class KaraokeToken(val text: String, val ms: Long)

/**
 * 把一行歌词切成「逐字染色」的单元：
 *  - CJK 逐字（每个字权重 1）；
 *  - 连续的 ASCII 字母/数字/符号算一个词（权重按长度，英文词念得比单字久）；
 *  - 空白并入前一个单元，避免染色时出现「空隙跳一下」。
 */
private fun tokenizeForKaraoke(text: String): List<KaraokeToken> {
    if (text.isBlank()) return emptyList()
    val out = ArrayList<KaraokeToken>(text.length)
    val latin = StringBuilder()

    fun flushLatin() {
        if (latin.isNotEmpty()) {
            val s = latin.toString()
            // 英文词：基础 120ms + 每个字母 20ms（Don't≈220ms / Now≈180ms，10 个词约 2 秒）
            out += KaraokeToken(s, (120 + 20 * s.trim().length).toLong().coerceIn(120L, 420L))
            latin.setLength(0)
        }
    }

    for (ch in text) {
        when {
            ch.isCjk() -> {
                flushLatin()
                // 中文/日文/韩文：一个字约 170ms（流行歌的正常吐字速度）
                out += KaraokeToken(ch.toString(), 170L)
            }
            ch.isWhitespace() -> {
                if (latin.isNotEmpty()) {
                    // 空白跟在这个英文词后面，然后收词——否则整行英文会被拼成一个词
                    latin.append(ch)
                    flushLatin()
                } else if (out.isNotEmpty()) {
                    // 空白挂到上一个字上，染色边界不会出现裂缝
                    val last = out.removeAt(out.lastIndex)
                    out += last.copy(text = last.text + ch)
                }
            }
            else -> latin.append(ch)
        }
    }
    flushLatin()
    return out
}

private fun Char.isCjk(): Boolean =
    this in '\u4E00'..'\u9FFF' || // 基本汉字
            this in '\u3400'..'\u4DBF' || // 扩展 A
            this in '\u3040'..'\u30FF' || // 日文假名
            this in '\uAC00'..'\uD7AF'    // 韩文音节

/**
 * 当前这一行「已经唱了多少」——0f 到 1f，供逐字染色用。
 *
 * 有真字时间时按字推进（字内线性插值），因此逐字染色与真实演唱对齐；
 * 没有字时间时按整行时长线性推进（[withPseudoWords] 已经补了近似字时间，
 * 所以这条分支只用于「连行时长都没有」的极端情况）。
 */
fun karaokeProgress(line: LyricLine, positionMs: Long): Float {
    val words = line.words
    if (words.isEmpty()) {
        val span = line.durationMs.takeIf { it > 0 } ?: return 0f
        return ((positionMs - line.timeMs).toFloat() / span).coerceIn(0f, 1f)
    }
    if (positionMs <= words.first().startMs) return 0f
    if (positionMs >= words.last().endMs) return 1f

    val total = (words.last().endMs - words.first().startMs).coerceAtLeast(1L).toFloat()
    var sung = 0f
    for (word in words) {
        when {
            positionMs >= word.endMs -> sung += (word.endMs - word.startMs).toFloat()
            positionMs <= word.startMs -> break
            else -> {
                sung += (positionMs - word.startMs).toFloat()
                break
            }
        }
    }
    return (sung / total).coerceIn(0f, 1f)
}

/**
 * 逐字染色的「已唱长度」——返回 0f..1f 的**宽度占比**。
 *
 * 与 [karaokeProgress] 的区别：那个按「时间占比」算，这个按「字数占比」算。
 * 逐字扫描是按屏幕宽度从左往右擦过去的，字宽不等时用时间占比会出现
 * 「字还没唱到、颜色已经盖过去」的错位，因此染色要用字数占比。
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

