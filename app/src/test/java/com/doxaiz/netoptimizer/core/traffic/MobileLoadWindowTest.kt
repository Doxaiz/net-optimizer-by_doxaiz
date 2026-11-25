package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.*
import org.junit.Test

class MobileLoadWindowTest {
    private fun samples(): List<MobileCounters> = (0..5).map {
        MobileCounters(42, 1_000L + 2_000L * it, 100L + 500 * it, 200L + 100 * it)
    }

    @Test fun computesActualAveragesAndBytes() {
        val sample = MobileLoadWindow.result(samples())!!
        assertEquals(10_000L, sample.durationMs)
        assertEquals(2_500L, sample.received)
        assertEquals(500L, sample.sent)
        assertEquals(250.0, sample.rxPerSecond, 0.001)
        assertEquals(50.0, sample.txPerSecond, 0.001)
        assertEquals(250.0, sample.peakReceived.bytesPerSecond, 0.001)
        assertEquals(50.0, sample.peakSent.bytesPerSecond, 0.001)
        assertEquals(2_000L, sample.peakReceived.durationMs)
    }

    @Test fun idleIsAValidZeroNotAnError() {
        val sample = MobileLoadWindow.result(samples().map { it.copy(rx = 12, tx = 33) })!!
        assertEquals(0L, sample.received)
        assertEquals(0.0, sample.txPerSecond, 0.0)
        assertEquals(0.0, sample.peakReceived.bytesPerSecond, 0.0)
        assertEquals(0.0, sample.peakSent.bytesPerSecond, 0.0)
    }

    @Test fun peaksArePerDirectionAndUseActualIntervalDuration() {
        val times = listOf(1_000L, 3_000L, 6_000L, 8_000L, 10_000L, 12_000L)
        val rx = listOf(0L, 100L, 1_300L, 1_300L, 1_500L, 1_550L)
        val tx = listOf(0L, 50L, 110L, 1_110L, 1_150L, 1_200L)
        val counters = times.indices.map { MobileCounters(42, times[it], rx[it], tx[it]) }
        val sample = MobileLoadWindow.result(counters)!!
        assertEquals(11_000L, sample.durationMs)
        assertEquals(400.0, sample.peakReceived.bytesPerSecond, 0.001)
        assertEquals(3_000L, sample.peakReceived.durationMs)
        assertEquals(500.0, sample.peakSent.bytesPerSecond, 0.001)
        assertEquals(2_000L, sample.peakSent.durationMs)
        assertTrue(sample.peakReceived.bytesPerSecond > sample.rxPerSecond)
        assertTrue(sample.peakSent.bytesPerSecond > sample.txPerSecond)
    }

    @Test fun incompleteOrUnavailableCountersAreRejected() {
        assertNull(MobileLoadWindow.result(samples().dropLast(1)))
        assertNull(MobileLoadWindow.result(samples().toMutableList().apply { this[0] = this[0].copy(rx = -1) }))
        assertNull(MobileLoadWindow.result(samples().toMutableList().apply { this[2] = this[2].copy(tx = -1) }))
        assertNull(MobileLoadWindow.result(samples().map { it.copy(elapsedMs = it.elapsedMs - 20_000) }))
    }

    @Test fun neverBridgesNetworkChangesOrCounterResets() {
        assertNull(MobileLoadWindow.result(samples().toMutableList().apply { this[3] = this[3].copy(networkId = 9) }))
        assertNull(MobileLoadWindow.result(samples().toMutableList().apply { this[3] = this[3].copy(rx = 0) }))
        assertNull(MobileLoadWindow.result(samples().toMutableList().apply { this[3] = this[3].copy(tx = 0) }))
    }

    @Test fun rejectsStaleOrShortWindowsAndUsesMeasuredTime() {
        assertNull(MobileLoadWindow.result(samples().toMutableList().apply { this[2] = this[2].copy(elapsedMs = this[1].elapsedMs) }))
        assertNull(MobileLoadWindow.result(samples().toMutableList().apply { this[2] = this[2].copy(elapsedMs = this[1].elapsedMs + 4001) }))
        assertNull(MobileLoadWindow.result(samples().mapIndexed { i, c -> c.copy(elapsedMs = 1_000L + i * 1_000) }))
        val slower = samples().mapIndexed { i, c -> c.copy(elapsedMs = 1_000L + i * 2_500) }
        assertEquals(200.0, MobileLoadWindow.result(slower)!!.rxPerSecond, 0.001)
    }
}
