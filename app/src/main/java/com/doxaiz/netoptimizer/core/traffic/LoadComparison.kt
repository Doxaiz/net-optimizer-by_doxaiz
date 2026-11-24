package com.doxaiz.netoptimizer.core.traffic

/** A completed series of TCP connections to the same pinned endpoint, not ICMP/game ping. */
internal data class LoadSample(
    val attempts: Int,
    val successes: Int,
    val averageMs: Int,
    val jitterMs: Int,
    val failedPercent: Int,
    val traffic: ProbeTraffic? = null,
    val medianMs: Int? = null,
    val maximumMs: Int? = null
)

internal object LoadComparison {
    const val ATTEMPTS = 6
    const val INTERVAL_MS = 1_000L
    const val MIN_SUCCESSES = 4

    fun usable(sample: LoadSample): Boolean =
        sample.attempts == ATTEMPTS && sample.successes in MIN_SUCCESSES..ATTEMPTS &&
            sample.averageMs >= 0 && sample.jitterMs >= 0 && sample.failedPercent in 0..100 &&
            sample.failedPercent == (ATTEMPTS - sample.successes) * 100 / ATTEMPTS

    /** Difference is descriptive only; it cannot establish causation. */
    fun delta(before: LoadSample, after: LoadSample): Long? =
        if (usable(before) && usable(after)) after.averageMs.toLong() - before.averageMs else null
}
