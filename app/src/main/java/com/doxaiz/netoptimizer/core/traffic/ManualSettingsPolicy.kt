package com.doxaiz.netoptimizer.core.traffic

/** The direct Android screen may be missing on vendor builds; report the actual fallback. */
internal enum class ManualSettingsDestination { DATA_USAGE, APP_DETAILS, UNAVAILABLE }

internal object ManualSettingsPolicy {
    fun openBackgroundSettings(open: (ManualSettingsDestination) -> Boolean): ManualSettingsDestination {
        if (open(ManualSettingsDestination.DATA_USAGE)) return ManualSettingsDestination.DATA_USAGE
        if (open(ManualSettingsDestination.APP_DETAILS)) return ManualSettingsDestination.APP_DETAILS
        return ManualSettingsDestination.UNAVAILABLE
    }
}
