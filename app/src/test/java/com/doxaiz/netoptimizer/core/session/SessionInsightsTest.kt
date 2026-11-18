package com.doxaiz.netoptimizer.core.session

import org.junit.Assert.*
import org.junit.Test

class SessionInsightsTest {
    @Test fun latencyCategoriesHandleBoundsAndMissingValues() {
        assertEquals(MetricLevel.UNKNOWN, SessionInsights.latencyLevel(-1))
        assertEquals(MetricLevel.GOOD, SessionInsights.latencyLevel(0))
        assertEquals(MetricLevel.GOOD, SessionInsights.latencyLevel(80))
        assertEquals(MetricLevel.CAUTION, SessionInsights.latencyLevel(81))
        assertEquals(MetricLevel.CAUTION, SessionInsights.latencyLevel(150))
        assertEquals(MetricLevel.POOR, SessionInsights.latencyLevel(151))
    }

    @Test fun failedConnectionsAreNotConfusedWithPacketLoss() {
        assertEquals(MetricLevel.UNKNOWN, SessionInsights.failureLevel(-1))
        assertEquals(MetricLevel.GOOD, SessionInsights.failureLevel(0))
        assertEquals(MetricLevel.CAUTION, SessionInsights.failureLevel(10))
        assertEquals(MetricLevel.POOR, SessionInsights.failureLevel(11))
        assertEquals(MetricLevel.POOR, SessionInsights.failureLevel(100))
        assertEquals(MetricLevel.UNKNOWN, SessionInsights.failureLevel(101))
    }

    @Test fun differenceIsDirectionalAndMissingDataNeverBecomesZero() {
        assertEquals(-20L, SessionInsights.latencyDifference(100, 80))
        assertEquals(20L, SessionInsights.latencyDifference(80, 100))
        assertEquals(0L, SessionInsights.latencyDifference(100, 100))
        assertNull(SessionInsights.latencyDifference(-1, 100))
        assertNull(SessionInsights.latencyDifference(100, -1))
        assertEquals(2147483647L, SessionInsights.latencyDifference(0, Int.MAX_VALUE))
    }

    @Test fun graphUsesOnlyValidValuesAndIsBounded() {
        assertEquals(listOf(0, 42, Int.MAX_VALUE), SessionInsights.historyValues("0, -1, abc, 42, 2147483647, 2147483648"))
        assertTrue(SessionInsights.historyValues("").isEmpty())
        val history = SessionInsights.historyValues((0..120).joinToString(","))
        assertEquals(120, history.size)
        assertEquals(1, history.first())
        assertEquals(120, history.last())
    }

    @Test fun durationIncludesSecondsAndHours() {
        assertEquals("0 мин 05 с", SessionInsights.formatDuration(5))
        assertEquals("1 мин 00 с", SessionInsights.formatDuration(60))
        assertEquals("1 ч 01 мин 01 с", SessionInsights.formatDuration(3661))
        assertEquals("—", SessionInsights.formatDuration(-1))
    }
}
