package com.doxaiz.netoptimizer.core.util

import org.junit.Assert.*
import org.junit.Test

class InterfaceModeTest {
    @Test fun savedModeMustBeRecognised() {
        assertEquals(InterfaceMode.SIMPLE, InterfaceMode.fromStored("SIMPLE"))
        assertEquals(InterfaceMode.EXPERT, InterfaceMode.fromStored("EXPERT"))
        assertNull(InterfaceMode.fromStored(null))
        assertNull(InterfaceMode.fromStored("unknown"))
        assertNull(InterfaceMode.fromStored("simple"))
    }
}
