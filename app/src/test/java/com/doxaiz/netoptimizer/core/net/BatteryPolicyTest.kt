package com.doxaiz.netoptimizer.core.net

import org.junit.Assert.*
import org.junit.Test

class BatteryPolicyTest {
    @Test fun thresholdIsTwentyPercentWithoutRoundingUp() {
        assertFalse(BatteryPolicy.canMeasure(19, 100))
        assertTrue(BatteryPolicy.canMeasure(20, 100))
        assertFalse(BatteryPolicy.canMeasure(199, 1000))
        assertTrue(BatteryPolicy.canMeasure(200, 1000))
    }
    @Test fun invalidAndExtremeReadingsFailSafelyWithoutOverflow() {
        assertFalse(BatteryPolicy.canMeasure(-1, 100))
        assertFalse(BatteryPolicy.canMeasure(20, 0))
        assertFalse(BatteryPolicy.canMeasure(101, 100))
        assertTrue(BatteryPolicy.canMeasure(Int.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(BatteryPolicy.canMeasure(1, Int.MAX_VALUE))
    }
}
