package com.doxaiz.netoptimizer.core.traffic

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualSettingsPolicyTest {
    @Test fun opensDirectScreenOnlyWhenAvailable() {
        val attempts = mutableListOf<ManualSettingsDestination>()
        val result = ManualSettingsPolicy.openBackgroundSettings { attempts += it; true }
        assertEquals(ManualSettingsDestination.DATA_USAGE, result)
        assertEquals(listOf(ManualSettingsDestination.DATA_USAGE), attempts)
    }

    @Test fun fallsBackToAppDetailsAndReportsFailure() {
        val attempts = mutableListOf<ManualSettingsDestination>()
        val fallback = ManualSettingsPolicy.openBackgroundSettings { attempts += it; it == ManualSettingsDestination.APP_DETAILS }
        assertEquals(ManualSettingsDestination.APP_DETAILS, fallback)
        assertEquals(listOf(ManualSettingsDestination.DATA_USAGE, ManualSettingsDestination.APP_DETAILS), attempts)
        assertEquals(ManualSettingsDestination.UNAVAILABLE, ManualSettingsPolicy.openBackgroundSettings { false })
    }
}
