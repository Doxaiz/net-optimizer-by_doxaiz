package com.doxaiz.netoptimizer.core.cellular

import org.junit.Assert.*
import org.junit.Test

class CellRefreshGateTest {
    @Test fun limitsRequestsAndConsumesOnlyOnce() {
        val gate = CellRefreshGate<String>()
        assertNull(gate.tryStart(0))
        gate.start()
        val first = gate.tryStart(0)!!
        assertNull(gate.tryStart(15_000)) // pending request, even after the interval
        gate.complete(first, "cells")
        assertEquals("cells", gate.take())
        assertNull(gate.take())
        assertNull(gate.tryStart(14_999))
        assertNotNull(gate.tryStart(15_000))
    }

    @Test fun stoppedOrOldCallbackCannotPopulateNewForeground() {
        val gate = CellRefreshGate<String>()
        gate.start()
        val first = gate.tryStart(100)!!
        gate.stop()
        gate.start()
        assertNull(gate.tryStart(20_000)) // old Android request is still outstanding
        gate.complete(first, "old")
        assertNull(gate.take())
        val second = gate.tryStart(20_000)!!
        gate.complete(first, "late")
        gate.complete(second, "new")
        assertEquals("new", gate.take())
    }

    @Test fun timedOutRequestCanBeRetriedAndLateResponseIgnored() {
        val gate = CellRefreshGate<String>()
        gate.start()
        val first = gate.tryStart(10_000)!!
        assertNull(gate.tryStart(54_999))
        val second = gate.tryStart(55_000)!!
        gate.complete(first, "late")
        assertNull(gate.take())
        gate.complete(second, "current")
        assertEquals("current", gate.take())
    }

    @Test fun errorsAndUnexpectedTimesDoNotCreateData() {
        val gate = CellRefreshGate<String>()
        gate.start()
        assertNull(gate.tryStart(-1))
        val id = gate.tryStart(1_000)!!
        gate.complete(id, null)
        assertNull(gate.take())
        assertNull(gate.tryStart(999))
        assertNotNull(gate.tryStart(16_000))
        gate.stop()
        assertNull(gate.tryStart(100_000))
    }
}
