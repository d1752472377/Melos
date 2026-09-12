package com.aesthetic.music.data.source

/**
 * 应用内可播放的平台。
 *
 * `code` 是应用全流程（搜索/歌曲/收藏/历史）使用的平台标识，
 * `lxCode` 是 LX Music 音源脚本规范里的源标识——两者不同名（如应用用 `netease`、脚本用 `wy`），
 * 脚本调用与平台显示都必须经过这里的映射，散落各处就会出现"某个平台永远解析失败"的问题。
 */
enum class SourcePlatform(val code: String, val label: String, val lxCode: String) {
    NETEASE("netease", "网易云音乐", "wy"),
    QQ("qq", "QQ 音乐", "tx"),
    KUGOU("kugou", "酷狗音乐", "kg"),
    KUWO("kuwo", "酷我音乐", "kw"),
    MIGU("migu", "咪咕音乐", "mg");

    /** 是否有内置直连搜索实现（咪咕没有，只能靠脚本，因此无法自动检测） */
    val hasBuiltin: Boolean get() = this != MIGU

    companion object {
        /** 允许出现在歌曲数据里的平台（内置直连覆盖的四个） */
        val builtins: List<SourcePlatform> = entries.filter { it.hasBuiltin }

        fun of(code: String?): SourcePlatform? {
            if (code.isNullOrBlank()) return null
            val key = code.trim().lowercase()
            return entries.firstOrNull { it.code == key || it.lxCode == key }
        }

        /** 平台显示名；未知平台按原样返回，便于把脚本声明的陌生源如实展示出来 */
        fun labelOf(code: String?): String =
            of(code)?.label ?: code?.takeIf { it.isNotBlank() } ?: "未知平台"
    }
}

/**
 * 已安装的音源插件（一个脚本一份记录）。
 *
 * @param targetPlatform 用户在导入时指定的平台（应用平台 code）；空串表示"通用"。
 *        指定平台后，播放该平台的歌曲时优先使用它，也让"智能切换/最快优先"有了挑选依据。
 * @param declaredSources 脚本自报支持的 LX 源（导入/激活时从沙箱 init 事件抓取），
 *        用于音源列表与检测：脚本到底支持哪些平台，以脚本自己的声明为准，而不是我们的猜测。
 * @param enable 是否参与解析（关掉不等于卸载，脚本与配置都保留）
 */
data class SourcePlugin(
    val id: String = "",
    val name: String = "自定义音源",
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val homepage: String = "",
    val targetPlatform: String = "",
    val declaredSources: List<String> = emptyList(),
    val enable: Boolean = true,
    val importedAt: Long = 0L,
    val lastUsedAt: Long = 0L,
) {
    /** 展示用适用平台文案 */
    val targetLabel: String
        get() = when {
            targetPlatform.isBlank() -> "全平台通用"
            else -> "指定：" + SourcePlatform.labelOf(targetPlatform)
        }

    /** 该插件是否适用于某个平台（未指定平台即通用） */
    fun appliesTo(platformCode: String): Boolean =
        targetPlatform.isBlank() || targetPlatform == platformCode

    /** 脚本声明的平台（转成应用平台枚举，忽略未知源） */
    val declaredPlatforms: List<SourcePlatform>
        get() = declaredSources.mapNotNull { SourcePlatform.of(it) }.distinct()
}

/** 检测结论 */
enum class ProbeStatus(val label: String) {
    /** 请求成功且数据可用 */
    OK("正常"),

    /** 请求成功但明显偏慢 */
    SLOW("偏慢"),

    /** 请求失败 / 返回内容不可用 */
    FAIL("失败"),

    /** 该平台缺少内置搜索入口，无法自动取证 */
    UNTESTABLE("无法自动检测"),

    /** 还没检测过 */
    UNKNOWN("未检测"),
}

/**
 * 一次检测的结果。
 *
 * @param target 检测目标 id：`builtin:<平台>` 或 `plugin:<插件id>:<平台>`
 * @param latencyMs 检测实测耗时（毫秒）；未检测时为 -1
 * @param observedMs 播放过程中的实测均值（毫秒）；-1 表示还没有播放样本。
 *        与 [latencyMs] 分开存：检测是"用户主动测的一次"，播放实测是"真实使用的长期表现"，
 *        两者混在一起会让界面上的数字来历不明；"自动最快"在缺少检测结果时用实测均值兜底。
 */
data class SourceProbe(
    val target: String = "",
    val status: ProbeStatus = ProbeStatus.UNKNOWN,
    val latencyMs: Long = -1L,
    val observedMs: Long = -1L,
    val observedCount: Int = 0,
    val detail: String = "",
    val checkedAt: Long = 0L,
) {
    val ok: Boolean get() = status == ProbeStatus.OK || status == ProbeStatus.SLOW

    /** 列表里的速度徽章文案 */
    val latencyLabel: String
        get() = when {
            latencyMs < 0 -> "—"
            latencyMs < 1000 -> "${latencyMs}ms"
            else -> String.format("%.1fs", latencyMs / 1000.0)
        }

    /**
     * 排序用的速度估计：优先检测值，其次播放实测均值，都没有则给一个中性值，
     * 让"还没测过的新插件"仍有机会被使用，而不是被永久排在最后。
     */
    val speedScoreMs: Long
        get() = when {
            latencyMs >= 0 -> latencyMs
            observedMs >= 0 -> observedMs
            else -> UNKNOWN_LATENCY_MS
        }

    companion object {
        fun builtinTarget(platform: SourcePlatform) = "builtin:${platform.code}"

        fun pluginTarget(pluginId: String, platform: SourcePlatform) = "plugin:$pluginId:${platform.code}"

        /** 判定速度档位：超过 [SLOW_THRESHOLD_MS] 视为偏慢（用于"智能切换"跳过慢源） */
        const val SLOW_THRESHOLD_MS = 2500L

        /** 没有任何速度数据时的中性估值（约等于一次普通网络请求） */
        const val UNKNOWN_LATENCY_MS = 1500L

        fun statusOf(latencyMs: Long, ok: Boolean): ProbeStatus = when {
            !ok -> ProbeStatus.FAIL
            latencyMs >= SLOW_THRESHOLD_MS -> ProbeStatus.SLOW
            else -> ProbeStatus.OK
        }
    }
}
