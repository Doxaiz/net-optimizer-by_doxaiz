package com.doxaiz.netoptimizer.core.net

import org.junit.Assert.*
import org.junit.Test

class TcpLatencyDistributionTest {
    @Test fun needsAtLeastThreeSuccessfulConnections() {
        assertNull(TcpLatencyDistributions.from(emptyList()))
        assertNull(TcpLatencyDistributions.from(listOf(-1, -1, -1)))
        assertNull(TcpLatencyDistributions.from(listOf(15, -1, 30)))
        assertEquals(TcpLatencyDistribution(20, 500), TcpLatencyDistributions.from(listOf(10, -1, 500, 20)))
    }

    @Test fun evenMedianRoundsDownAndAvoidsOverflow() {
        val samples = listOf(Int.MAX_VALUE, -1, Int.MAX_VALUE - 1, 2, 1)
        assertEquals(TcpLatencyDistribution(1_073_741_824, Int.MAX_VALUE), TcpLatencyDistributions.from(samples))
        assertEquals(Int.MAX_VALUE - 1, TcpLatencyDistributions.medianKotlin(listOf(Int.MAX_VALUE, Int.MAX_VALUE - 1)))
    }

    @Test fun interruptedSeriesNeverPublishesDistribution() {
        val target = PingTarget("test", "example.com", 443, "")
        val samples = listOf(10, 20, 30, -1)
        val complete = PingMeasurementPlan.result(target, samples, PingStatus.COMPLETED)
        assertEquals(20, complete.medianMs)
        assertEquals(30, complete.maximumMs)
        assertEquals(25, complete.lossPercent)
        for (status in PingStatus.entries.filter { it != PingStatus.COMPLETED }) {
            val stopped = PingMeasurementPlan.result(target, samples, status)
            assertNull(stopped.medianMs)
            assertNull(stopped.maximumMs)
        }
    }
}
