package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.*
import org.junit.Test

class TrafficRateTest {
    private val first = MobileCounters(1, 1_000, 100, 200)

    @Test fun usesTheActualMonotonicInterval() {
        val rate = TrafficRate.between(first, MobileCounters(1, 3_500, 2_600, 1_200))!!
        assertEquals(1_000.0, rate.rxBytesPerSecond, 0.001)
        assertEquals(400.0, rate.txBytesPerSecond, 0.001)
    }

    @Test fun zeroTrafficIsNotMissingData() {
        val rate = TrafficRate.between(first, MobileCounters(1, 3_000, 100, 200))!!
        assertEquals(0.0, rate.rxBytesPerSecond, 0.0)
        assertEquals(0.0, rate.txBytesPerSecond, 0.0)
    }

    @Test fun refusesFirstSampleNetworkChangeAndCounterReset() {
        assertNull(TrafficRate.between(null, first))
        assertNull(TrafficRate.between(first, MobileCounters(2, 3_000, 300, 400)))
        assertNull(TrafficRate.between(first, MobileCounters(1, 3_000, 99, 400)))
        assertNull(TrafficRate.between(first, MobileCounters(1, 3_000, 300, 199)))
        assertNull(TrafficRate.between(MobileCounters(1, 1_000, -1, 1), first))
    }

    @Test fun rejectsInvalidTimeAndGapsAfterPause() {
        assertNull(TrafficRate.between(first, MobileCounters(1, 1_000, 200, 300)))
        assertNull(TrafficRate.between(first, MobileCounters(1, 999, 200, 300)))
        assertNull(TrafficRate.between(first, MobileCounters(1, 11_001, 200, 300)))
    }

    @Test fun aggregatesBucketsByUidAndRejectsInvalidCounters() {
        val totals = TrafficTotals()
        assertTrue(totals.add(10, 100, 50))
        assertTrue(totals.add(10, 20, 30))
        assertTrue(totals.add(-5, 10, 20))
        assertFalse(totals.add(10, -1, 100))
        assertEquals(120L to 80L, totals.snapshot()[10])
        assertEquals(10L to 20L, totals.snapshot()[-5])
    }

    @Test fun byteAggregationDoesNotOverflow() {
        assertEquals(15L, saturatedAdd(7, 8))
        assertEquals(Long.MAX_VALUE, saturatedAdd(Long.MAX_VALUE - 2, 3))
    }
}
