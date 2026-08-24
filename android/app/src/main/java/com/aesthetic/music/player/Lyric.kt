package com.aesthetic.music.player

/** 单行歌词（带时间戳；translated 非空时显示原文+翻译两行） */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translated: String? = null,
)

/**
 * 解析 LRC 歌词为带时间戳的歌词行。
 * 支持 "[mm:ss.xx]文本" 或 "[mm:ss]文本"，支持一行多个时间标签。
 */
fun parseLrc(lrc: String?): List<LyricLine> {
    if (lrc.isNullOrBlank()) return emptyList()
    val regex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    val lines = mutableListOf<LyricLine>()
    for (raw in lrc.lineSequence()) {
        val matches = regex.findAll(raw).toList()
        if (matches.isEmpty()) continue
        val text = raw.replace(regex, "").trim()
        if (text.isEmpty()) continue
        for (m in matches) {
            val min = m.groupValues[1].toLongOrNull() ?: continue
            val sec = m.groupValues[2].toLongOrNull() ?: continue
            val fracStr = m.groupValues[3]
            // 分数位按位数补齐到毫秒：".5"=500ms（十分之一秒）、".50"=500ms（百分秒）、".500"=500ms。
            // 原实现对一位小数走 take(3) 直接得 5ms，歌词会提前约半秒高亮
            val frac = when (fracStr.length) {
                0 -> 0L
                1 -> fracStr.toLongOrNull()?.times(100) ?: 0L
                2 -> fracStr.toLongOrNull()?.times(10) ?: 0L
                else -> fracStr.take(3).toLongOrNull() ?: 0L
            }
            lines.add(LyricLine(min * 60_000 + sec * 1000 + frac, text))
        }
    }
    return lines.sortedBy { it.timeMs }
}

/**
 * 合并原文与翻译歌词（移植自参考 App 的 mergeTranslation）：
 * 时间差 1 秒内的翻译行拼接到原文（显示为两行）。
 */
fun mergeTranslation(original: List<LyricLine>, translation: List<LyricLine>?): List<LyricLine> {
    if (translation.isNullOrEmpty()) return original
    val result = mutableListOf<LyricLine>()
    for (o in original) {
        var best: LyricLine? = null
        var bestDelta = Long.MAX_VALUE
        for (t in translation) {
            val delta = kotlin.math.abs(t.timeMs - o.timeMs)
            if (delta < 1000 && delta < bestDelta) {
                bestDelta = delta
                best = t
            }
        }
        result.add(if (best != null) o.copy(translated = best.text) else o)
    }
    return result
}

/** 根据当前播放位置找到当前高亮歌词下标 */
fun currentLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var idx = -1
    for (i in lines.indices) {
        if (positionMs >= lines[i].timeMs) idx = i else break
    }
    return idx
}
