package com.doxaiz.netoptimizer.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionScoreTest {
    @Test fun missingOrInvalidInputsHaveNoScore() {
        assertNull(ConnectionScore.calculate(null, -85))
        assertNull(ConnectionScore.calculate(-1, -85))
        assertNull(ConnectionScore.calculate(40, 0))
        assertNull(ConnectionScore.calculate(40, Int.MAX_VALUE))
    }

    @Test fun higherDelayAndWeakerSignalLowerScore() {
        assertEquals(90, ConnectionScore.calculate(30, -80))
        assertEquals(19, ConnectionScore.calculate(121, -111))
        assertEquals(74, ConnectionScore.calculate(50, -90))
    }
}
