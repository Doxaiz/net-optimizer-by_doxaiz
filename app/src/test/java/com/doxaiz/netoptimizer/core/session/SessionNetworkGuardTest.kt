package com.doxaiz.netoptimizer.core.session

import org.junit.Assert.*
import org.junit.Test

class SessionNetworkGuardTest {
    @Test fun disconnectInvalidatesEvenIfNetworkReturns() {
        val guard = SessionNetworkGuard("mobile-1")
        assertTrue(guard.valid("mobile-1"))
        guard.lost("mobile-1")
        assertFalse(guard.valid("mobile-1"))
    }
    @Test fun switchingNetworksOrLosingCapabilitiesIsPermanent() {
        val guard = SessionNetworkGuard("mobile-1")
        guard.available("mobile-2")
        assertFalse(guard.valid("mobile-1"))
        val second = SessionNetworkGuard("mobile-1")
        second.capabilities("mobile-1", false)
        assertFalse(second.valid("mobile-1"))
    }
    @Test fun unrelatedLostNetworkDoesNotStopSession() {
        val guard = SessionNetworkGuard("mobile-1")
        guard.lost("other")
        guard.capabilities("other", false)
        assertTrue(guard.valid("mobile-1"))
        assertFalse(guard.valid(null))
    }
}
