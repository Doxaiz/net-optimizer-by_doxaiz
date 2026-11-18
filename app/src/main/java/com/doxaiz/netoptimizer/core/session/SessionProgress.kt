package com.doxaiz.netoptimizer.core.session

/** Foreground time and completed TCP measurements, independent of the screen or Android clock. */
data class SessionProgress(
    val elapsedMs: Long = 0,
    val segmentStartMs: Long? = null,
    val history: List<Int> = emptyList(),
    val sampleCount: Int = 0,
    val latencySum: Long = 0,
    val maxLatency: Int = -1,
    val attempts: Long = 0,
    val failures: Long = 0
) {
    fun resume(nowMs: Long): SessionProgress = if (segmentStartMs == null) copy(segmentStartMs = nowMs) else this

    fun elapsedAt(nowMs: Long): Long = elapsedMs + (segmentStartMs?.let { (nowMs - it).coerceAtLeast(0) } ?: 0)

    fun pause(nowMs: Long): SessionProgress = copy(elapsedMs = elapsedAt(nowMs), segmentStartMs = null)

    fun addCompleted(averageMs: Int, attemptsMade: Int, successes: Int): SessionProgress {
        require(attemptsMade > 0 && successes in 0..attemptsMade)
        require((averageMs >= 0) == (successes > 0))
        val hasLatency = averageMs >= 0
        return copy(
            history = if (hasLatency) (history + averageMs).takeLast(120) else history,
            sampleCount = sampleCount + if (hasLatency) 1 else 0,
            latencySum = latencySum + if (hasLatency) averageMs else 0,
            maxLatency = if (hasLatency) maxOf(maxLatency, averageMs) else maxLatency,
            attempts = attempts + attemptsMade,
            failures = failures + attemptsMade - successes
        )
    }

    fun averageLatency(): Int = if (sampleCount == 0) -1 else (latencySum / sampleCount).toInt()
    fun failedPercent(): Int = if (attempts == 0L) -1 else (failures * 100 / attempts).toInt()
}
