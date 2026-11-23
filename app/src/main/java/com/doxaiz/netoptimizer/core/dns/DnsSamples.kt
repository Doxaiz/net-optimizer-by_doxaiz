package com.doxaiz.netoptimizer.core.dns

import com.doxaiz.netoptimizer.core.net.NativeMeasurements

/** Five completed attempts; only validated responses contribute to the median. */
internal data class DnsSampleSummary(val answered: Int, val medianMs: Int?)

internal object DnsSamples {
    const val ATTEMPTS = 5
    const val MIN_ANSWERS = 3

    fun summarize(successfulMs: List<Int>): DnsSampleSummary {
        require(successfulMs.size <= ATTEMPTS && successfulMs.all { it >= 0 })
        NativeMeasurements.median(successfulMs, MIN_ANSWERS)?.let {
            return DnsSampleSummary(successfulMs.size, it.takeIf { value -> value >= 0 })
        }
        val sorted = successfulMs.sorted()
        val middle = sorted.size / 2
        val median = when {
            sorted.size < MIN_ANSWERS -> null
            sorted.size % 2 == 1 -> sorted[middle]
            else -> ((sorted[middle - 1].toLong() + sorted[middle]) / 2).toInt()
        }
        return DnsSampleSummary(sorted.size, median)
    }
}
