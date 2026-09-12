package com.aesthetic.music.player

/**
 * 媒体卡片（通知栏 / 锁屏 / 超级岛）上文字与按钮顺序的组装规则。
 *
 * 单独抽成纯函数的原因：这些文案要一致——通知卡片与悬浮胶囊读同一套规则，
 * 谁都不能自己拼一套；同时它们是纯逻辑，可以在 JVM 单测里逐条钉住（不依赖 Android 框架）。
 *
 * 已删除（本次"卡片设置整页下线"时一并清理）：`progressFraction` / `progressText` /
 * `remainingText` / `clock` / `compactIndices` —— 这些函数在改版后主代码里**已经没有任何调用方**，
 * 只剩下单测在"测试自己"（看起来有覆盖，实际功能并不存在）。进度与时间文案现在由
 * 系统媒体卡片按会话状态自己绘制，App 侧不再各拼一份。
 */
object MediaCardInfo {

    /** 卡片按钮种类（顺序即渲染顺序） */
    enum class ActionKind { PREVIOUS, PLAY_PAUSE, NEXT }

    /**
     * 卡片按钮顺序：**上一首 → 播放/暂停 → 下一首**，队列只有一首时只留播放/暂停。
     *
     * 这条规则来自实测反馈：系统媒体卡片只渲染前几个按钮，任何"排在播放控制前面"的按钮
     * 都会把"下一首"挤出行外（此前收藏按钮就是这么消失的）。
     * 现在卡片没有收藏按钮，但顺序仍然由这里统一给出并有单测覆盖，
     * 防止以后有人再往前面塞按钮。
     *
     * 队列只有一首时不给出上一首/下一首：对应的播放命令本来就不可用
     * （见 [MelosSessionPlayer]），画出来也是点了没反应的假按钮。
     */
    fun transportKinds(canSwitchQueue: Boolean): List<ActionKind> = buildList {
        if (canSwitchQueue) add(ActionKind.PREVIOUS)
        add(ActionKind.PLAY_PAUSE)
        if (canSwitchQueue) add(ActionKind.NEXT)
    }

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

    /**
     * Android 16 实时活动的状态栏胶囊文案。
     *
     * 状态栏只有几十 dp 宽，放歌名最合适；没有歌名时返回 null，此时系统会退回使用通知标题。
     *
     * 返回 String（不是 CharSequence）：`setShortCriticalText` 只接受 String。
     */
    fun shortCriticalText(title: String?): String? =
        title?.trim()?.takeIf { it.isNotEmpty() }
}
