package com.doxaiz.netoptimizer.core.net

/** Describes successful TCP connection times only; failures are reported separately. */
internal data class TcpLatencyDistribution(val medianMs: Int, val maximumMs: Int)

internal object TcpLatencyDistributions {
    const val MIN_SUCCESSES = 3

    fun from(samples: List<Int>): TcpLatencyDistribution? {
        val successes = samples.filter { it >= 0 }
        if (successes.size < MIN_SUCCESSES) return null
        val median = NativeMeasurements.median(successes, MIN_SUCCESSES)?.takeIf { it >= 0 }
            ?: medianKotlin(successes)
        return TcpLatencyDistribution(median, successes.max())
    }

    /** Reference calculation for the JVM and unsupported or unavailable native libraries. */
    internal fun medianKotlin(successes: List<Int>): Int {
        val sorted = successes.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else
            ((sorted[middle - 1].toLong() + sorted[middle]) / 2).toInt()
    }
}
