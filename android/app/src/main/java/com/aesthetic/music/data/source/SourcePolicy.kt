package com.aesthetic.music.data.source

import com.aesthetic.music.data.settings.UserPrefs

object SourcePolicy {

    const val MAX_ATTEMPTS = 3

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

        val filtered = if (prefs.sourceSmartSwitch) {
            val healthy = applicable.filter { p ->
                val probe = probeOf(p) ?: return@filter true
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
            prefs.sourceFastest -> filtered.sortedBy { p ->
                probeOf(p)?.speedScoreMs ?: SourceProbe.UNKNOWN_LATENCY_MS
            }

            else -> filtered.sortedWith(
                compareByDescending<SourcePlugin> { it.id == activeId }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.importedAt }
            )
        }

        return if (prefs.sourceFailover) ordered.take(MAX_ATTEMPTS) else ordered.take(1)
    }

    fun shouldContinueAfterFailure(prefs: UserPrefs): Boolean = prefs.sourceFailover

    fun builtinFallbackAvailable(songPlatform: String): Boolean =
        SourcePlatform.of(songPlatform)?.hasBuiltin == true
}
