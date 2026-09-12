package com.aesthetic.music.data.source

import com.aesthetic.music.data.settings.UserPrefs

/**
 * 音源挑选策略（纯函数，可单测）。
 *
 * 三个开关的语义在这里落地，界面上的说明文案必须与这段实现一一对应，
 * 否则用户开了开关却感觉"没用"：
 *
 * - `sourceFailover` 失败换源：源 A 解析失败时，自动按顺序试下一个源（不开启则只试当前源）。
 * - `sourceSmartSwitch` 智能切换：不必等到失败——若当前源最近被判定为失败或明显偏慢，
 *   直接跳过它改用更优的源（开启后容忍的慢阈值生效）。
 * - `sourceFastest` 自动按照最快的进行使用：按**实测速度**给候选排序，永远用最快的那一个。
 *
 * 三者可以同时开：最快的源排在第一位，慢源被跳过，失败时继续往后换。
 */
object SourcePolicy {

    /** 一次解析最多尝试的插件数（不开启失败换源时为 1） */
    const val MAX_ATTEMPTS = 3

    /**
     * 选出可用于该歌曲、且按策略排好序的候选插件。
     *
     * @param plugins 全部已安装插件
     * @param songPlatform 歌曲的平台（应用平台 code）
     * @param probes 检测/实测结果，key 为 [SourceProbe.pluginTarget]
     * @param activeId 用户手动指定的当前音源（策略未开启时优先它）
     */
    fun candidates(
        plugins: List<SourcePlugin>,
        songPlatform: String,
        probes: Map<String, SourceProbe>,
        prefs: UserPrefs,
        activeId: String? = null,
    ): List<SourcePlugin> {
        val applicable = plugins.filter { it.enable && it.appliesTo(songPlatform) }
        if (applicable.isEmpty()) return emptyList()

        val platform = SourcePlatform.of(songPlatform)
        fun probeOf(p: SourcePlugin): SourceProbe? =
            platform?.let { probes[SourceProbe.pluginTarget(p.id, it)] }

        // 智能切换：剔除"明确坏掉"的源；但如果全被剔除干净，就一个都不剔（宁可用坏源也别不播）
        val filtered = if (prefs.sourceSmartSwitch) {
            val healthy = applicable.filter { p ->
                val probe = probeOf(p) ?: return@filter true  // 没数据视为未知，保留
                when (probe.status) {
                    ProbeStatus.FAIL -> false
                    ProbeStatus.SLOW -> false
                    else -> true
                }
            }
            healthy.ifEmpty { applicable }
        } else {
            applicable
        }

        val ordered = when {
            // 自动最快：按实测速度（检测值优先，其次播放实测均值）
            prefs.sourceFastest -> filtered.sortedBy { p ->
                probeOf(p)?.speedScoreMs ?: SourceProbe.UNKNOWN_LATENCY_MS
            }

            // 否则按"手动指定 → 最近使用 → 导入时间"
            else -> filtered.sortedWith(
                compareByDescending<SourcePlugin> { it.id == activeId }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.importedAt }
            )
        }

        return if (prefs.sourceFailover) ordered.take(MAX_ATTEMPTS) else ordered.take(1)
    }

    /** 是否应该继续尝试下一个源（失败换源关闭时只试一次） */
    fun shouldContinueAfterFailure(prefs: UserPrefs): Boolean = prefs.sourceFailover

    /**
     * 内置平台直连是否可以兜底。
     *
     * 内置直连是"没插件也能出个声"的最后一道防线（见 PlatformMusicService.getDirectSongUrl 的实现说明），
     * 因此不提供关闭开关——关掉它只会让用户遇到"明明有网络却什么都放不了"。
     */
    fun builtinFallbackAvailable(songPlatform: String): Boolean =
        SourcePlatform.of(songPlatform)?.hasBuiltin == true
}
