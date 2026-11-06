package com.aesthetic.music.data.source

import com.aesthetic.music.data.model.Song

object SongMatcher {

    fun diceCoefficient(s1: String, s2: String): Float {
        val clean1 = normalize(s1)
        val clean2 = normalize(s2)

        if (clean1.isEmpty() && clean2.isEmpty()) return 1.0f
        if (clean1.isEmpty() || clean2.isEmpty()) return 0.0f
        if (clean1 == clean2) return 1.0f
        if (clean1.length < 2 || clean2.length < 2) {
            return if (clean1.contains(clean2) || clean2.contains(clean1)) 0.85f else 0.0f
        }

        val bigrams1 = mutableMapOf<String, Int>()
        for (i in 0 until clean1.length - 1) {
            val bg = clean1.substring(i, i + 2)
            bigrams1[bg] = (bigrams1[bg] ?: 0) + 1
        }

        var matches = 0
        var total2 = 0
        for (i in 0 until clean2.length - 1) {
            val bg = clean2.substring(i, i + 2)
            total2++
            val count = bigrams1[bg] ?: 0
            if (count > 0) {
                matches++
                bigrams1[bg] = count - 1
            }
        }

        val total1 = clean1.length - 1
        return (2f * matches) / (total1 + total2)
    }

    fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("""[（(\[【].*?[）)\]】]"""), "")
            .replace(Regex("""(?i)\b(feat\.?|ft\.?|remix|live|instrumental|伴奏)\b.*"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), "")
            .trim()
    }

    fun calculateScore(target: Song, candidate: Song): Float {
        val titleScore = diceCoefficient(target.title, candidate.title)
        val artistScore = diceCoefficient(target.artist, candidate.artist)

        val albumScore = if (target.album.isNotBlank() && candidate.album.isNotBlank()) {
            diceCoefficient(target.album, candidate.album)
        } else {
            0.5f
        }

        val baseScore = titleScore * 0.60f + artistScore * 0.30f + albumScore * 0.10f
        return baseScore.coerceIn(0.0f, 1.0f)
    }

    fun findBestMatch(
        target: Song,
        candidates: List<Song>,
        minScore: Float = 0.75f
    ): Pair<Song, Float>? {
        var bestCandidate: Song? = null
        var maxScore = 0.0f

        for (cand in candidates) {
            if (cand.platform == target.platform && cand.id == target.id) continue
            val score = calculateScore(target, cand)
            if (score > maxScore && score >= minScore) {
                maxScore = score
                bestCandidate = cand
            }
        }

        return bestCandidate?.let { it to maxScore }
    }
}
