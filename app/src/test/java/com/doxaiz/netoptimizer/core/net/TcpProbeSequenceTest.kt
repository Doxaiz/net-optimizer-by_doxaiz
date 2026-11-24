package com.doxaiz.netoptimizer.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TcpProbeSequenceTest {
    @Test fun progressCountsOnlyRealCompletedAttemptsIncludingFailures() = runBlocking {
        val answers = listOf(12, -1, 28).iterator()
        val progress = mutableListOf<Pair<Int, Int>>()
        val samples = TcpProbeSequence.run(3, { true }, { answers.next() }) { done, total -> progress += done to total }
        assertEquals(listOf(12, -1, 28), samples)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
    }

    @Test fun pausesOnlyBetweenAttemptsAndReportsProgressBeforeWaiting() = runBlocking {
        val events = mutableListOf<String>()
        val samples = TcpProbeSequence.run(3, beforeAttempt = { events += "check"; true },
            connect = { events += "connect"; 12 },
            betweenAttempts = { events += "pause" },
            onProgress = { done, _ -> events += "progress$done" })
        assertEquals(listOf(12, 12, 12), samples)
        assertEquals(listOf("check", "connect", "progress1", "pause", "check", "connect", "progress2",
            "pause", "check", "connect", "progress3"), events)
        var pauses = 0
        TcpProbeSequence.run(1, { true }, { 10 }, betweenAttempts = { pauses++ }) { _, _ -> }
        assertEquals(0, pauses)
    }

    @Test fun cancellationDuringPauseDoesNotStartNextConnection() = runBlocking {
        var connections = 0
        try {
            TcpProbeSequence.run(3, { true }, { connections++; 10 },
                betweenAttempts = { throw CancellationException("cancel while waiting") }) { _, _ -> }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, connections)
        }
    }

    @Test fun safetyInterruptionDoesNotReportUnperformedAttempts() = runBlocking {
        var checks = 0
        val progress = mutableListOf<Int>()
        val samples = TcpProbeSequence.run(6, { checks++ < 2 }, { 5 }) { done, _ -> progress += done }
        assertEquals(listOf(5, 5), samples)
        assertEquals(listOf(1, 2), progress)
    }

    @Test fun cancellationStopsBeforeNextAttempt() = runBlocking {
        var connections = 0
        try {
            TcpProbeSequence.run(6, { true }, { connections++; 10 }) { _, _ -> throw CancellationException("stop") }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, connections)
        }
    }
}
