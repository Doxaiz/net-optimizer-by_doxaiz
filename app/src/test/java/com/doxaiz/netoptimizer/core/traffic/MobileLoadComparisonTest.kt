package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.*
import org.junit.Test

class MobileLoadComparisonTest {
    private fun sample(start: Long, network: Long = 42, rx: Long = 100, tx: Long = 200,
                       rxStep: Long = 500, txStep: Long = 100, interval: Long = 2_000): MobileLoadSample =
        MobileLoadWindow.result((0..5).map { index ->
            MobileCounters(network, start + interval * index, rx + rxStep * index, tx + txStep * index)
        })!!

    private val before = sample(1_000)
    private val after = sample(12_000, rx = 3_000, tx = 1_000, rxStep = 100, txStep = 200)

    @Test fun comparesActualRatesNotRawBytesAndKeepsDirection() {
        val difference = MobileLoadComparison.difference(before, after, 22_000)!!
        assertEquals(-200.0, difference.rxBytesPerSecond, 0.001)
        assertEquals(50.0, difference.txBytesPerSecond, 0.001)
        val slower = sample(12_000, rx = 3_000, tx = 1_000, rxStep = 500, txStep = 100, interval = 2_500)
        val delta = MobileLoadComparison.difference(before, slower, 24_500)!!
        assertEquals(-50.0, delta.rxBytesPerSecond, 0.001)
        assertEquals(-10.0, delta.txBytesPerSecond, 0.001)
    }

    @Test fun idleIsValidAndDoesNotDivideByTheFirstRate() {
        val idleBefore = sample(1_000, rxStep = 0, txStep = 0)
        val idleAfter = sample(12_000, rx = 100, tx = 200, rxStep = 0, txStep = 0)
        val difference = MobileLoadComparison.difference(idleBefore, idleAfter, 22_000)!!
        assertEquals(0.0, difference.rxBytesPerSecond, 0.0)
        assertEquals(0.0, difference.txBytesPerSecond, 0.0)
    }

    @Test fun expiresAtExactlyTenMinutesAndRejectsFutureOrOverlappingWindows() {
        assertEquals(MobileLoadComparisonStatus.READY, MobileLoadComparison.status(before, after, 610_999))
        assertEquals(MobileLoadComparisonStatus.EXPIRED, MobileLoadComparison.status(before, after, 611_000))
        assertNull(MobileLoadComparison.difference(before, after, 611_000))
        assertEquals(MobileLoadComparisonStatus.INVALID_TIME, MobileLoadComparison.status(before, after, 21_999))
        val overlapping = sample(10_000, rx = 3_000, tx = 1_000)
        assertEquals(MobileLoadComparisonStatus.INVALID_TIME, MobileLoadComparison.status(before, overlapping, 30_000))
    }

    @Test fun differentNetworksAndCounterResetsCannotProduceDifferences() {
        val changed = sample(12_000, network = 77, rx = 3_000, tx = 1_000)
        assertEquals(MobileLoadComparisonStatus.DIFFERENT_NETWORK, MobileLoadComparison.status(before, changed, 22_000))
        assertNull(MobileLoadComparison.difference(before, changed, 22_000))
        val rxReset = sample(12_000, rx = 1, tx = 1_000)
        val txReset = sample(12_000, rx = 3_000, tx = 1)
        assertEquals(MobileLoadComparisonStatus.COUNTERS_RESET, MobileLoadComparison.status(before, rxReset, 22_000))
        assertEquals(MobileLoadComparisonStatus.COUNTERS_RESET, MobileLoadComparison.status(before, txReset, 22_000))
        assertNull(MobileLoadComparison.difference(before, rxReset, 22_000))
    }

    @Test fun inconsistentSampleMetadataIsRejected() {
        assertEquals(MobileLoadComparisonStatus.INVALID_SAMPLE,
            MobileLoadComparison.status(before.copy(durationMs = 0), after, 22_000))
        assertEquals(MobileLoadComparisonStatus.INVALID_SAMPLE,
            MobileLoadComparison.status(before.copy(sent = -1), after, 22_000))
        assertEquals(MobileLoadComparisonStatus.INVALID_SAMPLE,
            MobileLoadComparison.status(before.copy(last = before.last.copy(networkId = 2)), after, 22_000))
    }
}
