package com.aesthetic.music.player

/**
 * 媒体卡片（通知栏 / 锁屏 / 超级岛）上文字与进度的组装规则。
 *
 * 单独抽成纯函数的原因：这些文案在三个地方要一致——通知卡片、悬浮胶囊、设置页预览，
 * 谁都不能自己拼一套；同时它们是纯逻辑，可以在 JVM 单测里逐条钉住（不依赖 Android 框架）。
 */
object MediaCardInfo {

    /** 卡片按钮种类（顺序即渲染顺序） */
    enum class ActionKind { PREVIOUS, PLAY_PAUSE, NEXT, FAVORITE }

    /**
     * 卡片按钮顺序：**三个播放控制永远在前，收藏按钮永远在最后**。
     *
     * 这条规则来自一次实测反馈：卡片上"有上一首、没有下一首"。
     * 原因是收藏按钮可以在折叠态抢到前面的按钮位，而系统媒体卡片只渲染前几个按钮，
     * 于是"下一首"被挤出去、静默消失。播放控制是刚需，收藏是锦上添花——顺序不允许被设置改坏。
     */
    fun actionOrder(showFavorite: Boolean): List<ActionKind> = buildList {
        add(ActionKind.PREVIOUS)
        add(ActionKind.PLAY_PAUSE)
        add(ActionKind.NEXT)
        if (showFavorite) add(ActionKind.FAVORITE)
    }

    /**
     * 折叠态要显示哪几个按钮的下标：固定取前三个（播放控制）。
     * 收藏按钮即使存在也不进入折叠态，避免挤掉"下一首"。
     */
    fun compactIndices(actionCount: Int): IntArray =
        IntArray(actionCount.coerceIn(0, 3)) { it }

    /** 音质短标签：卡片空间宝贵，用两三个字表示 */
    fun qualityLabel(quality: String?): String? = when (quality?.lowercase()) {
        null, "" -> null
        "standard", "128k" -> "标准"
        "high", "320k", "hq" -> "高品质"
        "lossless", "flac" -> "无损"
        "hires", "flac24bit", "hi-res" -> "Hi-Res"
        else -> quality
    }

    /**
     * 信息行：`专辑 · 音质 · 音源`，各自为空则跳过；全空返回 null（不显示这一行）。
     *
     * @param showAlbum 关闭时只保留音质与音源——专辑名太长会把卡片撑满
     */
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

    /** 歌手行：歌手为空时用"未知歌手"，避免卡片上出现空白行 */
    fun artistLine(artist: String?): String =
        artist?.trim()?.takeIf { it.isNotEmpty() } ?: "未知歌手"

    /** 进度文案 `01:23 / 04:05`；时长未知时只显示已播放 */
    fun progressText(positionMs: Long, durationMs: Long): String {
        val pos = clock(positionMs)
        return if (durationMs > 0) "$pos / ${clock(durationMs)}" else pos
    }

    /** 剩余时间文案，胶囊展开态用 `-02:42` 更省地方 */
    fun remainingText(positionMs: Long, durationMs: Long): String =
        if (durationMs > 0) "-${clock((durationMs - positionMs).coerceAtLeast(0))}" else ""

    /**
     * 进度比例（0..1）。
     *
     * 时长未知（0 或负）时返回 0 而不是 NaN —— 直接相除会得到 NaN，
     * 传给 ProgressBar/通知进度会把控件画成异常状态。
     */
    fun progressFraction(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0) return 0f
        return (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Android 16 实时活动的状态栏胶囊文案。
     *
     * 状态栏只有几十 dp 宽，能放下一句歌词就不放歌名；两者都没有时返回 null，
     * 此时系统会退回使用通知标题。
     *
     * 返回 String（不是 CharSequence）：`setShortCriticalText` 只接受 String。
     */
    fun shortCriticalText(lyric: String?, title: String?): String? {
        val line = lyric?.trim()?.takeIf { it.isNotEmpty() }
        if (line != null) return line
        return title?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** mm:ss，超过一小时补上小时位；负数按 0 处理 */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0)) / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
