package com.doxaiz.netoptimizer.core.traffic

/** Device-wide mobile counters around a TCP series, including the test's own traffic. */
internal data class ProbeTraffic(val durationMs: Long, val received: Long, val sent: Long) {
    val rxPerSecond: Double get() = received.toDouble() * 1_000.0 / durationMs
    val txPerSecond: Double get() = sent.toDouble() * 1_000.0 / durationMs
}

internal object ProbeTrafficWindow {
    // Avoid presenting a stale or extremely short snapshot as a meaningful rate.
    const val MIN_DURATION_MS = 1_000L
    const val MAX_DURATION_MS = 60_000L

    fun between(before: MobileCounters?, after: MobileCounters?): ProbeTraffic? {
        if (before == null || after == null || before.networkId != after.networkId ||
            before.elapsedMs < 0 || after.elapsedMs < before.elapsedMs ||
            before.rx < 0 || before.tx < 0 || after.rx < before.rx || after.tx < before.tx) return null
        val elapsed = after.elapsedMs - before.elapsedMs
        if (elapsed !in MIN_DURATION_MS..MAX_DURATION_MS) return null
        return ProbeTraffic(elapsed, after.rx - before.rx, after.tx - before.tx)
    }
}
