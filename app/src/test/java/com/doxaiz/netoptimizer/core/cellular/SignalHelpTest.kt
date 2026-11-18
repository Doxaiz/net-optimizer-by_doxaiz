package com.doxaiz.netoptimizer.core.cellular

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalHelpTest {
    @Test fun missingPermissionIsNotReportedAsReady() {
        assertEquals(SignalHelp.PERMISSION_NEEDED, SignalHelpPolicy.choose(false, true, true))
        assertEquals(SignalHelp.PERMISSION_NEEDED, SignalHelpPolicy.choose(false, null, false))
    }

    @Test fun validSignalCanBeShownEvenIfLocationSwitchIsOff() {
        assertEquals(SignalHelp.AVAILABLE, SignalHelpPolicy.choose(true, false, true))
    }

    @Test fun suggestLocationSwitchOnlyWhenKnownToBeOffAndSignalUnavailable() {
        assertEquals(SignalHelp.LOCATION_OFF, SignalHelpPolicy.choose(true, false, false))
        assertEquals(SignalHelp.WAITING, SignalHelpPolicy.choose(true, null, false))
        assertEquals(SignalHelp.WAITING, SignalHelpPolicy.choose(true, true, false))
    }
}
