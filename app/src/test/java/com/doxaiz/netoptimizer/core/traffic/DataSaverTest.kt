package com.doxaiz.netoptimizer.core.traffic

import android.net.ConnectivityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSaverTest {
    @Test fun statusDistinguishesAnExemptionFromDisabled() {
        assertEquals(DataSaverStatus.OFF, DataSaverPolicy.status(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED))
        assertEquals(DataSaverStatus.ON_APP_EXEMPT, DataSaverPolicy.status(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED))
        assertEquals(DataSaverStatus.ON, DataSaverPolicy.status(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED))
        assertEquals(DataSaverStatus.UNKNOWN, DataSaverPolicy.status(null))
        assertEquals(DataSaverStatus.UNKNOWN, DataSaverPolicy.status(-1))
    }

    @Test fun triesFallbacksAndStopsAfterSuccess() {
        val attempted = mutableListOf<String>()
        val opened = DataSaverPolicy.openFirst(listOf("direct", "network", "general")) {
            attempted.add(it)
            it == "network"
        }
        assertTrue(opened)
        assertEquals(listOf("direct", "network"), attempted)
    }

    @Test fun reportsFailureWhenNoSettingsScreenOpens() {
        val attempted = mutableListOf<String>()
        assertFalse(DataSaverPolicy.openFirst(listOf("direct", "network", "general")) { attempted.add(it); false })
        assertEquals(listOf("direct", "network", "general"), attempted)
        assertFalse(DataSaverPolicy.openFirst(emptyList()) { true })
    }
}
