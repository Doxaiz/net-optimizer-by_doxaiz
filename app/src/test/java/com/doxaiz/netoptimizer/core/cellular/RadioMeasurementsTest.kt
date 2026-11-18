package com.doxaiz.netoptimizer.core.cellular

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioMeasurementsTest {
    @Test
    fun lteAndNrMeasurementBoundariesAndUnavailable() {
        checkRange(-34, 3, RadioMeasurements::lteRsrq)
        checkRange(-20, 30, RadioMeasurements::lteSinr)
        checkRange(-43, 20, RadioMeasurements::nrRsrq)
        checkRange(-23, 40, RadioMeasurements::nrSinr)
    }

    @Test
    fun pciRangesDifferByTechnology() {
        checkRange(0, 503, RadioMeasurements::ltePci)
        checkRange(0, 1007, RadioMeasurements::nrPci)
        assertNull(RadioMeasurements.ltePci(504))
        assertEquals(504, RadioMeasurements.nrPci(504))
    }

    private fun checkRange(min: Int, max: Int, filter: (Int) -> Int?) {
        assertEquals(min, filter(min))
        assertEquals(max, filter(max))
        assertNull(filter(min - 1))
        assertNull(filter(max + 1))
        assertNull(filter(Int.MAX_VALUE))
        assertNull(filter(Int.MIN_VALUE))
    }
}
