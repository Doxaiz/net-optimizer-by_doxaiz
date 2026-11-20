package com.doxaiz.netoptimizer.core.net

import com.doxaiz.netoptimizer.core.session.SessionNetworkGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PingMeasurementPlanTest {
    private val target = PingTarget("Test", "example.com", 443, "")

    @Test fun reportsOnlyCompletedTcpAttempts() = runBlocking {
        val progress = mutableListOf<Int>()
        val answers = listOf(20, -1, 40).iterator()
        val result = PingMeasurementPlan.run(target, 3, { true }, { true }, { "address" },
            { answers.next() }, { done, _ -> progress += done })
        assertEquals(PingStatus.COMPLETED, result.status)
        assertEquals(30, result.avgMs)
        assertEquals(3, result.attemptsMade)
        assertEquals(2, result.successfulAttempts)
        assertEquals(listOf(1, 2, 3), progress)
    }

    @Test fun rejectsNetworkThatChangesAndReturnsDuringDns() = runBlocking {
        val guard = SessionNetworkGuard("mobile")
        var connects = 0
        val result = PingMeasurementPlan.run(target, 3, { guard.valid("mobile") }, { true }, {
            guard.available("wifi")
            guard.available("mobile")
            "address"
        }, { connects++; 20 })
        assertEquals(PingStatus.NETWORK_CHANGED, result.status)
        assertEquals(0, connects)
        assertEquals(-1, result.avgMs)
        assertEquals(-1, result.lossPercent)
    }

    @Test fun rejectsChangeDuringFinalConnectionEvenIfOriginalReturns() = runBlocking {
        val guard = SessionNetworkGuard("mobile")
        var connects = 0
        val result = PingMeasurementPlan.run(target, 2, { guard.valid("mobile") }, { true }, { "address" }, {
            connects++
            if (connects == 2) { guard.lost("mobile"); guard.available("mobile") }
            15
        })
        assertEquals(2, result.attemptsMade)
        assertEquals(PingStatus.NETWORK_CHANGED, result.status)
        assertEquals(-1, result.avgMs)
        assertEquals(-1, result.jitterMs)
        assertEquals(-1, result.lossPercent)
        assertFalse(result.ok)
    }

    @Test fun batteryFailureAfterResolutionIsNotReportedAsDnsFailure() = runBlocking {
        var battery = true
        val result = PingMeasurementPlan.run<String>(target, 3, { true }, { battery }, {
            battery = false
            null
        }, { 10 })
        assertEquals(PingStatus.LOW_BATTERY, result.status)
        assertEquals(0, result.attemptsMade)
    }

    @Test fun neverStartsResolutionWhenSafetyFailsAndPreservesCancellation() = runBlocking {
        var resolved = false
        val stopped = PingMeasurementPlan.run(target, 3, { false }, { true }, { resolved = true; "address" }, { 10 })
        assertFalse(resolved)
        assertEquals(PingStatus.NETWORK_CHANGED, stopped.status)
        try {
            PingMeasurementPlan.run(target, 3, { true }, { true }, { "address" }, { 10 }) { _, _ ->
                throw CancellationException("cancelled")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
}
