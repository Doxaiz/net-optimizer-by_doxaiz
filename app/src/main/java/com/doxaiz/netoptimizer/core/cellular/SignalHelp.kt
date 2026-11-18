package com.doxaiz.netoptimizer.core.cellular

/** Guidance only. A setting is not proof that Android or the modem supplies cell data. */
enum class SignalHelp { PERMISSION_NEEDED, AVAILABLE, LOCATION_OFF, WAITING }

object SignalHelpPolicy {
    fun choose(hasFineLocation: Boolean, locationEnabled: Boolean?, hasDisplayableSignal: Boolean): SignalHelp = when {
        !hasFineLocation -> SignalHelp.PERMISSION_NEEDED
        hasDisplayableSignal -> SignalHelp.AVAILABLE
        locationEnabled == false -> SignalHelp.LOCATION_OFF
        else -> SignalHelp.WAITING
    }
}
