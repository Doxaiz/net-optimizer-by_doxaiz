package com.doxaiz.netoptimizer.core.cellular

import org.junit.Assert.*
import org.junit.Test

class SignalHistoryTest {
    @Test fun validSamplesAndMissingValuesAreDistinguished() {
        var history = SignalHistory.append(emptyList(), 1_000, -95, "4G")
        history = SignalHistory.append(history, 4_000, 0, "4G")
        history = SignalHistory.append(history, 7_000, -160, "5G")
        history = SignalHistory.append(history, 10_000, -29, "5G")
        history = SignalHistory.append(history, 13_000, -80, "—")
        assertEquals(listOf(-95, null, -160, null, null), history.map { it.rsrp })
        assertNull(history.last().technology)
    }

    @Test fun repeatedOrOutOfOrderPollsDoNotCreateFakeSamples() {
        val history = SignalHistory.append(emptyList(), 3_000, -100, "4G")
        assertEquals(history, SignalHistory.append(history, 3_000, -101, "4G"))
        assertEquals(history, SignalHistory.append(history, 2_999, -101, "4G"))
        assertTrue(SignalHistory.append(emptyList(), -1, -90, "4G").isEmpty())
        val repeatedValue = SignalHistory.append(history, 6_000, -100, "4G")
        assertEquals(2, repeatedValue.size) // Same reported value is still a distinct poll.
    }

    @Test fun boundedByTimeAndCapacity() {
        var history = emptyList<SignalObservation>()
        repeat(70) { history = SignalHistory.append(history, it * 3_000L, -100, "4G") }
        assertTrue(history.size <= SignalHistory.MAX_POINTS)
        assertTrue(history.last().atMs - history.first().atMs <= SignalHistory.WINDOW_MS)
        assertTrue(SignalHistory.trim(history, 1_000_000).isEmpty())
        assertTrue(SignalHistory.trim(history, 0).isEmpty()) // reject future timestamps
    }

    @Test fun breaksLinesForUnavailableReadingsTechnologyChangeAndLongGaps() {
        val a = SignalObservation(1_000, -100, "4G")
        assertTrue(SignalHistory.canConnect(a, SignalObservation(4_000, -99, "4G")))
        assertFalse(SignalHistory.canConnect(a, SignalObservation(4_000, null, "4G")))
        assertFalse(SignalHistory.canConnect(a, SignalObservation(4_000, -99, "5G")))
        assertFalse(SignalHistory.canConnect(a, SignalObservation(10_001, -99, "4G")))
        assertFalse(SignalHistory.canConnect(a, SignalObservation(1_000, -99, "4G")))
    }
}
