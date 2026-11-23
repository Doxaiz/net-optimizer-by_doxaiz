package com.doxaiz.netoptimizer.core.net

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Pure test orchestration; a sticky network guard must stay valid throughout DNS and TCP. */
internal object PingMeasurementPlan {
    suspend fun <Address : Any> run(
        target: PingTarget,
        attempts: Int,
        sameNetwork: () -> Boolean,
        batteryOk: () -> Boolean,
        resolve: () -> Address?,
        connect: (Address) -> Int,
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): PingResult {
        require(attempts > 0)
        fun interruption(): PingStatus? = when {
            !sameNetwork() -> PingStatus.NETWORK_CHANGED
            !batteryOk() -> PingStatus.LOW_BATTERY
            else -> null
        }
        interruption()?.let { return result(target, emptyList(), it) }
        val address = resolve()
        currentCoroutineContext().ensureActive()
        interruption()?.let { return result(target, emptyList(), it) }
        if (address == null) return result(target, emptyList(), PingStatus.RESOLUTION_FAILED)
        var stopped: PingStatus? = null
        val samples = TcpProbeSequence.run(attempts, beforeAttempt = {
            stopped = interruption()
            stopped == null
        }, connect = { connect(address) }, onProgress = onProgress)
        val status = stopped ?: interruption() ?: PingStatus.COMPLETED
        return result(target, samples, status)
    }

    fun result(target: PingTarget, samples: List<Int>, status: PingStatus): PingResult {
        val summary = TcpProbeSummary.from(samples)
        val distribution = if (status == PingStatus.COMPLETED) TcpLatencyDistributions.from(samples) else null
        // Never publish measurements from an interrupted or untracked test as complete.
        return PingResult(target,
            if (status == PingStatus.COMPLETED) summary.averageMs else -1,
            if (status == PingStatus.COMPLETED) summary.jitterMs else -1,
            if (status == PingStatus.COMPLETED) summary.failedPercent else -1,
            status == PingStatus.COMPLETED && summary.averageMs in 0..150 && summary.failedPercent == 0,
            summary.attempts, summary.successes, status, distribution?.medianMs, distribution?.maximumMs)
    }
}
