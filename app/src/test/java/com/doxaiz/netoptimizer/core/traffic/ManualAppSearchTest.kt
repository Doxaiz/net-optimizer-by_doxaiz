package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualAppSearchTest {
    private val app = StopTarget(10123, "com.example.download", "Загрузки")

    @Test fun matchesNameAndPackageWithoutCaseOrSurroundingWhitespace() {
        assertTrue(ManualAppSearch.matches(app, "  загрУЗ  "))
        assertTrue(ManualAppSearch.matches(app, "EXAMPLE.DOWNLOAD"))
        assertTrue(ManualAppSearch.matches(app, "  "))
        assertFalse(ManualAppSearch.matches(app, "music"))
    }
}
