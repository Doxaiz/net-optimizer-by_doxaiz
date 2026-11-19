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

/** DEFAULT is Android's "not accounted in another state", not a live background detector. */
internal enum class TrafficBucketState { DEFAULT, FOREGROUND, UNKNOWN }

internal data class TrafficAmounts(
    val rx: Long = 0,
    val tx: Long = 0,
    val defaultBytes: Long = 0,
    val stateComplete: Boolean = true
) {
    val outsideForeground: Long? get() = defaultBytes.takeIf { stateComplete }
}

/** Aggregate separate state/metered/roaming buckets; unknown state is never presented as zero. */
internal class TrafficTotals {
    private val values = mutableMapOf<Int, TrafficAmounts>()
    fun add(uid: Int, rx: Long, tx: Long, state: TrafficBucketState): Boolean {
        if (rx < 0 || tx < 0) return false
        val old = values[uid] ?: TrafficAmounts()
        values[uid] = TrafficAmounts(
            rx = saturatedAdd(old.rx, rx),
            tx = saturatedAdd(old.tx, tx),
            defaultBytes = if (state == TrafficBucketState.DEFAULT) saturatedAdd(old.defaultBytes, saturatedAdd(rx, tx)) else old.defaultBytes,
            stateComplete = old.stateComplete && state != TrafficBucketState.UNKNOWN
        )
        return true
    }
    fun snapshot(): Map<Int, TrafficAmounts> = values.toMap()
}

internal fun saturatedAdd(a: Long, b: Long): Long = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
