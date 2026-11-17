package com.doxaiz.netoptimizer.core.net

import org.junit.Assert.assertEquals
import org.junit.Test

class TcpProbeSummaryTest {
    @Test fun noAttemptsIsNotZeroLoss() {
        val result = TcpProbeSummary.from(emptyList())
        assertEquals(-1, result.averageMs)
        assertEquals(-1, result.jitterMs)
        assertEquals(-1, result.failedPercent)
        assertEquals(0, result.attempts)
    }

    @Test fun singleSuccessHasNoJitter() {
        val result = TcpProbeSummary.from(listOf(20, -1, -1))
        assertEquals(20, result.averageMs)
        assertEquals(-1, result.jitterMs)
        assertEquals(66, result.failedPercent)
        assertEquals(1, result.successes)
    }

    @Test fun usesSuccessfulConnectionsForAverageAndJitter() {
        val result = TcpProbeSummary.from(listOf(10, -1, 20, 25))
        assertEquals(18, result.averageMs)
        assertEquals(8, result.jitterMs)
        assertEquals(25, result.failedPercent)
    }

    @Test fun allFailedIsNotAnActualLatency() {
        val result = TcpProbeSummary.from(listOf(-1, -1))
        assertEquals(-1, result.averageMs)
        assertEquals(-1, result.jitterMs)
        assertEquals(100, result.failedPercent)
    }
}
