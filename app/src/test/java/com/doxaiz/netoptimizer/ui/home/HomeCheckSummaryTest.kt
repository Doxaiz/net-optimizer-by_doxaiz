package com.doxaiz.netoptimizer.ui.home

import com.doxaiz.netoptimizer.core.net.PingResult
import com.doxaiz.netoptimizer.core.net.PingStatus
import com.doxaiz.netoptimizer.core.net.PingTarget
import org.junit.Assert.*
import org.junit.Test

class HomeCheckSummaryTest {
    private val target = PingTarget("Яндекс", "yandex.ru", 443, "")
    private fun result(status: PingStatus, avg: Int = -1, attempts: Int = 0, successes: Int = 0) =
        PingResult(target, avg, -1, -1, false, attempts, successes, status)

    @Test fun reportsMeasuredLatencyAndPartialSuccessHonestly() {
        val summary = homeCheckSummary(result(PingStatus.COMPLETED, 73, 6, 3))
        assertTrue(summary.hasMeasurement)
        assertEquals("73 мс", summary.headline)
        assertTrue(summary.explanation.contains("3/6"))
        assertTrue(summary.explanation.contains("Яндекса"))
    }

    @Test fun incompleteAndFailedResultsAreNeverPresentedAsMeasured() {
        for (status in PingStatus.entries.filter { it != PingStatus.COMPLETED }) {
            assertFalse(homeCheckSummary(result(status, 42, 6, 6)).hasMeasurement)
        }
        assertFalse(homeCheckSummary(result(PingStatus.COMPLETED, -1, 6, 0)).hasMeasurement)
        assertFalse(homeCheckSummary(result(PingStatus.COMPLETED, 5, 0, 0)).hasMeasurement)
    }

    @Test fun statusExplainsLikelyCauseRatherThanInventingAnImprovement() {
        assertEquals("Сеть изменилась", homeCheckSummary(result(PingStatus.NETWORK_CHANGED)).headline)
        assertEquals("Проверка приостановлена", homeCheckSummary(result(PingStatus.LOW_BATTERY)).headline)
    }
}
