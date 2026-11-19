package com.doxaiz.netoptimizer.core.traffic

/** Historical ranking is descriptive, never a claim of current activity or causation. */
internal enum class TrafficSort { TOTAL, OUTSIDE_FOREGROUND }

internal object TrafficRanking {
    fun sort(apps: List<AppTraffic>, order: TrafficSort): List<AppTraffic> = when (order) {
        TrafficSort.TOTAL -> apps.sortedWith(compareByDescending<AppTraffic> { it.totalBytes }.thenBy { it.uid })
        TrafficSort.OUTSIDE_FOREGROUND -> apps.sortedWith(
            compareByDescending<AppTraffic> { it.outsideForegroundBytes != null }
                .thenByDescending { it.outsideForegroundBytes ?: 0L }
                .thenByDescending { it.totalBytes }
                .thenBy { it.uid }
        )
    }
}
