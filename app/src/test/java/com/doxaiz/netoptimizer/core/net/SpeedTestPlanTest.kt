package com.doxaiz.netoptimizer.core.net

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class SpeedTestPlanTest {
    @Test fun speedUsesBytesAndNanosecondsAndRejectsInvalidInputs() {
        assertEquals(80.0, SpeedTestPlan.mbps(10_000_000, 1_000_000_000)!!, 0.00001)
        assertEquals(20.0, SpeedTestPlan.mbps(5_000_000, 2_000_000_000)!!, 0.00001)
        assertNull(SpeedTestPlan.mbps(0, 1))
        assertNull(SpeedTestPlan.mbps(1, 0))
    }

    @Test fun networkIsRequiredBeforeAnyTransfer() {
        var calls = 0
        val result = SpeedTestPlan.run(false, { true }, { true }, { false }) { _, _ -> calls++; 1.0 }
        assertEquals(SpeedStatus.NO_CELLULAR, result.status)
        assertEquals(0, calls)
        assertNull(result.downloadMbps)
    }

    @Test fun partialFailureIsNotReportedAsZeroOrHiddenByOtherDirection() {
        val result = SpeedTestPlan.run(true, { true }, { true }, { false }) { direction, _ ->
            if (direction == SpeedDirection.DOWNLOAD) throw java.io.IOException() else 12.0
        }
        assertEquals(SpeedStatus.COMPLETED, result.status)
        assertNull(result.downloadMbps)
        assertEquals(12.0, result.uploadMbps!!, 0.001)
    }

    @Test fun networkChangeDiscardsAllResults() {
        var same = true
        val result = SpeedTestPlan.run(true, { same }, { true }, { false }) { direction, check ->
            if (direction == SpeedDirection.UPLOAD) { same = false; check() }
            50.0
        }
        assertEquals(SpeedStatus.NETWORK_CHANGED, result.status)
        assertNull(result.downloadMbps)
        assertNull(result.uploadMbps)
    }

    @Test fun lowBatteryStopsEvenBetweenTransfers() {
        var ok = true
        val result = SpeedTestPlan.run(true, { true }, { ok }, { false }) { _, _ ->
            ok = false
            30.0
        }
        assertEquals(SpeedStatus.LOW_BATTERY, result.status)
        assertNull(result.downloadMbps)
    }

    @Test fun cancellationIsNotSwallowed() {
        try {
            SpeedTestPlan.run(true, { true }, { true }, { true }) { _, _ -> 10.0 }
            fail("cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun rejectsNonFiniteAndNonPositiveResults() {
        val result = SpeedTestPlan.run(true, { true }, { true }, { false }) { direction, _ ->
            if (direction == SpeedDirection.DOWNLOAD) Double.NaN else 0.0
        }
        assertNull(result.downloadMbps)
        assertNull(result.uploadMbps)
    }
}
