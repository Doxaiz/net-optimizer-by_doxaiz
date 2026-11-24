package com.doxaiz.netoptimizer.core.net

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Progress counts completed connection attempts, including failures, never a timer or estimate. */
internal object TcpProbeSequence {
    suspend fun run(
        attempts: Int,
        beforeAttempt: () -> Boolean,
        connect: () -> Int,
        betweenAttempts: suspend () -> Unit = {},
        onProgress: suspend (completed: Int, total: Int) -> Unit
    ): List<Int> {
        require(attempts > 0)
        val samples = mutableListOf<Int>()
        repeat(attempts) { index ->
            if (index > 0) betweenAttempts()
            currentCoroutineContext().ensureActive()
            if (!beforeAttempt()) return samples
            samples += connect()
            currentCoroutineContext().ensureActive()
            onProgress(samples.size, attempts)
        }
        return samples
    }
}
