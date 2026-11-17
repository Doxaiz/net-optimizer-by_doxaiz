package com.doxaiz.netoptimizer.core.net

import org.junit.Assert.*
import org.junit.Test

class LoadedLatencySummaryTest {
    @Test fun onlyAcceptsSuccessfulSamplesWithBytesDuringTheEntireActiveWindow() {
        assertEquals(40, LoadedLatencySummary.overlappingSample(true, true, 100, 101, 40))
        assertNull(LoadedLatencySummary.overlappingSample(false, true, 100, 101, 40))
        assertNull(LoadedLatencySummary.overlappingSample(true, false, 100, 101, 40))
        assertNull(LoadedLatencySummary.overlappingSample(true, true, 100, 100, 40))
        assertNull(LoadedLatencySummary.overlappingSample(true, true, 100, 101, -1))
    }

    @Test fun requiresThreeSuccessfulMeasurementsInBothPhases() {
        val summary = LoadedLatencySummary.from(listOf(20, -1, 30, 40), listOf(50, -1, 60))
        assertEquals(3, summary.idleSuccesses)
        assertEquals(2, summary.loadedSuccesses)
        assertEquals(30, summary.idleMedianMs)
        assertNull(summary.loadedMedianMs)
        assertNull(summary.differenceMs)
    }

    @Test fun usesMediansAndPreservesNegativeDifference() {
        val summary = LoadedLatencySummary.from(listOf(50, 3000, 70, 60), listOf(40, 10, 30, 20))
        assertEquals(65, summary.idleMedianMs)
        assertEquals(25, summary.loadedMedianMs)
        assertEquals(-40, summary.differenceMs)
    }

    @Test fun zeroMillisecondsIsAValidMeasurementAndMedianDoesNotOverflow() {
        assertEquals(0, LoadedLatencySummary.from(listOf(0, 0, 1), listOf(0, 0, 0)).differenceMs)
        assertEquals(Int.MAX_VALUE, LoadedLatencySummary.from(List(4) { Int.MAX_VALUE }, emptyList()).idleMedianMs)
    }
}
