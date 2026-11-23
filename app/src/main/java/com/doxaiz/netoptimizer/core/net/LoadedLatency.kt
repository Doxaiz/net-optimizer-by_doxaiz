package com.doxaiz.netoptimizer.core.net

/** TCP connection timings, not ICMP RTT or a conclusive bufferbloat diagnosis. */
data class LoadedLatency(
    val idleMedianMs: Int?,
    val loadedMedianMs: Int?,
    val idleSuccesses: Int,
    val loadedSuccesses: Int
) {
    val differenceMs: Int? get() = if (idleMedianMs != null && loadedMedianMs != null) loadedMedianMs - idleMedianMs else null
}

internal object LoadedLatencySummary {
    const val MIN_SUCCESS = 3

    /** Both endpoints of the connection attempt must fall inside an active transfer,
     * and bytes must actually have arrived during the attempt. */
    fun overlappingSample(startActive: Boolean, endActive: Boolean, bytesBefore: Long, bytesAfter: Long, ms: Int): Int? =
        ms.takeIf { startActive && endActive && bytesAfter > bytesBefore && it >= 0 }

    fun from(idle: List<Int>, loaded: List<Int>): LoadedLatency {
        val idleSuccess = idle.filter { it >= 0 }
        val loadedSuccess = loaded.filter { it >= 0 }
        return LoadedLatency(median(idleSuccess), median(loadedSuccess), idleSuccess.size, loadedSuccess.size)
    }

    private fun median(samples: List<Int>): Int? {
        if (samples.size < MIN_SUCCESS) return null
        NativeMeasurements.median(samples, MIN_SUCCESS)?.let { return it.takeIf { value -> value >= 0 } }
        val sorted = samples.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else
            ((sorted[middle - 1].toLong() + sorted[middle]) / 2).toInt()
    }
}
