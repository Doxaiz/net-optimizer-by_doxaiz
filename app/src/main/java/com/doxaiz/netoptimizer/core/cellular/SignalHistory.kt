package com.doxaiz.netoptimizer.core.cellular

/** Poll snapshots, not guaranteed to be fresh modem measurements. Null marks an unavailable reading. */
data class SignalObservation(val atMs: Long, val rsrp: Int?, val technology: String?)

object SignalHistory {
    const val WINDOW_MS = 120_000L
    const val MAX_POINTS = 42
    private const val MAX_JOIN_GAP_MS = 9_000L

    fun append(previous: List<SignalObservation>, atMs: Long, rsrp: Int, technology: String): List<SignalObservation> {
        if (atMs < 0 || (previous.isNotEmpty() && atMs <= previous.last().atMs)) return previous
        val validTechnology = technology.takeIf { it == "4G" || it == "5G" }
        val value = rsrp.takeIf { it in -160..-30 && validTechnology != null }
        return trim(previous + SignalObservation(atMs, value, validTechnology), atMs).takeLast(MAX_POINTS)
    }

    fun trim(previous: List<SignalObservation>, nowMs: Long): List<SignalObservation> =
        previous.filter { it.atMs <= nowMs && nowMs - it.atMs <= WINDOW_MS }.takeLast(MAX_POINTS)

    fun canConnect(first: SignalObservation, second: SignalObservation): Boolean =
        first.rsrp != null && second.rsrp != null && first.technology != null &&
            first.technology == second.technology && second.atMs > first.atMs &&
            second.atMs - first.atMs <= MAX_JOIN_GAP_MS
}
