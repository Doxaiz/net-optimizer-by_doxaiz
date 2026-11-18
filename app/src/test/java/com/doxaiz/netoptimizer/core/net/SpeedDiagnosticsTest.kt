package com.doxaiz.netoptimizer.core.net

import org.junit.Assert.*
import org.junit.Test

class SpeedDiagnosticsTest {
    private fun result(down: Double? = 10.0, up: Double? = 5.0, latency: LoadedLatency? = null) =
        SpeedResult(down, up, SpeedStatus.COMPLETED, latency)

    @Test fun interruptedTestsNeverProduceDiagnosticHintsOrComparison() {
        for (status in listOf(SpeedStatus.LOW_BATTERY, SpeedStatus.NETWORK_CHANGED, SpeedStatus.NO_CELLULAR)) {
            val stopped = SpeedResult(1.0, 0.5, status)
            assertTrue(SpeedDiagnostics.hints(stopped).isEmpty())
            assertNull(SpeedDiagnostics.difference(stopped, result()))
            assertNull(SpeedDiagnostics.difference(result(), stopped))
        }
    }

    @Test fun incompleteAndInvalidMeasurementsAreNeverZeroOrHealthy() {
        assertEquals(listOf(SpeedHint.INCOMPLETE), SpeedDiagnostics.hints(result(null, 5.0)))
        assertEquals(listOf(SpeedHint.INCOMPLETE), SpeedDiagnostics.hints(result(Double.NaN, 0.0)))
        assertEquals(listOf(SpeedHint.INCOMPLETE), SpeedDiagnostics.hints(result(Double.POSITIVE_INFINITY, -1.0)))
        assertEquals(listOf(SpeedHint.INCOMPLETE, SpeedHint.SLOW_UPLOAD), SpeedDiagnostics.hints(result(null, 0.5)))
    }

    @Test fun speedThresholdsAreStrictAndSeparate() {
        assertEquals(listOf(SpeedHint.SLOW_DOWNLOAD, SpeedHint.SLOW_UPLOAD), SpeedDiagnostics.hints(result(1.99, 0.99)))
        assertEquals(listOf(SpeedHint.NO_STRONG_INDICATION), SpeedDiagnostics.hints(result(2.0, 1.0)))
    }

    @Test fun latencyRequiresEnoughSuccessesAndUsesLongDifference() {
        assertEquals(listOf(SpeedHint.NO_STRONG_INDICATION), SpeedDiagnostics.hints(result(latency = LoadedLatency(151, 300, 2, 3))))
        assertEquals(listOf(SpeedHint.HIGH_IDLE_TCP), SpeedDiagnostics.hints(result(latency = LoadedLatency(151, 400, 3, 2))))
        assertEquals(listOf(SpeedHint.HIGH_IDLE_TCP, SpeedHint.LOADED_TCP_INCREASE), SpeedDiagnostics.hints(result(latency = LoadedLatency(151, 252, 3, 3))))
        assertEquals(listOf(SpeedHint.NO_STRONG_INDICATION), SpeedDiagnostics.hints(result(latency = LoadedLatency(150, 250, 3, 3))))
        assertEquals(listOf(SpeedHint.NO_STRONG_INDICATION), SpeedDiagnostics.hints(result(latency = LoadedLatency(-1, Int.MAX_VALUE, 3, 3))))
    }

    @Test fun comparesOnlyPresentDirectionsAndKeepsDirection() {
        assertEquals(SpeedDifference(2.0, -1.0), SpeedDiagnostics.difference(result(5.0, 3.0), result(7.0, 2.0)))
        assertEquals(SpeedDifference(null, 1.0), SpeedDiagnostics.difference(result(null, 3.0), result(7.0, 4.0)))
        assertNull(SpeedDiagnostics.difference(result(null, 2.0), result(7.0, null)))
        assertNull(SpeedDiagnostics.difference(result(0.0, Double.NaN), result(7.0, 4.0)))
    }
}
