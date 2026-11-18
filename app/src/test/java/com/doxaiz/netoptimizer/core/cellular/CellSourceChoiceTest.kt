package com.doxaiz.netoptimizer.core.cellular

import org.junit.Assert.*
import org.junit.Test

class CellSourceChoiceTest {
    @Test fun choosesNewerRegisteredDataAndHandlesEmptyLists() {
        assertTrue(CellSourceChoice.useRefresh(true, 1_000, true, 2_000, 3_000))
        assertFalse(CellSourceChoice.useRefresh(true, 2_000, true, 1_000, 3_000))
        assertTrue(CellSourceChoice.useRefresh(true, 2_000, true, 2_000, 3_000))
        assertFalse(CellSourceChoice.useRefresh(true, 1_000, false, null, 3_000))
        assertTrue(CellSourceChoice.useRefresh(false, null, true, 1_000, 3_000))
    }

    @Test fun unknownOrFutureModemTimestampsDoNotBeatKnownData() {
        assertFalse(CellSourceChoice.useRefresh(true, 1_000, true, null, 3_000))
        assertFalse(CellSourceChoice.useRefresh(true, 1_000, true, 0, 3_000))
        assertFalse(CellSourceChoice.useRefresh(true, 1_000, true, 3_001, 3_000))
        assertTrue(CellSourceChoice.useRefresh(true, null, true, 1_000, 3_000))
        assertTrue(CellSourceChoice.useRefresh(true, null, true, null, 3_000))
    }
}
