package com.doxaiz.netoptimizer.core.cellular

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalFreshnessTest {
    @Test fun freshAtBothAgeBoundaries() {
        assertEquals(SignalFreshnessStatus.FRESH, SignalFreshness.status(45_000, 30_000, 40_000, 60_000))
        assertEquals(SignalFreshnessStatus.STALE_POLL, SignalFreshness.status(44_999, 30_000, 40_000, 60_000))
        assertEquals(SignalFreshnessStatus.STALE_MODEM, SignalFreshness.status(59_000, 29_999, 40_000, 60_000))
    }

    @Test fun noForegroundOrOldPollDoesNotLeakPreviousSession() {
        assertEquals(SignalFreshnessStatus.STALE_POLL, SignalFreshness.status(59_000, 59_000, null, 60_000))
        assertEquals(SignalFreshnessStatus.STALE_POLL, SignalFreshness.status(59_000, 59_000, 59_001, 60_000))
        assertEquals(SignalFreshnessStatus.STALE_POLL, SignalFreshness.status(null, null, 0, 60_000))
        assertEquals(SignalFreshnessStatus.STALE_POLL, SignalFreshness.status(60_001, 59_000, 0, 60_000))
        assertEquals(SignalFreshnessStatus.STALE_POLL, SignalFreshness.status(59_000, 59_000, -1, 60_000))
    }

    @Test fun unavailableInvalidAndFutureModemTimestampAreUnknownNotFresh() {
        for (timestamp in listOf(null, 0L, -1L, 59_001L, Long.MAX_VALUE)) {
            assertEquals(SignalFreshnessStatus.UNKNOWN_MODEM_TIME, SignalFreshness.status(59_000, timestamp, 0, 60_000))
        }
    }

    @Test fun staleValuesAreHiddenAndUnknownTimeIsExplicitlyAllowed() {
        assertTrue(SignalFreshness.canDisplay(SignalFreshnessStatus.FRESH))
        assertTrue(SignalFreshness.canDisplay(SignalFreshnessStatus.UNKNOWN_MODEM_TIME))
        assertFalse(SignalFreshness.canDisplay(SignalFreshnessStatus.STALE_MODEM))
        assertFalse(SignalFreshness.canDisplay(SignalFreshnessStatus.STALE_POLL))
    }
}
