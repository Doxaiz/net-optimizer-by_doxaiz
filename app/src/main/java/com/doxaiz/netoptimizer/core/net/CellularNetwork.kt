package com.doxaiz.netoptimizer.core.net

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager

/** Only the currently active cellular network is used; never silently fall back to Wi-Fi. */
object CellularNetwork {
    fun active(context: Context): Network? {
        return try {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val network = manager.activeNetwork ?: return null
            val caps = manager.getNetworkCapabilities(network) ?: return null
            network.takeIf {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        } catch (_: Exception) { null }
    }

    fun batteryOk(context: Context): Boolean {
        return try {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
            val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            BatteryPolicy.canMeasure(level, scale)
        } catch (_: Exception) { false }
    }
}
