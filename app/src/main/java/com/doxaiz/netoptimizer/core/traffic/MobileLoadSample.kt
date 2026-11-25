package com.doxaiz.netoptimizer.core.traffic

/** Highest average over one sampled interval, not an instantaneous maximum. */
internal data class MobileLoadPeak(val bytesPerSecond: Double, val durationMs: Long)

/** Mobile-interface totals across one uninterrupted foreground sampling window. */
internal data class MobileLoadSample(
    val durationMs: Long,
    val received: Long,
    val sent: Long,
    val peakReceived: MobileLoadPeak,
    val peakSent: MobileLoadPeak,
    // Ephemeral local metadata, used only to check whether two samples can be compared.
    val first: MobileCounters,
    val last: MobileCounters
) {
    val rxPerSecond: Double get() = received.toDouble() * 1000.0 / durationMs
    val txPerSecond: Double get() = sent.toDouble() * 1000.0 / durationMs
}

/** Each adjacent sample must be valid; a lost interval invalidates the entire window. */
internal object MobileLoadWindow {
    const val INTERVALS = 5
    const val INTERVAL_MS = 2_000L
    const val MAX_INTERVAL_MS = 4_000L

    fun result(samples: List<MobileCounters>): MobileLoadSample? {
        if (samples.size != INTERVALS + 1 || samples.any { it.rx < 0 || it.tx < 0 || it.elapsedMs < 0 }) return null
        for ((a, b) in samples.zipWithNext()) {
            val elapsed = b.elapsedMs - a.elapsedMs
            if (elapsed <= 0 || elapsed > MAX_INTERVAL_MS || a.networkId != b.networkId || b.rx < a.rx || b.tx < a.tx) return null
        }
        val first = samples.first()
        val last = samples.last()
        val duration = last.elapsedMs - first.elapsedMs
        if (duration < INTERVAL_MS * INTERVALS || duration > MAX_INTERVAL_MS * INTERVALS) return null
        val intervals = samples.zipWithNext()
        fun peak(bytes: (MobileCounters) -> Long): MobileLoadPeak = intervals.map { (a, b) ->
            val intervalMs = b.elapsedMs - a.elapsedMs
            MobileLoadPeak((bytes(b) - bytes(a)).toDouble() * 1000.0 / intervalMs, intervalMs)
        }.maxBy { it.bytesPerSecond }
        return MobileLoadSample(duration, last.rx - first.rx, last.tx - first.tx,
            peak { it.rx }, peak { it.tx }, first, last)
    }
}

internal enum class MobileLoadFailure { NO_MOBILE, COUNTERS_UNAVAILABLE, NETWORK_CHANGED, INVALID_SAMPLE }
internal sealed interface MobileLoadResult {
    data class Success(val sample: MobileLoadSample) : MobileLoadResult
    data class Failure(val reason: MobileLoadFailure) : MobileLoadResult
}
