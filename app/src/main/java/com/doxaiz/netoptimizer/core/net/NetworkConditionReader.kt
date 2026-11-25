package com.doxaiz.netoptimizer.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Reads only public, documented capability flags for the active cellular network. */
internal object NetworkConditionReader {
    fun read(context: Context): NetworkConditions? = try {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = manager.activeNetwork ?: return null
        val caps = manager.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return null
        NetworkConditions(
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            captivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
            notCongested = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED),
            notSuspended = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED),
            notRoaming = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING),
            temporarilyUnmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            downstreamKbps = caps.linkDownstreamBandwidthKbps,
            upstreamKbps = caps.linkUpstreamBandwidthKbps
        )
    } catch (_: Exception) { null }
}
