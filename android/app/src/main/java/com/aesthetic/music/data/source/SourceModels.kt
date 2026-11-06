package com.aesthetic.music.data.source

enum class SourcePlatform(val code: String, val label: String, val lxCode: String) {
    NETEASE("netease", "网易云音乐", "wy"),
    QQ("qq", "QQ 音乐", "tx"),
    KUGOU("kugou", "酷狗音乐", "kg"),
    KUWO("kuwo", "酷我音乐", "kw"),
    MIGU("migu", "咪咕音乐", "mg");

    val hasBuiltin: Boolean get() = this != MIGU

    companion object {
        val builtins: List<SourcePlatform> = entries.filter { it.hasBuiltin }

        fun of(code: String?): SourcePlatform? {
            if (code.isNullOrBlank()) return null
            val key = code.trim().lowercase()
            return entries.firstOrNull { it.code == key || it.lxCode == key }
        }

        fun labelOf(code: String?): String =
            of(code)?.label ?: code?.takeIf { it.isNotBlank() } ?: "未知平台"
    }
}

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
    val targetLabel: String
        get() = when {
            targetPlatform.isBlank() -> "全平台通用"
            else -> "指定：" + SourcePlatform.labelOf(targetPlatform)
        }

    fun appliesTo(platformCode: String): Boolean =
        targetPlatform.isBlank() || targetPlatform == platformCode

    val declaredPlatforms: List<SourcePlatform>
        get() = declaredSources.mapNotNull { SourcePlatform.of(it) }.distinct()
}

enum class ProbeStatus(val label: String) {
    OK("正常"),

    SLOW("偏慢"),

    FAIL("失败"),

    UNTESTABLE("无法自动检测"),

    UNKNOWN("未检测"),
}

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

    val latencyLabel: String
        get() = when {
            latencyMs < 0 -> "—"
            latencyMs < 1000 -> "${latencyMs}ms"
            else -> String.format("%.1fs", latencyMs / 1000.0)
        }

    val speedScoreMs: Long
        get() = when {
            latencyMs >= 0 -> latencyMs
            observedMs >= 0 -> observedMs
            else -> UNKNOWN_LATENCY_MS
        }

    companion object {
        fun builtinTarget(platform: SourcePlatform) = "builtin:${platform.code}"

        fun pluginTarget(pluginId: String, platform: SourcePlatform) = "plugin:$pluginId:${platform.code}"

        const val SLOW_THRESHOLD_MS = 2500L

        const val UNKNOWN_LATENCY_MS = 1500L

        fun statusOf(latencyMs: Long, ok: Boolean): ProbeStatus = when {
            !ok -> ProbeStatus.FAIL
            latencyMs >= SLOW_THRESHOLD_MS -> ProbeStatus.SLOW
            else -> ProbeStatus.OK
        }
    }
}

data class QualityProbe(
    val quality: String,
    val status: ProbeStatus = ProbeStatus.UNKNOWN,
    val latencyMs: Long = -1L,
    val sizeBytes: Long = -1L,
    val detail: String = "",
) {
    val label: String get() = qualityLabel(quality)

    val latencyLabel: String
        get() = when {
            latencyMs < 0 -> "—"
            latencyMs < 1000 -> "${latencyMs}ms"
            else -> String.format(java.util.Locale.US, "%.1fs", latencyMs / 1000.0)
        }

    val sizeLabel: String get() = formatSize(sizeBytes)

    companion object {
        fun qualityLabel(quality: String): String = when (quality.lowercase()) {
            "128k" -> "标准"
            "320k" -> "极高 HQ"
            "flac" -> "无损 SQ"
            "flac24bit" -> "24bit"
            "hires" -> "Hi-Res"
            "atmos" -> "杜比全景声"
            "master" -> "母带"
            else -> quality.uppercase()
        }

        fun formatSize(bytes: Long): String = when {
            bytes <= 0L -> ""
            bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            bytes >= 1024L -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
}

data class PlatformQualityGroup(
    val platform: SourcePlatform,
    val qualities: List<QualityProbe> = emptyList(),
)

data class PluginDetectState(
    val plugin: SourcePlugin,
    val quick: Boolean,
    val running: Boolean = true,
    val total: Int = 0,
    val done: Int = 0,
    val groups: List<PlatformQualityGroup> = emptyList(),
    val error: String? = null,
) {
    val title: String get() = if (quick) "快速检测" else "全档检测"

    val passed: Int
        get() = groups.sumOf { group ->
            group.qualities.count { it.status == ProbeStatus.OK || it.status == ProbeStatus.SLOW }
        }
}
