package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.*
import org.junit.Test

class LoadComparisonTest {
    private val baseline = LoadSample(6, 6, 100, 5, 0)

    @Test fun requiresEnoughSuccessfulAttemptsFromACompleteSeries() {
        assertTrue(LoadComparison.usable(baseline))
        assertTrue(LoadComparison.usable(LoadSample(6, 4, 120, 20, 33)))
        assertFalse(LoadComparison.usable(LoadSample(6, 3, 120, 20, 50)))
        assertFalse(LoadComparison.usable(LoadSample(5, 5, 120, 20, 0)))
        assertFalse(LoadComparison.usable(LoadSample(6, 6, -1, 20, 0)))
        assertFalse(LoadComparison.usable(LoadSample(6, 6, 10, -1, 0)))
        assertFalse(LoadComparison.usable(LoadSample(6, 6, 10, 1, 33)))
    }

    @Test fun differenceIsSignedAndUnavailableForInvalidSeries() {
        assertEquals(-30L, LoadComparison.delta(baseline, baseline.copy(averageMs = 70)))
        assertEquals(40L, LoadComparison.delta(baseline, baseline.copy(averageMs = 140)))
        assertEquals(0L, LoadComparison.delta(baseline, baseline))
        assertNull(LoadComparison.delta(baseline, baseline.copy(successes = 3)))
        assertEquals(2_147_483_647L, LoadComparison.delta(baseline.copy(averageMs = 0), baseline.copy(averageMs = Int.MAX_VALUE)))
    }
}
