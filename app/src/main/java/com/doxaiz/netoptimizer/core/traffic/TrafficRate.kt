package com.doxaiz.netoptimizer.core.traffic

/** Counters are monotonically increasing since boot; time uses Android's monotonic clock. */
data class MobileCounters(val networkId: Long, val elapsedMs: Long, val rx: Long, val tx: Long)
data class MobileRate(val rxBytesPerSecond: Double, val txBytesPerSecond: Double)

object TrafficRate {
    fun between(previous: MobileCounters?, current: MobileCounters): MobileRate? {
        if (previous == null || previous.networkId != current.networkId ||
            previous.rx < 0 || previous.tx < 0 || current.rx < previous.rx || current.tx < previous.tx) return null
        val millis = current.elapsedMs - previous.elapsedMs
        // Do not treat a resume after a long pause as an instantaneous reading.
        if (millis <= 0 || millis > 10_000) return null
        return MobileRate((current.rx - previous.rx).toDouble() * 1000.0 / millis,
            (current.tx - previous.tx).toDouble() * 1000.0 / millis)
    }
}

/** Aggregate NetworkStats' separate state/metered/roaming buckets without duplicating UIDs. */
internal class TrafficTotals {
    private val values = mutableMapOf<Int, Pair<Long, Long>>()
    fun add(uid: Int, rx: Long, tx: Long): Boolean {
        if (rx < 0 || tx < 0) return false
        val old = values[uid] ?: (0L to 0L)
        values[uid] = saturatedAdd(old.first, rx) to saturatedAdd(old.second, tx)
        return true
    }
    fun snapshot(): Map<Int, Pair<Long, Long>> = values.toMap()
}

internal fun saturatedAdd(a: Long, b: Long): Long = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
