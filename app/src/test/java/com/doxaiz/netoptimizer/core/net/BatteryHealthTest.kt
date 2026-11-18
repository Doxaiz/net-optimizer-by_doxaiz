package com.doxaiz.netoptimizer.core.net

import org.junit.Assert.*
import org.junit.Test

class BatteryHealthTest {
    @Test fun parsesScaledPercentWithoutRoundingUpOrOverflow() {
        assertEquals(19, BatteryHealth.fromRaw(199, 1000, 420).percent)
        assertEquals(20, BatteryHealth.fromRaw(200, 1000, 420).percent)
        assertEquals(100, BatteryHealth.fromRaw(Int.MAX_VALUE, Int.MAX_VALUE, 420).percent)
        assertNull(BatteryHealth.fromRaw(-1, 100, 420).percent)
        assertNull(BatteryHealth.fromRaw(101, 100, 420).percent)
        assertNull(BatteryHealth.fromRaw(1, 0, 420).percent)
    }

    @Test fun missingTemperatureIsNotMistakenForZero() {
        assertNull(BatteryHealth.fromRaw(50, 100, Int.MIN_VALUE).temperatureTenths)
        assertNull(BatteryHealth.fromRaw(50, 100, 1001).temperatureTenths)
        assertEquals(0, BatteryHealth.fromRaw(50, 100, 0).temperatureTenths)
        assertEquals(-400, BatteryHealth.fromRaw(50, 100, -400).temperatureTenths)
    }

    @Test fun warningBoundariesAndMissingBatteryAreExplicit() {
        assertEquals(emptyList<BatteryWarning>(), BatteryHealth.warnings(BatterySnapshot(20, 420)))
        assertEquals(listOf(BatteryWarning.LOW_CHARGE, BatteryWarning.HIGH_TEMPERATURE), BatteryHealth.warnings(BatterySnapshot(19, 421)))
        assertEquals(listOf(BatteryWarning.UNKNOWN_CHARGE), BatteryHealth.warnings(BatterySnapshot(null, null)))
        assertEquals(listOf(BatteryWarning.UNKNOWN_CHARGE, BatteryWarning.HIGH_TEMPERATURE), BatteryHealth.warnings(BatterySnapshot(null, 430)))
    }

    @Test fun readingMustBelongToCurrentForegroundAndNotBeTooOld() {
        assertTrue(BatteryHealth.isFresh(1000, 1000, 16000))
        assertFalse(BatteryHealth.isFresh(1000, 1000, 16001))
        assertFalse(BatteryHealth.isFresh(999, 1000, 2000))
        assertFalse(BatteryHealth.isFresh(2001, 1000, 2000))
        assertFalse(BatteryHealth.isFresh(null, 1000, 2000))
        assertFalse(BatteryHealth.isFresh(1000, null, 2000))
    }
}
