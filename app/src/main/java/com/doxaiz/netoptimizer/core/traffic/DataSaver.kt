package com.doxaiz.netoptimizer.core.traffic

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Settings

/** Android's Data Saver is a system-wide, user-controlled setting, not an app switch. */
internal enum class DataSaverStatus { OFF, ON, ON_APP_EXEMPT, UNKNOWN }

internal object DataSaverPolicy {
    fun status(value: Int?): DataSaverStatus = when (value) {
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> DataSaverStatus.OFF
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> DataSaverStatus.ON_APP_EXEMPT
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> DataSaverStatus.ON
        else -> DataSaverStatus.UNKNOWN
    }

    /** Only a privileged system query can confirm the global setting: the app's own
     * status may be restricted independently of global Data Saver. */
    fun parseGlobal(output: String): Boolean? = when (output.trim()) {
        "Restrict background status: enabled" -> true
        "Restrict background status: disabled" -> false
        else -> null
    }

    /** Try settings screens in order; a vendor may omit or reject any individual action. */
    fun openFirst(actions: List<String>, open: (String) -> Boolean): Boolean {
        for (action in actions) if (open(action)) return true
        return false
    }
}

internal class DataSaver(private val context: Context) {
    fun status(): DataSaverStatus = try {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        DataSaverPolicy.status(manager?.restrictBackgroundStatus)
    } catch (_: Exception) { DataSaverStatus.UNKNOWN }

    /** The direct action is an AOSP settings action, not a public SDK constant; use fallbacks. */
    fun openSettings(): Boolean = DataSaverPolicy.openFirst(
        listOf("android.settings.DATA_SAVER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)
    ) { action ->
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) { false }
    }
}
