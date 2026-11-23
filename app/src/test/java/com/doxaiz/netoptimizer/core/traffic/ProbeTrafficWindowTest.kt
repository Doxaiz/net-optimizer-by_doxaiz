package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.*
import org.junit.Test

class ProbeTrafficWindowTest {
    private val before = MobileCounters(42, 10_000, 5_000, 2_000)
    private val after = MobileCounters(42, 12_000, 7_500, 3_000)

    @Test fun computesPerDirectionRatesUsingActualElapsedTime() {
        val traffic = ProbeTrafficWindow.between(before, after)!!
        assertEquals(2_000L, traffic.durationMs)
        assertEquals(2_500L, traffic.received)
        assertEquals(1_000L, traffic.sent)
        assertEquals(1_250.0, traffic.rxPerSecond, 0.001)
        assertEquals(500.0, traffic.txPerSecond, 0.001)
    }

    @Test fun zeroTrafficIsValidAndBoundariesAreInclusive() {
        val idle = ProbeTrafficWindow.between(before, before.copy(elapsedMs = 11_000))!!
        assertEquals(0.0, idle.rxPerSecond, 0.0)
        assertEquals(0.0, idle.txPerSecond, 0.0)
        assertNotNull(ProbeTrafficWindow.between(before, after.copy(elapsedMs = 70_000)))
        assertNull(ProbeTrafficWindow.between(before, after.copy(elapsedMs = 10_999)))
        assertNull(ProbeTrafficWindow.between(before, after.copy(elapsedMs = 70_001)))
    }

    @Test fun rejectsUnavailableChangedResetOrInvalidSamples() {
        assertNull(ProbeTrafficWindow.between(null, after))
        assertNull(ProbeTrafficWindow.between(before, null))
        assertNull(ProbeTrafficWindow.between(before, after.copy(networkId = 99)))
        assertNull(ProbeTrafficWindow.between(before.copy(rx = -1), after))
        assertNull(ProbeTrafficWindow.between(before.copy(tx = -1), after))
        assertNull(ProbeTrafficWindow.between(before, after.copy(rx = -1)))
        assertNull(ProbeTrafficWindow.between(before, after.copy(tx = -1)))
        assertNull(ProbeTrafficWindow.between(before, after.copy(rx = 4_999)))
        assertNull(ProbeTrafficWindow.between(before, after.copy(tx = 1_999)))
        assertNull(ProbeTrafficWindow.between(before, after.copy(elapsedMs = 9_999)))
        assertNull(ProbeTrafficWindow.between(before.copy(elapsedMs = -1), after))
    }

    @Test fun missingTrafficDoesNotInvalidateTcpComparison() {
        val first = LoadSample(6, 6, 80, 5, 0, null)
        val second = first.copy(averageMs = 70, traffic = ProbeTrafficWindow.between(before, after))
        assertEquals(-10L, LoadComparison.delta(first, second))
    }
}
