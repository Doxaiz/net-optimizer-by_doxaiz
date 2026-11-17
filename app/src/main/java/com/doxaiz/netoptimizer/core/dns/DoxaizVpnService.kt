package com.doxaiz.netoptimizer.core.dns

import android.content.Context
import android.content.Intent
import android.net.VpnService

/** Disabled until DNS interception and per-app blocking can be implemented safely. */
class DoxaizVpnService : VpnService() {
    companion object {
        var currentDns: String = "77.88.8.8"
        var blockedApps: Set<String> = emptySet()
        var running: Boolean = false

        fun start(context: Context, dns: String, blocked: Set<String>) {
            // Never establish the old full-tunnel interface: it dropped non-DNS traffic.
            stop(context)
        }

        fun stop(context: Context) {
            running = false
            blockedApps = emptySet()
            context.stopService(Intent(context, DoxaizVpnService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = false
        blockedApps = emptySet()
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
