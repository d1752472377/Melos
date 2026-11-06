package com.aesthetic.music.player

object MediaCardInfo {

    enum class ActionKind { PREVIOUS, PLAY_PAUSE, NEXT }

    fun transportKinds(canSwitchQueue: Boolean): List<ActionKind> = buildList {
        if (canSwitchQueue) add(ActionKind.PREVIOUS)
        add(ActionKind.PLAY_PAUSE)
        if (canSwitchQueue) add(ActionKind.NEXT)
    }

    fun qualityLabel(quality: String?): String? = when (quality?.lowercase()) {
        null, "" -> null
        "standard", "128k" -> "标准"
        "high", "320k", "hq" -> "高品质"
        "lossless", "flac" -> "无损"
        "hires", "flac24bit", "hi-res" -> "Hi-Res"
        else -> quality
    }

    fun infoLine(
        album: String?,
        quality: String?,
        sourceName: String?,
        showAlbum: Boolean = true,
    ): String? {
        val parts = buildList {
            if (showAlbum) album?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            qualityLabel(quality)?.let { add(it) }
            sourceName?.trim()?.takeIf { it.isNotEmpty() && it != "未知音源" }?.let { add(it) }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun artistLine(artist: String?): String =
        artist?.trim()?.takeIf { it.isNotEmpty() } ?: "未知歌手"

    fun shortCriticalText(title: String?): String? =
        title?.trim()?.takeIf { it.isNotEmpty() }
}
