package com.doxaiz.netoptimizer.core.traffic

internal enum class MobileLoadComparisonStatus { READY, INVALID_SAMPLE, INVALID_TIME, DIFFERENT_NETWORK, COUNTERS_RESET, EXPIRED, UNRELIABLE_NETWORK }
internal data class MobileLoadDifference(val rxBytesPerSecond: Double, val txBytesPerSecond: Double)

/** Short samples are descriptive, not proof that a change caused any difference. */
internal object MobileLoadComparison {
    const val WINDOW_MS = 10 * 60_000L

    private fun valid(sample: MobileLoadSample): Boolean {
        val first = sample.first
        val last = sample.last
        if (first.elapsedMs < 0 || last.elapsedMs <= first.elapsedMs ||
            first.networkId != last.networkId || first.rx < 0 || first.tx < 0 ||
            last.rx < first.rx || last.tx < first.tx) return false
        val minDuration = MobileLoadWindow.INTERVAL_MS * MobileLoadWindow.INTERVALS
        val maxDuration = MobileLoadWindow.MAX_INTERVAL_MS * MobileLoadWindow.INTERVALS
        return sample.durationMs == last.elapsedMs - first.elapsedMs && sample.durationMs in minDuration..maxDuration &&
            sample.received == last.rx - first.rx && sample.sent == last.tx - first.tx
    }

    /**
     * [reliableNetwork] is false when Android reports the current cellular network as
     * suspended, captive-portalled or not validated. Such conditions make even a correctly
     * paired comparison meaningless, so no difference is shown.
     */
    fun status(before: MobileLoadSample, after: MobileLoadSample, nowElapsedMs: Long,
               reliableNetwork: Boolean = true): MobileLoadComparisonStatus {
        if (!valid(before) || !valid(after)) return MobileLoadComparisonStatus.INVALID_SAMPLE
        if (after.first.elapsedMs < before.last.elapsedMs || nowElapsedMs < after.last.elapsedMs)
            return MobileLoadComparisonStatus.INVALID_TIME
        if (before.last.networkId != after.first.networkId) return MobileLoadComparisonStatus.DIFFERENT_NETWORK
        if (after.first.rx < before.last.rx || after.first.tx < before.last.tx) return MobileLoadComparisonStatus.COUNTERS_RESET
        if (nowElapsedMs - before.last.elapsedMs >= WINDOW_MS) return MobileLoadComparisonStatus.EXPIRED
        if (!reliableNetwork) return MobileLoadComparisonStatus.UNRELIABLE_NETWORK
        return MobileLoadComparisonStatus.READY
    }

    fun difference(before: MobileLoadSample, after: MobileLoadSample, nowElapsedMs: Long,
                   reliableNetwork: Boolean = true): MobileLoadDifference? =
        if (status(before, after, nowElapsedMs, reliableNetwork) == MobileLoadComparisonStatus.READY)
            MobileLoadDifference(after.rxPerSecond - before.rxPerSecond, after.txPerSecond - before.txPerSecond)
        else null
}
