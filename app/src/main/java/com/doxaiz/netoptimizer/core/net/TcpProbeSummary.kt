package com.doxaiz.netoptimizer.core.net

import kotlin.math.abs
import kotlin.math.roundToInt

/** A negative value means a failed TCP connection, not an IP packet-loss measurement. */
data class TcpProbeSummary(
    val attempts: Int,
    val successes: Int,
    val averageMs: Int,
    val jitterMs: Int,
    val failedPercent: Int
) {
    companion object {
        fun from(samples: List<Int>): TcpProbeSummary = NativeMeasurements.tcp(samples) ?: fromKotlin(samples)

        /** Reference implementation for JVM tests and unavailable native libraries. */
        internal fun fromKotlin(samples: List<Int>): TcpProbeSummary {
            val successful = samples.filter { it >= 0 }
            val average = if (successful.isEmpty()) -1 else successful.average().roundToInt()
            val jitter = if (successful.size < 2) -1 else
                successful.zipWithNext { a, b -> abs(a - b) }.average().roundToInt()
            val failures = if (samples.isEmpty()) -1 else (samples.size - successful.size) * 100 / samples.size
            return TcpProbeSummary(samples.size, successful.size, average, jitter, failures)
        }
    }
}
