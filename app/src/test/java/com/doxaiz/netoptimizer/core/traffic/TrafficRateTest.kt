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

    @Test fun aggregatesStateAndMeteredBucketsWithoutInventingBackgroundTraffic() {
        val totals = TrafficTotals()
        assertTrue(totals.add(10, 100, 50, TrafficBucketState.DEFAULT))
        assertTrue(totals.add(10, 20, 30, TrafficBucketState.FOREGROUND))
        assertTrue(totals.add(10, 7, 3, TrafficBucketState.DEFAULT))
        assertTrue(totals.add(-5, 10, 20, TrafficBucketState.FOREGROUND))
        assertFalse(totals.add(10, -1, 100, TrafficBucketState.DEFAULT))
        assertEquals(TrafficAmounts(127, 83, 160, true), totals.snapshot()[10])
        assertEquals(0L, totals.snapshot()[-5]!!.outsideForeground)
    }

    @Test fun unknownOrCombinedStateIsNotMisreportedAsZero() {
        val totals = TrafficTotals()
        assertTrue(totals.add(10, 7, 4, TrafficBucketState.DEFAULT))
        assertTrue(totals.add(10, 9, 1, TrafficBucketState.UNKNOWN))
        assertTrue(totals.add(10, 2, 1, TrafficBucketState.FOREGROUND))
        assertEquals(18L, totals.snapshot()[10]!!.rx)
        assertEquals(6L, totals.snapshot()[10]!!.tx)
        assertNull(totals.snapshot()[10]!!.outsideForeground)
    }

    @Test fun stateAggregationSaturatesSafely() {
        val totals = TrafficTotals()
        assertTrue(totals.add(10, Long.MAX_VALUE - 1, 8, TrafficBucketState.DEFAULT))
        assertTrue(totals.add(10, 10, 1, TrafficBucketState.FOREGROUND))
        assertEquals(Long.MAX_VALUE, totals.snapshot()[10]!!.rx)
        assertEquals(Long.MAX_VALUE, totals.snapshot()[10]!!.outsideForeground)
    }

    @Test fun byteAggregationDoesNotOverflow() {
        assertEquals(15L, saturatedAdd(7, 8))
        assertEquals(Long.MAX_VALUE, saturatedAdd(Long.MAX_VALUE - 2, 3))
    }
}
